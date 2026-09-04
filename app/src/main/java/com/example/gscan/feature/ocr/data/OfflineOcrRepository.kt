package com.example.gscan.feature.ocr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.database.model.OcrResultEntity
import com.example.gscan.core.database.model.PageEntity
import com.example.gscan.core.image.readImageOrientation
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.feature.ocr.domain.model.OcrException
import com.example.gscan.feature.ocr.domain.model.OcrFailure
import com.example.gscan.feature.ocr.domain.model.OcrJobState
import com.example.gscan.feature.ocr.domain.model.OcrJobStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageText
import com.example.gscan.feature.ocr.domain.model.OcrRunSummary
import com.example.gscan.feature.ocr.domain.repository.OcrRepository
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

@Singleton
class OfflineOcrRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val operationLock: DocumentOperationLock,
) : OcrRepository {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun observeResults(documentId: String): Flow<List<OcrPageText>> =
        documentDao.observeOcrResults(documentId).map { rows ->
            rows.map { row ->
                OcrPageText(
                    pageId = row.result.pageId,
                    position = row.position,
                    text = row.result.text,
                    status = runCatching { OcrPageStatus.valueOf(row.result.status) }
                        .getOrDefault(OcrPageStatus.FAILED),
                    errorCode = row.result.errorCode,
                    updatedAtEpochMillis = row.result.updatedAtEpochMillis,
                )
            }
        }

    override fun observeJob(documentId: String): Flow<OcrJobState> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(documentId)).map { workInfos ->
            val info = workInfos.firstOrNull { !it.state.isFinished }
                ?: workInfos.lastOrNull()
                ?: return@map OcrJobState()
            OcrJobState(
                status = info.state.toDomainStatus(),
                completedPages = info.progress.getInt(DocumentOcrWorker.KEY_COMPLETED_PAGES, 0),
                totalPages = info.progress.getInt(DocumentOcrWorker.KEY_TOTAL_PAGES, 0),
            )
        }

    override fun enqueue(documentId: String) {
        val request = OneTimeWorkRequestBuilder<DocumentOcrWorker>()
            .setInputData(DocumentOcrWorker.inputData(documentId))
            .addTag(workName(documentId))
            .build()
        workManager.enqueueUniqueWork(
            workName(documentId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(documentId: String) {
        workManager.cancelUniqueWork(workName(documentId))
    }

    override suspend fun recognizeDocument(
        documentId: String,
        onProgress: suspend (completedPages: Int, totalPages: Int) -> Unit,
    ): OcrRunSummary = operationLock.mutex.withLock {
        val details = documentDao.getWithPages(documentId)
            ?: throw OcrException(OcrFailure.DOCUMENT_NOT_FOUND)
        val pages = details.pages.sortedBy { it.position }
        if (pages.isEmpty()) throw OcrException(OcrFailure.NO_PAGES)
        val previousTextByPageId = documentDao.getOcrResults(documentId)
            .associate { it.result.pageId to it.result.text }

        val now = System.currentTimeMillis()
        documentDao.prepareOcr(
            documentId = documentId,
            results = pages.map { page ->
                page.toOcrEntity(
                    status = OcrPageStatus.PENDING,
                    text = previousTextByPageId[page.id].orEmpty(),
                    errorCode = null,
                    updatedAt = now,
                )
            },
        )
        onProgress(0, pages.size)

        var failedPages = 0
        pages.forEachIndexed { index, page ->
            currentCoroutineContext().ensureActive()
            val previousText = previousTextByPageId[page.id].orEmpty()
            documentDao.saveOcrResult(
                page.toOcrEntity(
                    status = OcrPageStatus.PROCESSING,
                    text = previousText,
                    errorCode = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            try {
                val text = recognizePage(page)
                currentCoroutineContext().ensureActive()
                documentDao.saveOcrResult(
                    page.toOcrEntity(
                        status = OcrPageStatus.SUCCEEDED,
                        text = text,
                        errorCode = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failedPages++
                val failure = (error as? OcrException)?.reason ?: OcrFailure.RECOGNITION_FAILED
                documentDao.saveOcrResult(
                    page.toOcrEntity(
                        status = OcrPageStatus.FAILED,
                        text = previousText,
                        errorCode = failure.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            onProgress(index + 1, pages.size)
        }
        OcrRunSummary(totalPages = pages.size, failedPages = failedPages)
    }

    private suspend fun recognizePage(page: PageEntity): String {
        val bitmap = decodePage(page)
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw OcrException(OcrFailure.RECOGNITION_FAILED, error)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodePage(page: PageEntity): Bitmap {
        val file = page.sourceUri.toUri().path?.let(::File)
            ?: throw OcrException(OcrFailure.SOURCE_UNAVAILABLE)
        if (!file.isFile) throw OcrException(OcrFailure.SOURCE_UNAVAILABLE)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw OcrException(OcrFailure.SOURCE_UNAVAILABLE)
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > OCR_MAX_DIMENSION_PX ||
            bounds.outHeight / sampleSize > OCR_MAX_DIMENSION_PX
        ) {
            sampleSize *= 2
        }
        val decoded = try {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } catch (error: OutOfMemoryError) {
            throw OcrException(OcrFailure.RECOGNITION_FAILED, error)
        } ?: throw OcrException(OcrFailure.SOURCE_UNAVAILABLE)

        val orientation = file.readImageOrientation()
        val userRotation = page.rotationDegrees.normalizedRotation()
        if (orientation.rotationDegrees == 0 &&
            !orientation.isFlippedHorizontally &&
            userRotation == 0
        ) {
            return decoded
        }
        val matrix = Matrix().apply {
            if (orientation.isFlippedHorizontally) postScale(-1f, 1f)
            if (orientation.rotationDegrees != 0) postRotate(orientation.rotationDegrees.toFloat())
            if (userRotation != 0) postRotate(userRotation.toFloat())
        }
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { transformed -> if (transformed !== decoded) decoded.recycle() }
        } catch (error: Exception) {
            decoded.recycle()
            throw OcrException(OcrFailure.RECOGNITION_FAILED, error)
        } catch (error: OutOfMemoryError) {
            decoded.recycle()
            throw OcrException(OcrFailure.RECOGNITION_FAILED, error)
        }
    }

    private fun PageEntity.toOcrEntity(
        status: OcrPageStatus,
        text: String,
        errorCode: String?,
        updatedAt: Long,
    ) = OcrResultEntity(
        pageId = id,
        documentId = documentId,
        text = text,
        script = OCR_SCRIPT,
        engineVersion = OCR_ENGINE_VERSION,
        status = status.name,
        errorCode = errorCode,
        updatedAtEpochMillis = updatedAt,
    )

    private fun WorkInfo.State.toDomainStatus(): OcrJobStatus = when (this) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> OcrJobStatus.QUEUED
        WorkInfo.State.RUNNING -> OcrJobStatus.RUNNING
        WorkInfo.State.SUCCEEDED -> OcrJobStatus.SUCCEEDED
        WorkInfo.State.FAILED -> OcrJobStatus.FAILED
        WorkInfo.State.CANCELLED -> OcrJobStatus.CANCELLED
    }

    private fun workName(documentId: String) = "document-ocr-$documentId"

    private fun Int.normalizedRotation(): Int = ((this % 360) + 360) % 360

    // ML Kit Task không hỗ trợ cancel. Chờ Task kết thúc trước khi coroutine tiếp tục
    // để caller không recycle Bitmap trong lúc native recognizer vẫn đang đọc ảnh.
    private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.resumeWithException(CancellationException("ML Kit task was cancelled"))
        }
    }

    private companion object {
        const val OCR_MAX_DIMENSION_PX = 2_400
        const val OCR_SCRIPT = "LATIN"
        const val OCR_ENGINE_VERSION = "ML_KIT_TEXT_RECOGNITION_16_0_1"
    }
}
