package com.example.gscan.feature.documents.domain.repository

import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.DocumentPageSelection
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    suspend fun composeDocument(
        title: String,
        selections: List<DocumentPageSelection>,
        onProgress: (Int, Int) -> Unit,
    ): String

    fun observeDocuments(query: String = ""): Flow<List<ScannedDocument>>

    fun observeTrash(): Flow<List<ScannedDocument>>

    fun observeDocumentDetails(documentId: String): Flow<ScannedDocumentDetails?>

    suspend fun rotatePageClockwise(documentId: String, pageId: String)

    suspend fun movePage(documentId: String, pageId: String, targetPosition: Int)

    suspend fun deletePage(documentId: String, pageId: String)

    suspend fun addPages(documentId: String, sourceUris: List<String>)

    suspend fun rename(documentId: String, title: String)

    suspend fun delete(id: String)

    suspend fun restore(id: String)

    suspend fun permanentlyDelete(id: String)

    suspend fun emptyTrash(): Int
}
