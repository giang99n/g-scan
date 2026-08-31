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

data class ScannedDocumentDetails(
    val document: ScannedDocument,
    val pages: List<ScannedPage>,
)

data class ScannedPage(
    val id: String,
    val position: Int,
    val sourceUri: String,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

enum class DocumentStatus {
    DRAFT,
    PROCESSING,
    READY,
    FAILED,
}
