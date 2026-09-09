package com.example.gscan.feature.documents.domain.repository

import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.DocumentPageSelection
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.model.DocumentFolder
import com.example.gscan.feature.documents.domain.model.DocumentTag
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    suspend fun composeDocument(
        title: String,
        selections: List<DocumentPageSelection>,
        onProgress: (Int, Int) -> Unit,
    ): String

    fun observeDocuments(query: String = ""): Flow<List<ScannedDocument>>

    fun observeFolders(): Flow<List<DocumentFolder>>

    fun observeTags(): Flow<List<DocumentTag>>

    fun observeTrash(): Flow<List<ScannedDocument>>

    fun observeDocumentDetails(documentId: String): Flow<ScannedDocumentDetails?>

    suspend fun rotatePageClockwise(documentId: String, pageId: String)

    suspend fun movePage(documentId: String, pageId: String, targetPosition: Int)

    suspend fun deletePage(documentId: String, pageId: String)

    suspend fun duplicatePage(documentId: String, pageId: String): String

    suspend fun addPages(documentId: String, sourceUris: List<String>)

    suspend fun rename(documentId: String, title: String)

    suspend fun delete(id: String)

    suspend fun restore(id: String)

    suspend fun permanentlyDelete(id: String)

    suspend fun emptyTrash(): Int

    suspend fun setFavorite(documentIds: Set<String>, favorite: Boolean)

    suspend fun moveToFolder(documentIds: Set<String>, folderId: String?)

    suspend fun updateTags(
        documentIds: Set<String>,
        addedTagIds: Set<String>,
        removedTagIds: Set<String>,
    )

    suspend fun moveToTrash(documentIds: Set<String>)

    suspend fun createFolder(name: String)

    suspend fun renameFolder(folderId: String, name: String)

    suspend fun deleteFolder(folderId: String)

    suspend fun createTag(name: String)

    suspend fun renameTag(tagId: String, name: String)

    suspend fun deleteTag(tagId: String)
}
