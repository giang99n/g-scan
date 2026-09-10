package com.example.gscan.feature.export.domain.model

data class ExportedPdf(
    val filePath: String,
    val displayName: String,
    val pageCount: Int,
)

enum class PdfQualityPreset {
    SMALL,
    BALANCED,
    HIGH,
}

enum class PdfExportFailure {
    DOCUMENT_NOT_FOUND,
    NO_PAGES,
    SOURCE_UNAVAILABLE,
    STORAGE_FULL,
    INSUFFICIENT_MEMORY,
    WRITE_FAILED,
    UNKNOWN,
}

class PdfExportException(
    val reason: PdfExportFailure,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause)
