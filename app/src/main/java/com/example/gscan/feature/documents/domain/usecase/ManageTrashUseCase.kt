package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ManageTrashUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    fun observe(): Flow<List<ScannedDocument>> = repository.observeTrash()

    suspend fun restore(documentId: String) {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        repository.restore(documentId)
    }

    suspend fun permanentlyDelete(documentId: String) {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        repository.permanentlyDelete(documentId)
    }

    suspend fun empty(): Int = repository.emptyTrash()
}
