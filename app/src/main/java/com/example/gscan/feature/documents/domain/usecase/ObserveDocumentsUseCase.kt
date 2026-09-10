package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    operator fun invoke(query: String = ""): Flow<List<ScannedDocument>> =
        repository.observeDocuments(query)
}
