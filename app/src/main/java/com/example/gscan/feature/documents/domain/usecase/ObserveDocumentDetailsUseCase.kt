package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDocumentDetailsUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    operator fun invoke(documentId: String): Flow<ScannedDocumentDetails?> =
        repository.observeDocumentDetails(documentId)
}
