package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject

class ManageDocumentPagesUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend fun rotateClockwise(documentId: String, pageId: String) {
        repository.rotatePageClockwise(documentId, pageId)
    }

    suspend fun move(documentId: String, pageId: String, targetPosition: Int) {
        require(targetPosition >= 0) { "targetPosition must be non-negative" }
        repository.movePage(documentId, pageId, targetPosition)
    }

    suspend fun delete(documentId: String, pageId: String) {
        repository.deletePage(documentId, pageId)
    }

    suspend fun add(documentId: String, sourceUris: List<String>) {
        repository.addPages(documentId, sourceUris)
    }
}
