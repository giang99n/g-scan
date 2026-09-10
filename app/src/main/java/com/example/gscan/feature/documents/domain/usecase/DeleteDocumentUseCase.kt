package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject

class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String) {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        repository.delete(documentId)
    }
}
