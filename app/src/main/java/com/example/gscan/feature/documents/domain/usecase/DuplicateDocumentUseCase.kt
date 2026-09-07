package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.DocumentPageSelection
import com.example.gscan.feature.documents.domain.model.MAX_DOCUMENT_TITLE_LENGTH
import com.example.gscan.feature.documents.domain.model.PageEditException
import com.example.gscan.feature.documents.domain.model.PageEditFailure
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class DuplicateDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String): String {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        val details = repository.observeDocumentDetails(documentId).first()
            ?: throw PageEditException(PageEditFailure.DOCUMENT_NOT_FOUND)
        if (details.pages.isEmpty()) {
            throw PageEditException(PageEditFailure.EMPTY_DOCUMENT)
        }

        return repository.composeDocument(
            title = details.document.title.toCopyTitle(),
            selections = listOf(
                DocumentPageSelection(
                    documentId = documentId,
                    pageIds = details.pages.map { it.id },
                ),
            ),
            onProgress = { _, _ -> },
        )
    }
}

private fun String.toCopyTitle(): String {
    val suffix = " (bản sao)"
    return take(MAX_DOCUMENT_TITLE_LENGTH - suffix.length).trimEnd() + suffix
}
