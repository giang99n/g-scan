package com.example.gscan.feature.documents.domain.model

data class ScannedDocument(
    val id: String,
    val title: String,
    val pageCount: Int,
    val thumbnailUri: String?,
    val status: DocumentStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

enum class DocumentStatus {
    DRAFT,
    PROCESSING,
    READY,
    FAILED,
}
