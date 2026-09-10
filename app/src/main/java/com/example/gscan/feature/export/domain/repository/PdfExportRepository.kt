package com.example.gscan.feature.export.domain.repository

import com.example.gscan.feature.export.domain.model.ExportedPdf
import com.example.gscan.feature.export.domain.model.PdfQualityPreset

interface PdfExportRepository {
    suspend fun createPdf(
        documentId: String,
        preset: PdfQualityPreset,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit,
    ): ExportedPdf

    suspend fun savePdf(exportedPdf: ExportedPdf, destinationUri: String)
}
