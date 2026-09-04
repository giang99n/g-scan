package com.example.gscan.feature.ocr.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.gscan.feature.ocr.domain.repository.OcrRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class DocumentOcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val repository: OcrRepository,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        return try {
            val summary = repository.recognizeDocument(documentId) { completed, total ->
                setProgress(workDataOf(KEY_COMPLETED_PAGES to completed, KEY_TOTAL_PAGES to total))
            }
            if (summary.failedPages == 0) {
                Result.success()
            } else {
                Result.failure(workDataOf(KEY_FAILED_PAGES to summary.failedPages))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_COMPLETED_PAGES = "completedPages"
        const val KEY_TOTAL_PAGES = "totalPages"
        const val KEY_FAILED_PAGES = "failedPages"
        private const val KEY_DOCUMENT_ID = "documentId"

        fun inputData(documentId: String): Data = workDataOf(KEY_DOCUMENT_ID to documentId)
    }
}
