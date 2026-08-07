package com.example.gscan.feature.documents.data

import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineDocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<ScannedDocument>> =
        documentDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun save(document: ScannedDocument) {
        documentDao.upsert(document.toEntity())
    }

    override suspend fun delete(id: String) {
        documentDao.deleteById(id)
    }
}
