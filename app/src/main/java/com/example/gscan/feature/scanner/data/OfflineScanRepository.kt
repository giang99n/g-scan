package com.example.gscan.feature.scanner.data

import androidx.room.withTransaction
import com.example.gscan.core.database.GScanDatabase
import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.PageEntity
import com.example.gscan.core.storage.DocumentFileStorage
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.core.storage.DocumentStorageException
import com.example.gscan.core.storage.StorageFailureReason
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.model.ScanSaveFailure
import com.example.gscan.feature.scanner.domain.repository.ScanRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class OfflineScanRepository @Inject constructor(
    private val database: GScanDatabase,
    private val storage: DocumentFileStorage,
    private val operationLock: DocumentOperationLock,
) : ScanRepository {
    override suspend fun saveScan(
        title: String,
        sourceUris: List<String>,
    ): String {
        if (sourceUris.isEmpty()) {
            throw ScanSaveException(ScanSaveFailure.NO_PAGES)
        }

        return operationLock.mutex.withLock {
            val documentId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            try {
                val storedPages = storage.copyScannerPages(documentId, sourceUris)
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
                cleanupAfterCancellation(documentId)
                throw error
            } catch (error: Exception) {
                val commitState = documentCommitState(documentId)
                when (commitState) {
                    true -> documentId
                    false, null -> {
                        val cleanupFailed = commitState == null ||
                            cleanupAfterFailure(documentId)
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
    }

    private suspend fun cleanupAfterCancellation(documentId: String) {
        if (documentCommitState(documentId) == false) {
            withContext(NonCancellable) {
                runCatching { storage.deleteDocument(documentId) }
            }
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

private fun StorageFailureReason.toScanSaveFailure(): ScanSaveFailure = when (this) {
    StorageFailureReason.SOURCE_UNAVAILABLE -> ScanSaveFailure.SOURCE_UNAVAILABLE
    StorageFailureReason.STORAGE_FULL -> ScanSaveFailure.STORAGE_FULL
    StorageFailureReason.INVALID_IMAGE -> ScanSaveFailure.INVALID_IMAGE
    StorageFailureReason.WRITE_FAILED -> ScanSaveFailure.UNKNOWN
    StorageFailureReason.CLEANUP_FAILED -> ScanSaveFailure.CLEANUP_FAILED
}
