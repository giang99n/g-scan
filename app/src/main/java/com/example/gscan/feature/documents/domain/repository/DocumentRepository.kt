package com.example.gscan.feature.documents.domain.repository

import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<ScannedDocument>>

    fun observeDocumentDetails(documentId: String): Flow<ScannedDocumentDetails?>

    suspend fun rotatePageClockwise(documentId: String, pageId: String)

    suspend fun movePage(documentId: String, pageId: String, targetPosition: Int)

    suspend fun deletePage(documentId: String, pageId: String)

    suspend fun delete(id: String)
}
