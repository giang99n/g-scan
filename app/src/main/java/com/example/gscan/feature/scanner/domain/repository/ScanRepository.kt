package com.example.gscan.feature.scanner.domain.repository

interface ScanRepository {
    suspend fun saveDocument(
        title: String,
        sourceUris: List<String>,
    ): String
}
