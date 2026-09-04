package com.example.gscan.feature.documents.data

import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.database.dao.PageMutationException
import com.example.gscan.core.database.dao.PageMutationFailure
import com.example.gscan.core.storage.DocumentFileStorage
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.model.PageEditException
import com.example.gscan.feature.documents.domain.model.PageEditFailure
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineDocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val storage: DocumentFileStorage,
    private val operationLock: DocumentOperationLock,
) : DocumentRepository {
    override fun observeDocuments(query: String): Flow<List<ScannedDocument>> {
        val normalizedQuery = query.trim()
        val summaries = if (normalizedQuery.isEmpty()) {
            documentDao.observeAllSummaries()
        } else {
            documentDao.observeSearchSummaries(
                titlePattern = "%${normalizedQuery.escapeLikePattern()}%",
                ftsQuery = normalizedQuery.toFtsQuery(),
            )
        }
        return summaries.map { rows ->
            rows.map { summary ->
                summary.document.toDomain(summary.thumbnailRotationDegrees)
            }
        }
    }

    override fun observeDocumentDetails(documentId: String): Flow<ScannedDocumentDetails?> =
        documentDao.observeWithPages(documentId).map { it?.toDomain() }

    override suspend fun rotatePageClockwise(documentId: String, pageId: String) {
        mutatePages {
            documentDao.rotatePageClockwise(documentId, pageId, System.currentTimeMillis())
        }
    }

    override suspend fun movePage(documentId: String, pageId: String, targetPosition: Int) {
        mutatePages {
            documentDao.movePage(documentId, pageId, targetPosition, System.currentTimeMillis())
        }
    }

    override suspend fun deletePage(documentId: String, pageId: String) {
        operationLock.mutex.withLock {
            val sourceUri = try {
                documentDao.deletePage(documentId, pageId, System.currentTimeMillis())
            } catch (error: PageMutationException) {
                throw error.toDomainException()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw PageEditException(PageEditFailure.UNKNOWN, error)
            }
            withContext(NonCancellable) {
                runCatching { storage.deletePage(documentId, sourceUri) }
            }
        }
    }

    override suspend fun delete(id: String) {
        operationLock.mutex.withLock {
            val deletedRows = documentDao.deleteDocument(id)
            if (deletedRows > 0) {
                withContext(NonCancellable) {
                    // Room là source of truth. Nếu cleanup lỗi, startup reconciliation
                    // sẽ nhận ra thư mục orphan và thử dọn lại an toàn.
                    runCatching { storage.deleteDocument(id) }
                }
            }
        }
    }

    private suspend fun mutatePages(block: suspend () -> Unit) {
        operationLock.mutex.withLock {
            try {
                block()
            } catch (error: PageMutationException) {
                throw error.toDomainException()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw PageEditException(PageEditFailure.UNKNOWN, error)
            }
        }
    }
}

private fun String.escapeLikePattern(): String = buildString(length) {
    this@escapeLikePattern.forEach { character ->
        if (character == '\\' || character == '%' || character == '_') append('\\')
        append(character)
    }
}

private fun String.toFtsQuery(): String {
    val tokens = Regex("[\\p{L}\\p{N}]+").findAll(this)
        .map { it.value }
        .take(MAX_SEARCH_TOKENS)
        .toList()
    return if (tokens.isEmpty()) {
        NO_MATCH_FTS_QUERY
    } else {
        tokens.joinToString(" AND ") { token -> "\"$token\"*" }
    }
}

private const val MAX_SEARCH_TOKENS = 8
private const val NO_MATCH_FTS_QUERY = "gscan_no_matching_token_7f3a*"

private fun PageMutationException.toDomainException() = PageEditException(
    reason = when (failure) {
        PageMutationFailure.DOCUMENT_NOT_FOUND -> PageEditFailure.DOCUMENT_NOT_FOUND
        PageMutationFailure.PAGE_NOT_FOUND -> PageEditFailure.PAGE_NOT_FOUND
        PageMutationFailure.LAST_PAGE -> PageEditFailure.LAST_PAGE
        PageMutationFailure.INVALID_POSITION -> PageEditFailure.INVALID_POSITION
    },
    cause = this,
)
