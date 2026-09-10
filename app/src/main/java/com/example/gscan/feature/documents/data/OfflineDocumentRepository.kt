package com.example.gscan.feature.documents.data

import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.database.dao.PageMutationException
import com.example.gscan.core.database.dao.PageMutationFailure
import com.example.gscan.core.database.model.PageEntity
import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.FolderEntity
import com.example.gscan.core.database.model.TagEntity
import com.example.gscan.feature.documents.domain.model.DocumentPageSelection
import com.example.gscan.core.storage.DocumentFileStorage
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.core.storage.DocumentStorageException
import com.example.gscan.core.storage.StorageFailureReason
import com.example.gscan.feature.documents.domain.model.MAX_PAGES_PER_DOCUMENT
import com.example.gscan.feature.documents.domain.model.DocumentEditException
import com.example.gscan.feature.documents.domain.model.DocumentEditFailure
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.documents.domain.model.PageEditException
import com.example.gscan.feature.documents.domain.model.PageEditFailure
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.model.DocumentFolder
import com.example.gscan.feature.documents.domain.model.DocumentTag
import com.example.gscan.feature.documents.domain.model.MAX_ORGANIZATION_NAME_LENGTH
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineDocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val storage: DocumentFileStorage,
    private val operationLock: DocumentOperationLock,
) : DocumentRepository {
    override suspend fun composeDocument(
        title: String,
        selections: List<DocumentPageSelection>,
        onProgress: (Int, Int) -> Unit,
    ): String = operationLock.mutex.withLock {
        val sources = selections.flatMap { selection ->
            val pages = documentDao.getWithPages(selection.documentId)?.pages
                ?: throw IllegalArgumentException("Tài liệu nguồn đã bị xóa. Hãy chọn lại.")
            val byId = pages.associateBy { it.id }
            selection.pageIds.map { pageId ->
                byId[pageId] ?: throw IllegalArgumentException("Trang nguồn đã thay đổi. Hãy chọn lại.")
            }
        }
        require(sources.size in 1..MAX_PAGES_PER_DOCUMENT) { "Hãy chọn từ 1 đến 100 trang." }
        val id = UUID.randomUUID().toString()
        try {
            onProgress(0, sources.size)
            val copied = storage.copyDocumentPages(id, sources.map { it.sourceUri }, onProgress)
            val now = System.currentTimeMillis()
            documentDao.insertDocumentWithPages(
                DocumentEntity(
                    id = id,
                    title = title,
                    pageCount = copied.size,
                    thumbnailUri = copied.first().sourceUri,
                    status = DocumentStatus.READY.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
                copied.mapIndexed { index, page ->
                    sources[index].copy(
                        id = UUID.randomUUID().toString(), documentId = id, position = index,
                        sourceUri = page.sourceUri, createdAtEpochMillis = now,
                    )
                },
            )
            id
        } catch (error: Exception) {
            // Cancellation can arrive after Room commits: only remove proven uncommitted files.
            val committed = withContext(NonCancellable) {
                runCatching { documentDao.exists(id) }.getOrNull().also { exists ->
                    if (exists == false) runCatching { storage.deleteDocument(id) }
                }
            }
            if (committed == true) id else {
                if (error is DocumentStorageException) throw error.toPageEditException()
                throw error
            }
        }
    }

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
        return combine(summaries, documentDao.observeDocumentTags()) { rows, tagRows ->
            val tagsByDocument = tagRows.groupBy { it.documentId }
            rows.map { summary ->
                summary.document.toDomain(
                    summary.thumbnailRotationDegrees,
                    summary.thumbnailSignatureInk,
                    summary.folderName,
                    tagsByDocument[summary.document.id].orEmpty().map { DocumentTag(it.tagId, it.tagName) },
                )
            }
        }
    }

    override fun observeFolders(): Flow<List<DocumentFolder>> =
        documentDao.observeFolders().map { rows -> rows.map { DocumentFolder(it.id, it.name) } }

    override fun observeTags(): Flow<List<DocumentTag>> =
        documentDao.observeTags().map { rows -> rows.map { DocumentTag(it.id, it.name) } }

    override fun observeDocumentDetails(documentId: String): Flow<ScannedDocumentDetails?> =
        documentDao.observeWithPages(documentId).map { it?.toDomain() }

    override fun observeTrash(): Flow<List<ScannedDocument>> =
        documentDao.observeTrashSummaries().map { rows ->
            rows.map { summary ->
                summary.document.toDomain(
                    summary.thumbnailRotationDegrees,
                    summary.thumbnailSignatureInk,
                    summary.folderName,
                )
            }
        }

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

    override suspend fun duplicatePage(documentId: String, pageId: String): String =
        operationLock.mutex.withLock {
            val currentPages = documentDao.getWithPages(documentId)?.pages
                ?: throw PageEditException(PageEditFailure.DOCUMENT_NOT_FOUND)
            if (currentPages.size >= MAX_PAGES_PER_DOCUMENT) {
                throw PageEditException(PageEditFailure.TOO_MANY_PAGES)
            }
            val sourcePage = currentPages.firstOrNull { it.id == pageId }
                ?: throw PageEditException(PageEditFailure.PAGE_NOT_FOUND)
            val storedPage = try {
                storage.appendDocumentPages(documentId, listOf(sourcePage.sourceUri)).single()
            } catch (error: DocumentStorageException) {
                throw error.toPageEditException()
            }
            val duplicatedPageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            try {
                withContext(NonCancellable) {
                    documentDao.duplicatePage(
                        documentId = documentId,
                        sourcePageId = pageId,
                        duplicatedPage = PageEntity(
                            id = duplicatedPageId,
                            documentId = documentId,
                            position = currentPages.size,
                            sourceUri = storedPage.sourceUri,
                            width = storedPage.width,
                            height = storedPage.height,
                            rotationDegrees = sourcePage.rotationDegrees,
                            createdAtEpochMillis = now,
                            signatureInk = sourcePage.signatureInk,
                        ),
                        maxPageCount = MAX_PAGES_PER_DOCUMENT,
                        updatedAtEpochMillis = now,
                    )
                }
                duplicatedPageId
            } catch (error: Exception) {
                val committed = withContext(NonCancellable) {
                    runCatching {
                        documentDao.pageExists(documentId, duplicatedPageId)
                    }.getOrNull().also { exists ->
                        if (exists == false) {
                            runCatching {
                                storage.deleteStoredPages(documentId, listOf(storedPage.sourceUri))
                            }
                        }
                    }
                }
                if (committed == true) {
                    duplicatedPageId
                } else {
                    when (error) {
                        is PageMutationException -> throw error.toDomainException()
                        is CancellationException -> throw error
                        else -> throw PageEditException(PageEditFailure.UNKNOWN, error)
                    }
                }
            }
        }

    override suspend fun addPages(documentId: String, sourceUris: List<String>) {
        if (sourceUris.isEmpty()) throw PageEditException(PageEditFailure.NO_PAGES)
        operationLock.mutex.withLock {
            val currentPages = documentDao.getWithPages(documentId)?.pages
                ?: throw PageEditException(PageEditFailure.DOCUMENT_NOT_FOUND)
            if (currentPages.size + sourceUris.size > MAX_PAGES_PER_DOCUMENT) {
                throw PageEditException(PageEditFailure.TOO_MANY_PAGES)
            }

            val storedPages = try {
                storage.appendDocumentPages(documentId, sourceUris)
            } catch (error: DocumentStorageException) {
                throw error.toPageEditException()
            }
            val now = System.currentTimeMillis()
            try {
                withContext(NonCancellable) {
                    documentDao.appendPages(
                        documentId = documentId,
                        pages = storedPages.mapIndexed { index, page ->
                            PageEntity(
                                id = UUID.randomUUID().toString(),
                                documentId = documentId,
                                position = currentPages.size + index,
                                sourceUri = page.sourceUri,
                                width = page.width,
                                height = page.height,
                                rotationDegrees = 0,
                                createdAtEpochMillis = now,
                            )
                        },
                        maxPageCount = MAX_PAGES_PER_DOCUMENT,
                        readyStatus = DocumentStatus.READY.name,
                        updatedAtEpochMillis = now,
                    )
                }
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    runCatching {
                        storage.deleteStoredPages(documentId, storedPages.map { it.sourceUri })
                    }
                }
                when (error) {
                    is PageMutationException -> throw error.toDomainException()
                    else -> throw PageEditException(PageEditFailure.UNKNOWN, error)
                }
            }
        }
    }

    override suspend fun rename(documentId: String, title: String) {
        operationLock.mutex.withLock {
            try {
                val currentTitle = documentDao.getDocumentTitle(documentId)
                    ?: throw DocumentEditException(DocumentEditFailure.DOCUMENT_NOT_FOUND)
                if (currentTitle == title) return@withLock

                val updatedRows = documentDao.renameDocument(
                    documentId = documentId,
                    title = title,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
                if (updatedRows == 0) {
                    throw DocumentEditException(DocumentEditFailure.DOCUMENT_NOT_FOUND)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: DocumentEditException) {
                throw error
            } catch (error: Exception) {
                throw DocumentEditException(DocumentEditFailure.UNKNOWN, error)
            }
        }
    }

    override suspend fun delete(id: String) {
        // Xóa mềm không đụng tới file nên không chờ các tác vụ dài như OCR/export.
        // Các mutation page/OCR đều kiểm tra document còn active trước khi commit.
        val updatedRows = withContext(NonCancellable) {
            documentDao.moveToTrash(id, System.currentTimeMillis())
        }
        if (updatedRows != 1) {
            throw DocumentEditException(DocumentEditFailure.DOCUMENT_NOT_FOUND)
        }
    }

    override suspend fun restore(id: String) {
        operationLock.mutex.withLock {
            val updatedRows = documentDao.restoreFromTrash(id, System.currentTimeMillis())
            if (updatedRows != 1) {
                throw DocumentEditException(DocumentEditFailure.DOCUMENT_NOT_FOUND)
            }
        }
    }

    override suspend fun permanentlyDelete(id: String) {
        operationLock.mutex.withLock {
            val deletedRows = withContext(NonCancellable) { documentDao.deleteDocument(id) }
            if (deletedRows != 1) {
                throw DocumentEditException(DocumentEditFailure.DOCUMENT_NOT_FOUND)
            }
            withContext(NonCancellable) {
                // Room là source of truth. Nếu cleanup lỗi, startup reconciliation
                // sẽ nhận ra thư mục orphan và thử dọn lại an toàn.
                runCatching { storage.deleteDocument(id) }
            }
        }
    }

    override suspend fun emptyTrash(): Int = operationLock.mutex.withLock {
        withContext(NonCancellable) {
            val deletedIds = documentDao.deleteAllTrashedDocuments()
            deletedIds.forEach { id -> runCatching { storage.deleteDocument(id) } }
            deletedIds.size
        }
    }

    override suspend fun setFavorite(documentIds: Set<String>, favorite: Boolean) {
        documentDao.setFavorite(documentIds, favorite, System.currentTimeMillis())
    }

    override suspend fun moveToFolder(documentIds: Set<String>, folderId: String?) {
        documentDao.moveDocuments(documentIds, folderId, System.currentTimeMillis())
    }

    override suspend fun updateTags(
        documentIds: Set<String>,
        addedTagIds: Set<String>,
        removedTagIds: Set<String>,
    ) {
        documentDao.updateDocumentTags(documentIds, addedTagIds, removedTagIds)
    }

    override suspend fun moveToTrash(documentIds: Set<String>) {
        withContext(NonCancellable) {
            documentDao.moveDocumentsToTrash(documentIds, System.currentTimeMillis())
        }
    }

    override suspend fun createFolder(name: String) {
        documentDao.insertFolder(FolderEntity(UUID.randomUUID().toString(), normalizeOrganizationName(name), System.currentTimeMillis()))
    }

    override suspend fun renameFolder(folderId: String, name: String) {
        documentDao.updateFolderName(folderId, normalizeOrganizationName(name))
    }

    override suspend fun deleteFolder(folderId: String) {
        if (documentDao.deleteFolder(folderId) != 1) {
            throw DocumentEditException(DocumentEditFailure.DOCUMENT_NOT_FOUND)
        }
    }

    override suspend fun createTag(name: String) {
        documentDao.insertTag(TagEntity(UUID.randomUUID().toString(), normalizeOrganizationName(name), System.currentTimeMillis()))
    }

    override suspend fun renameTag(tagId: String, name: String) {
        documentDao.updateTagName(tagId, normalizeOrganizationName(name))
    }

    override suspend fun deleteTag(tagId: String) {
        if (documentDao.deleteTag(tagId) != 1) {
            throw DocumentEditException(DocumentEditFailure.DOCUMENT_NOT_FOUND)
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

private fun normalizeOrganizationName(name: String): String {
    val normalized = name.trim().filterNot(Char::isISOControl)
    require(normalized.isNotEmpty()) { "Tên không được để trống." }
    require(normalized.length <= MAX_ORGANIZATION_NAME_LENGTH) { "Tên tối đa 40 ký tự." }
    return normalized
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
        PageMutationFailure.TOO_MANY_PAGES -> PageEditFailure.TOO_MANY_PAGES
    },
    cause = this,
)

private fun DocumentStorageException.toPageEditException() = PageEditException(
    reason = when (reason) {
        StorageFailureReason.SOURCE_UNAVAILABLE -> PageEditFailure.SOURCE_UNAVAILABLE
        StorageFailureReason.STORAGE_FULL -> PageEditFailure.STORAGE_FULL
        StorageFailureReason.INVALID_IMAGE -> PageEditFailure.INVALID_IMAGE
        else -> PageEditFailure.STORAGE
    },
    cause = this,
)
