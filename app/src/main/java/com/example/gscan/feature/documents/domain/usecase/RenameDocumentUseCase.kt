package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.DocumentEditException
import com.example.gscan.feature.documents.domain.model.DocumentEditFailure
import com.example.gscan.feature.documents.domain.model.MAX_DOCUMENT_TITLE_LENGTH
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject

class RenameDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String, title: String) {
        val normalizedTitle = title
            .map { character -> if (character.isISOControl()) ' ' else character }
            .joinToString(separator = "")
            .trim()
            .replace(WHITESPACE_PATTERN, " ")
        if (normalizedTitle.isEmpty()) {
            throw DocumentEditException(DocumentEditFailure.EMPTY_TITLE)
        }
        if (normalizedTitle.length > MAX_DOCUMENT_TITLE_LENGTH) {
            throw DocumentEditException(DocumentEditFailure.TITLE_TOO_LONG)
        }
        repository.rename(documentId, normalizedTitle)
    }

    private companion object {
        val WHITESPACE_PATTERN = Regex("\\s+")
    }
}
