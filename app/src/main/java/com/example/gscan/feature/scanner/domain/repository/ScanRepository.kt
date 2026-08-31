package com.example.gscan.feature.scanner.domain.repository

interface ScanRepository {
    suspend fun saveScan(
        title: String,
        sourceUris: List<String>,
    ): String
}
