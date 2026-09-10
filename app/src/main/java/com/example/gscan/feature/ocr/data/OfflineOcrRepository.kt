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
import com.example.gscan.feature.ocr.domain.model.ExportedOcrText
import com.example.gscan.feature.ocr.domain.model.OcrTextExportException
import com.example.gscan.feature.ocr.domain.model.OcrTextExportFailure
import com.example.gscan.feature.ocr.domain.model.OcrTextExportMode
import com.example.gscan.feature.ocr.domain.repository.OcrRepository
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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

    override suspend fun createTextExport(
        documentId: String,
        mode: OcrTextExportMode,
    ): ExportedOcrText = withContext(Dispatchers.IO) {
        operationLock.mutex.withLock {
            val details = documentDao.getWithPages(documentId)
                ?: throw OcrTextExportException(OcrTextExportFailure.DOCUMENT_NOT_FOUND)
            val pages = details.pages.sortedBy { it.position }
            val resultsByPageId = documentDao.getOcrResults(documentId)
                .associateBy { it.result.pageId }
            val hasAnyText = resultsByPageId.values.any { it.result.text.isNotBlank() }
            val exportablePages = when (mode) {
                OcrTextExportMode.SUCCESSFUL_ONLY -> pages.filter { page ->
                    resultsByPageId[page.id]?.result?.let { result ->
                        result.status == OcrPageStatus.SUCCEEDED.name && result.text.isNotBlank()
                    } == true
                }
                OcrTextExportMode.KEEP_PAGE_PLACEHOLDERS -> pages
            }
            if (!hasAnyText || exportablePages.isEmpty()) {
                throw OcrTextExportException(OcrTextExportFailure.NO_TEXT)
            }

            val directory = File(context.cacheDir, TEXT_EXPORT_DIRECTORY).apply {
                if (!exists() && !mkdirs()) {
                    throw OcrTextExportException(OcrTextExportFailure.WRITE_FAILED)
                }
            }
            cleanupExpiredTextExports(directory)
            if (directory.usableSpace < MIN_FREE_SPACE_BYTES) {
                throw OcrTextExportException(OcrTextExportFailure.STORAGE_FULL)
            }

            val displayName = "${details.document.title.toSafeTextFileName()}-${System.currentTimeMillis()}.txt"
            val destination = File(directory, displayName)
            val temporary = File(directory, ".$displayName.part")
            var committed = false
            try {
                temporary.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.appendLine(details.document.title)
                    writer.appendLine("Xuất từ GScan")
                    writer.appendLine()
                    exportablePages.forEachIndexed { index, page ->
                        currentCoroutineContext().ensureActive()
                        if (index > 0) writer.appendLine()
                        writer.appendLine("===== Trang ${page.position + 1} =====")
                        val result = resultsByPageId[page.id]?.result
                        writer.appendLine(result.toExportText())
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!temporary.renameTo(destination)) {
                    throw OcrTextExportException(OcrTextExportFailure.WRITE_FAILED)
                }
                committed = true
                ExportedOcrText(
                    filePath = destination.absolutePath,
                    displayName = displayName,
                    pageCount = pages.size,
                    exportedPageCount = exportablePages.size,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: OcrTextExportException) {
                throw error
            } catch (error: IOException) {
                val reason = if (directory.usableSpace < MIN_FREE_SPACE_BYTES) {
                    OcrTextExportFailure.STORAGE_FULL
                } else {
                    OcrTextExportFailure.WRITE_FAILED
                }
                throw OcrTextExportException(reason, error)
            } catch (error: Exception) {
                throw OcrTextExportException(OcrTextExportFailure.UNKNOWN, error)
            } finally {
                if (!committed) temporary.delete()
            }
        }
    }

    override suspend fun saveTextExport(
        exportedText: ExportedOcrText,
        destinationUri: String,
    ) = withContext(Dispatchers.IO) {
        val source = requireValidTextExport(exportedText.filePath)
        try {
            val output = context.contentResolver.openOutputStream(destinationUri.toUri(), "w")
                ?: throw OcrTextExportException(OcrTextExportFailure.WRITE_FAILED)
            source.inputStream().use { input ->
                output.use { target ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: OcrTextExportException) {
            throw error
        } catch (error: IOException) {
            throw OcrTextExportException(OcrTextExportFailure.WRITE_FAILED, error)
        } catch (error: SecurityException) {
            throw OcrTextExportException(OcrTextExportFailure.WRITE_FAILED, error)
        }
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

    private fun OcrResultEntity?.toExportText(): String = when {
        this == null -> "[Chưa nhận dạng]"
        status == OcrPageStatus.SUCCEEDED.name && text.isNotBlank() -> text
        status == OcrPageStatus.SUCCEEDED.name -> "[Không tìm thấy văn bản]"
        status == OcrPageStatus.FAILED.name && text.isNotBlank() ->
            "[Kết quả OCR trước; lần nhận dạng gần nhất thất bại]\n$text"
        status == OcrPageStatus.FAILED.name -> "[Nhận dạng thất bại]"
        text.isNotBlank() -> "[Kết quả OCR trước; nhận dạng chưa hoàn tất]\n$text"
        else -> "[Chưa nhận dạng xong]"
    }

    private fun requireValidTextExport(filePath: String): File {
        val directory = File(context.cacheDir, TEXT_EXPORT_DIRECTORY).canonicalFile
        val file = File(filePath).canonicalFile
        if (file.parentFile != directory || !file.isFile || file.extension.lowercase() != "txt") {
            throw OcrTextExportException(OcrTextExportFailure.SOURCE_UNAVAILABLE)
        }
        return file
    }

    private fun cleanupExpiredTextExports(directory: File) {
        val cutoff = System.currentTimeMillis() - TEXT_EXPORT_RETENTION_MILLIS
        directory.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    private fun String.toSafeTextFileName(): String =
        replace(INVALID_FILE_NAME_CHARACTERS, "_")
            .trim()
            .trim('.')
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { DEFAULT_FILE_NAME }

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
        const val TEXT_EXPORT_DIRECTORY = "text_exports"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MIN_FREE_SPACE_BYTES = 1024L * 1024L
        const val TEXT_EXPORT_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L
        const val MAX_FILE_NAME_LENGTH = 40
        const val DEFAULT_FILE_NAME = "GScan-OCR"
        val INVALID_FILE_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|]")
    }
}
