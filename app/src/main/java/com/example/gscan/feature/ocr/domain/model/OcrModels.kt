package com.example.gscan.feature.ocr.domain.model

data class OcrPageText(
    val pageId: String,
    val position: Int,
    val text: String,
    val status: OcrPageStatus,
    val errorCode: String?,
    val updatedAtEpochMillis: Long,
)

enum class OcrPageStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
}

data class OcrJobState(
    val status: OcrJobStatus = OcrJobStatus.IDLE,
    val completedPages: Int = 0,
    val totalPages: Int = 0,
)

enum class OcrJobStatus {
    IDLE,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class OcrRunSummary(
    val totalPages: Int,
    val failedPages: Int,
)

enum class OcrFailure {
    DOCUMENT_NOT_FOUND,
    NO_PAGES,
    SOURCE_UNAVAILABLE,
    RECOGNITION_FAILED,
    UNKNOWN,
}

class OcrException(
    val reason: OcrFailure,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause)
