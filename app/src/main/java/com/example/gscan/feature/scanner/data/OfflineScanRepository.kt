package com.example.gscan.feature.scanner.data

import android.content.Context
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.core.net.toUri
import com.example.gscan.core.database.GScanDatabase
import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.PageEntity
import com.example.gscan.core.storage.DocumentFileStorage
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.core.storage.DocumentStorageException
import com.example.gscan.core.storage.StorageFailureReason
import com.example.gscan.core.storage.StoredPage
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.model.ScanSaveFailure
import com.example.gscan.feature.scanner.domain.repository.ScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class OfflineScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GScanDatabase,
    private val storage: DocumentFileStorage,
    private val operationLock: DocumentOperationLock,
) : ScanRepository {
    override suspend fun saveDocument(
        title: String,
        sourceUris: List<String>,
    ): String = saveStoredDocument(title) { documentId ->
        if (sourceUris.isEmpty()) {
            throw ScanSaveException(ScanSaveFailure.NO_PAGES)
        }
        storage.copyDocumentPages(documentId, sourceUris)
    }

    override suspend fun savePdfDocument(
        title: String,
        sourceUri: String,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit,
    ): String {
        val resolvedTitle = withContext(Dispatchers.IO) { pdfTitle(sourceUri) } ?: title
        return saveStoredDocument(resolvedTitle) { documentId ->
            storage.renderPdfPages(documentId, sourceUri, onProgress)
        }
    }

    private fun pdfTitle(sourceUri: String): String? = runCatching {
        context.contentResolver.query(
            sourceUri.toUri(),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column < 0) return@use null
            cursor.getString(column)
                ?.trim()
                ?.removeSuffixIgnoringCase(".pdf")
                ?.trim()
                ?.filterNot(Char::isISOControl)
                ?.replace('/', ' ')
                ?.replace('\\', ' ')
                ?.trim()
                ?.take(MAX_DOCUMENT_TITLE_LENGTH)
                ?.takeIf(String::isNotBlank)
        }
    }.getOrNull()

    private suspend fun saveStoredDocument(
        title: String,
        storePages: suspend (documentId: String) -> List<StoredPage>,
    ): String = operationLock.mutex.withLock {
        val documentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        try {
            val storedPages = storePages(documentId)
            database.withTransaction {
                database.documentDao().insertDocumentWithPages(
                    document = DocumentEntity(
                        id = documentId,
                        title = title,
                        pageCount = storedPages.size,
                        thumbnailUri = storedPages.first().sourceUri,
                        status = DocumentStatus.READY.name,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                    pages = storedPages.mapIndexed { index, page ->
                        PageEntity(
                            id = UUID.randomUUID().toString(),
                            documentId = documentId,
                            position = index,
                            sourceUri = page.sourceUri,
                            width = page.width,
                            height = page.height,
                            rotationDegrees = 0,
                            createdAtEpochMillis = now,
                        )
                    },
                )
            }
            documentId
        } catch (error: CancellationException) {
            when (documentCommitState(documentId)) {
                true -> documentId
                false -> {
                    cleanupUncommittedDocument(documentId)
                    throw error
                }
                null -> throw error
            }
        } catch (error: Exception) {
            val commitState = documentCommitState(documentId)
            when (commitState) {
                true -> documentId
                false, null -> {
                    val cleanupFailed = commitState == null || cleanupAfterFailure(documentId)
                    val failure = when {
                        cleanupFailed -> ScanSaveFailure.CLEANUP_FAILED
                        error is DocumentStorageException -> error.reason.toScanSaveFailure()
                        else -> ScanSaveFailure.DATABASE_ERROR
                    }
                    throw ScanSaveException(failure, error)
                }
            }
        }
    }

    private suspend fun cleanupUncommittedDocument(documentId: String) {
        withContext(NonCancellable) {
            runCatching { storage.deleteDocument(documentId) }
        }
    }

    private suspend fun documentCommitState(documentId: String): Boolean? =
        withContext(NonCancellable) {
            runCatching { database.documentDao().exists(documentId) }.getOrNull()
        }

    private suspend fun cleanupAfterFailure(documentId: String): Boolean =
        withContext(NonCancellable) {
            runCatching { storage.deleteDocument(documentId) }.isFailure
        }
}

private fun String.removeSuffixIgnoringCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

private const val MAX_DOCUMENT_TITLE_LENGTH = 120

private fun StorageFailureReason.toScanSaveFailure(): ScanSaveFailure = when (this) {
    StorageFailureReason.SOURCE_UNAVAILABLE -> ScanSaveFailure.SOURCE_UNAVAILABLE
    StorageFailureReason.STORAGE_FULL -> ScanSaveFailure.STORAGE_FULL
    StorageFailureReason.INVALID_IMAGE -> ScanSaveFailure.INVALID_IMAGE
    StorageFailureReason.INVALID_PDF -> ScanSaveFailure.INVALID_PDF
    StorageFailureReason.PDF_PASSWORD_PROTECTED -> ScanSaveFailure.PDF_PASSWORD_PROTECTED
    StorageFailureReason.TOO_MANY_PAGES -> ScanSaveFailure.TOO_MANY_PAGES
    StorageFailureReason.INSUFFICIENT_MEMORY -> ScanSaveFailure.INSUFFICIENT_MEMORY
    StorageFailureReason.WRITE_FAILED -> ScanSaveFailure.UNKNOWN
    StorageFailureReason.CLEANUP_FAILED -> ScanSaveFailure.CLEANUP_FAILED
}
