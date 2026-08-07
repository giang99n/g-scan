package com.example.gscan.feature.documents.data

import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.documents.domain.model.ScannedDocument

internal fun DocumentEntity.toDomain() = ScannedDocument(
    id = id,
    title = title,
    pageCount = pageCount,
    thumbnailUri = thumbnailUri,
    status = runCatching { DocumentStatus.valueOf(status) }.getOrDefault(DocumentStatus.FAILED),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun ScannedDocument.toEntity() = DocumentEntity(
    id = id,
    title = title,
    pageCount = pageCount,
    thumbnailUri = thumbnailUri,
    status = status.name,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
