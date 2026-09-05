package com.example.gscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.DocumentSummary
import com.example.gscan.core.database.model.DocumentWithPages
import com.example.gscan.core.database.model.OcrResultEntity
import com.example.gscan.core.database.model.OcrResultWithPosition
import com.example.gscan.core.database.model.OcrSearchEntity
import com.example.gscan.core.database.model.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DocumentDao {
    @Query(
        "SELECT documents.*, COALESCE((SELECT rotationDegrees FROM pages " +
            "WHERE documentId = documents.id ORDER BY position LIMIT 1), 0) " +
            "AS thumbnailRotationDegrees FROM documents ORDER BY updatedAtEpochMillis DESC",
    )
    abstract fun observeAllSummaries(): Flow<List<DocumentSummary>>

    @Query(
        "SELECT documents.*, COALESCE((SELECT rotationDegrees FROM pages " +
            "WHERE documentId = documents.id ORDER BY position LIMIT 1), 0) " +
            "AS thumbnailRotationDegrees FROM documents " +
            "WHERE title LIKE :titlePattern ESCAPE '\\' OR id IN " +
            "(SELECT documentId FROM ocr_search WHERE ocr_search.text MATCH :ftsQuery) " +
            "ORDER BY updatedAtEpochMillis DESC",
    )
    abstract fun observeSearchSummaries(
        titlePattern: String,
        ftsQuery: String,
    ): Flow<List<DocumentSummary>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    abstract fun observeWithPages(documentId: String): Flow<DocumentWithPages?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    abstract suspend fun getWithPages(documentId: String): DocumentWithPages?

    @Query("SELECT id FROM documents")
    abstract suspend fun getAllIds(): List<String>

    @Query("SELECT sourceUri FROM pages")
    abstract suspend fun getAllPageSourceUris(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM documents WHERE id = :documentId)")
    abstract suspend fun exists(documentId: String): Boolean

    @Query("SELECT title FROM documents WHERE id = :documentId LIMIT 1")
    abstract suspend fun getDocumentTitle(documentId: String): String?

    @Query("DELETE FROM documents WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Query(
        "UPDATE documents SET title = :title, updatedAtEpochMillis = :updatedAtEpochMillis " +
            "WHERE id = :documentId",
    )
    abstract suspend fun renameDocument(
        documentId: String,
        title: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM ocr_search WHERE documentId = :documentId")
    abstract suspend fun deleteOcrSearchByDocumentId(documentId: String)

    @Query("DELETE FROM ocr_search WHERE pageId = :pageId")
    abstract suspend fun deleteOcrSearchByPageId(pageId: String)

    @Query(
        "SELECT ocr_results.*, pages.position AS position FROM ocr_results " +
            "INNER JOIN pages ON pages.id = ocr_results.pageId " +
            "WHERE ocr_results.documentId = :documentId ORDER BY pages.position",
    )
    abstract fun observeOcrResults(documentId: String): Flow<List<OcrResultWithPosition>>

    @Query(
        "SELECT ocr_results.*, pages.position AS position FROM ocr_results " +
            "INNER JOIN pages ON pages.id = ocr_results.pageId " +
            "WHERE ocr_results.documentId = :documentId ORDER BY pages.position",
    )
    abstract suspend fun getOcrResults(documentId: String): List<OcrResultWithPosition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOcrResult(result: OcrResultEntity)

    @Insert
    abstract suspend fun insertOcrSearch(result: OcrSearchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertPages(pages: List<PageEntity>)

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position")
    abstract suspend fun getPages(documentId: String): List<PageEntity>

    @Query("UPDATE pages SET rotationDegrees = :rotationDegrees WHERE id = :pageId AND documentId = :documentId")
    abstract suspend fun updatePageRotation(
        documentId: String,
        pageId: String,
        rotationDegrees: Int,
    ): Int

    @Query("UPDATE pages SET position = position + :offset WHERE documentId = :documentId")
    abstract suspend fun shiftPagePositions(documentId: String, offset: Int)

    @Query("UPDATE pages SET position = :position WHERE id = :pageId AND documentId = :documentId")
    abstract suspend fun updatePagePosition(
        documentId: String,
        pageId: String,
        position: Int,
    ): Int

    @Query("DELETE FROM pages WHERE id = :pageId AND documentId = :documentId")
    abstract suspend fun deletePageById(documentId: String, pageId: String): Int

    @Query(
        "UPDATE documents SET pageCount = :pageCount, thumbnailUri = :thumbnailUri, " +
            "updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :documentId",
    )
    abstract suspend fun updateDocumentPageSummary(
        documentId: String,
        pageCount: Int,
        thumbnailUri: String?,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE documents SET pageCount = :pageCount, thumbnailUri = :thumbnailUri, " +
            "status = :status, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :documentId",
    )
    abstract suspend fun updateDocumentAfterPageAppend(
        documentId: String,
        pageCount: Int,
        thumbnailUri: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun insertDocumentWithPages(
        document: DocumentEntity,
        pages: List<PageEntity>,
    ) {
        require(pages.isNotEmpty()) { "A scanned document must contain at least one page" }
        insertDocument(document)
        insertPages(pages)
    }

    @Transaction
    open suspend fun rotatePageClockwise(
        documentId: String,
        pageId: String,
        updatedAtEpochMillis: Long,
    ) {
        val pages = requireDocumentPages(documentId)
        val page = pages.firstOrNull { it.id == pageId }
            ?: throw PageMutationException(PageMutationFailure.PAGE_NOT_FOUND)
        updatePageRotation(
            documentId = documentId,
            pageId = pageId,
            rotationDegrees = (page.rotationDegrees + 90) % 360,
        )
        updateDocumentPageSummary(
            documentId = documentId,
            pageCount = pages.size,
            thumbnailUri = pages.first().sourceUri,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    @Transaction
    open suspend fun movePage(
        documentId: String,
        pageId: String,
        targetPosition: Int,
        updatedAtEpochMillis: Long,
    ) {
        val pages = requireDocumentPages(documentId)
        val currentPosition = pages.indexOfFirst { it.id == pageId }
        if (currentPosition < 0) {
            throw PageMutationException(PageMutationFailure.PAGE_NOT_FOUND)
        }
        if (targetPosition !in pages.indices) {
            throw PageMutationException(PageMutationFailure.INVALID_POSITION)
        }
        if (currentPosition == targetPosition) return

        val reordered = pages.toMutableList().apply {
            add(targetPosition, removeAt(currentPosition))
        }
        rewritePagePositions(documentId, reordered)
        updateDocumentPageSummary(
            documentId = documentId,
            pageCount = reordered.size,
            thumbnailUri = reordered.first().sourceUri,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    @Transaction
    open suspend fun deletePage(
        documentId: String,
        pageId: String,
        updatedAtEpochMillis: Long,
    ): String {
        val pages = requireDocumentPages(documentId)
        val deletedPage = pages.firstOrNull { it.id == pageId }
            ?: throw PageMutationException(PageMutationFailure.PAGE_NOT_FOUND)
        if (pages.size == 1) {
            throw PageMutationException(PageMutationFailure.LAST_PAGE)
        }

        deleteOcrSearchByPageId(pageId)
        deletePageById(documentId, pageId)
        val remainingPages = pages.filterNot { it.id == pageId }
        rewritePagePositions(documentId, remainingPages)
        updateDocumentPageSummary(
            documentId = documentId,
            pageCount = remainingPages.size,
            thumbnailUri = remainingPages.first().sourceUri,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        return deletedPage.sourceUri
    }

    @Transaction
    open suspend fun appendPages(
        documentId: String,
        pages: List<PageEntity>,
        maxPageCount: Int,
        readyStatus: String,
        updatedAtEpochMillis: Long,
    ) {
        val existingPages = requireDocumentPages(documentId)
        if (pages.isEmpty()) return
        if (existingPages.size + pages.size > maxPageCount) {
            throw PageMutationException(PageMutationFailure.TOO_MANY_PAGES)
        }
        val expectedPositions = existingPages.size until (existingPages.size + pages.size)
        if (pages.map(PageEntity::position) != expectedPositions.toList() ||
            pages.any { it.documentId != documentId }
        ) {
            throw PageMutationException(PageMutationFailure.INVALID_POSITION)
        }

        insertPages(pages)
        updateDocumentAfterPageAppend(
            documentId = documentId,
            pageCount = existingPages.size + pages.size,
            thumbnailUri = existingPages.firstOrNull()?.sourceUri ?: pages.first().sourceUri,
            status = readyStatus,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    @Transaction
    open suspend fun deleteDocument(documentId: String): Int {
        deleteOcrSearchByDocumentId(documentId)
        return deleteById(documentId)
    }

    @Transaction
    open suspend fun prepareOcr(documentId: String, results: List<OcrResultEntity>) {
        if (!exists(documentId)) throw PageMutationException(PageMutationFailure.DOCUMENT_NOT_FOUND)
        results.forEach { upsertOcrResult(it) }
    }

    @Transaction
    open suspend fun saveOcrResult(result: OcrResultEntity) {
        if (!exists(result.documentId)) {
            throw PageMutationException(PageMutationFailure.DOCUMENT_NOT_FOUND)
        }
        upsertOcrResult(result)
        deleteOcrSearchByPageId(result.pageId)
        if (result.text.isNotBlank()) {
            insertOcrSearch(
                OcrSearchEntity(
                    pageId = result.pageId,
                    documentId = result.documentId,
                    text = result.text,
                ),
            )
        }
    }

    private suspend fun requireDocumentPages(documentId: String): List<PageEntity> {
        if (!exists(documentId)) {
            throw PageMutationException(PageMutationFailure.DOCUMENT_NOT_FOUND)
        }
        return getPages(documentId)
    }

    private suspend fun rewritePagePositions(
        documentId: String,
        pages: List<PageEntity>,
    ) {
        shiftPagePositions(documentId, pages.size + 1)
        pages.forEachIndexed { position, page ->
            updatePagePosition(documentId, page.id, position)
        }
    }
}

enum class PageMutationFailure {
    DOCUMENT_NOT_FOUND,
    PAGE_NOT_FOUND,
    LAST_PAGE,
    INVALID_POSITION,
    TOO_MANY_PAGES,
}

class PageMutationException(val failure: PageMutationFailure) : IllegalStateException(failure.name)
