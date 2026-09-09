package com.example.gscan.feature.documents.domain.model

data class ScannedDocument(
    val id: String,
    val title: String,
    val pageCount: Int,
    val thumbnailUri: String?,
    val thumbnailRotationDegrees: Int,
    val status: DocumentStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
    val thumbnailSignatureInk: String = "[]",
    val isFavorite: Boolean = false,
    val folderId: String? = null,
    val folderName: String? = null,
    val tags: List<DocumentTag> = emptyList(),
)

data class DocumentFolder(val id: String, val name: String)

data class DocumentTag(val id: String, val name: String)

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
    val signatureInk: String = "[]",
)

enum class DocumentStatus {
    DRAFT,
    PROCESSING,
    READY,
    FAILED,
}

enum class PageEditFailure {
    DOCUMENT_NOT_FOUND,
    PAGE_NOT_FOUND,
    LAST_PAGE,
    INVALID_POSITION,
    NO_PAGES,
    EMPTY_DOCUMENT,
    TOO_MANY_PAGES,
    SOURCE_UNAVAILABLE,
    STORAGE_FULL,
    INVALID_IMAGE,
    STORAGE,
    UNKNOWN,
}

class PageEditException(
    val reason: PageEditFailure,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause)

const val MAX_PAGES_PER_DOCUMENT = 100

enum class DocumentEditFailure {
    DOCUMENT_NOT_FOUND,
    EMPTY_TITLE,
    TITLE_TOO_LONG,
    UNKNOWN,
}

class DocumentEditException(
    val reason: DocumentEditFailure,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause)

const val MAX_DOCUMENT_TITLE_LENGTH = 120
const val MAX_ORGANIZATION_NAME_LENGTH = 40
