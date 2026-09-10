package com.example.gscan.feature.scanner.domain.model

enum class ScanSaveFailure {
    NO_PAGES,
    TOO_MANY_PAGES,
    SOURCE_UNAVAILABLE,
    STORAGE_FULL,
    INVALID_IMAGE,
    INVALID_PDF,
    PDF_PASSWORD_PROTECTED,
    INSUFFICIENT_MEMORY,
    DATABASE_ERROR,
    CLEANUP_FAILED,
    UNKNOWN,
}

class ScanSaveException(
    val failure: ScanSaveFailure,
    cause: Throwable? = null,
) : RuntimeException(failure.name, cause)
