package com.example.gscan.feature.export.domain.usecase

import com.example.gscan.feature.export.domain.model.ExportedPdf
import com.example.gscan.feature.export.domain.model.PdfQualityPreset
import com.example.gscan.feature.export.domain.repository.PdfExportRepository
import javax.inject.Inject

class CreatePdfUseCase @Inject constructor(
    private val repository: PdfExportRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        preset: PdfQualityPreset,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit,
    ): ExportedPdf = repository.createPdf(documentId, preset, onProgress)
}

class SavePdfUseCase @Inject constructor(
    private val repository: PdfExportRepository,
) {
    suspend operator fun invoke(exportedPdf: ExportedPdf, destinationUri: String) {
        repository.savePdf(exportedPdf, destinationUri)
    }
}
