package com.example.gscan.feature.documents.domain.repository

import com.example.gscan.feature.documents.domain.model.ScannedDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<ScannedDocument>>

    suspend fun save(document: ScannedDocument)

    suspend fun delete(id: String)
}
