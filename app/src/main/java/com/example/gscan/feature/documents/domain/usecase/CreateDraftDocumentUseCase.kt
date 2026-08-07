package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import java.util.UUID
import javax.inject.Inject

class CreateDraftDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(title: String) {
        val now = System.currentTimeMillis()
        repository.save(
            ScannedDocument(
                id = UUID.randomUUID().toString(),
                title = title.trim().ifEmpty { "Tài liệu chưa đặt tên" },
                pageCount = 1,
                thumbnailUri = null,
                status = DocumentStatus.DRAFT,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }
}
