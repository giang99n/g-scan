package com.example.gscan.feature.documents.data

import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.storage.DocumentFileStorage
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineDocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val storage: DocumentFileStorage,
    private val operationLock: DocumentOperationLock,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<ScannedDocument>> =
        documentDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeDocumentDetails(documentId: String): Flow<ScannedDocumentDetails?> =
        documentDao.observeWithPages(documentId).map { it?.toDomain() }

    override suspend fun delete(id: String) {
        operationLock.mutex.withLock {
            documentDao.deleteById(id)
            withContext(NonCancellable) {
                storage.deleteDocument(id)
            }
        }
    }
}
