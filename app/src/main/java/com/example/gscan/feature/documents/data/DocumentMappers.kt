package com.example.gscan.feature.documents.data

import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.DocumentWithPages
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.model.ScannedPage

internal fun DocumentEntity.toDomain() = ScannedDocument(
    id = id,
    title = title,
    pageCount = pageCount,
    thumbnailUri = thumbnailUri,
    status = runCatching { DocumentStatus.valueOf(status) }.getOrDefault(DocumentStatus.FAILED),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun DocumentWithPages.toDomain() = ScannedDocumentDetails(
    document = document.toDomain(),
    pages = pages
        .sortedBy { it.position }
        .map { page ->
            ScannedPage(
                id = page.id,
                position = page.position,
                sourceUri = page.sourceUri,
                width = page.width,
                height = page.height,
                rotationDegrees = page.rotationDegrees,
            )
        },
)
