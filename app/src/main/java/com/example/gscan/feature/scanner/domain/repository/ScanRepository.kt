package com.example.gscan.feature.scanner.domain.repository

interface ScanRepository {
    suspend fun saveDocument(
        title: String,
        sourceUris: List<String>,
    ): String

    suspend fun savePdfDocument(
        title: String,
        sourceUri: String,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit,
    ): String
}
