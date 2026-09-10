package com.example.gscan.feature.ocr.domain.repository

import com.example.gscan.feature.ocr.domain.model.OcrJobState
import com.example.gscan.feature.ocr.domain.model.OcrPageText
import com.example.gscan.feature.ocr.domain.model.OcrRunSummary
import com.example.gscan.feature.ocr.domain.model.ExportedOcrText
import com.example.gscan.feature.ocr.domain.model.OcrTextExportMode
import kotlinx.coroutines.flow.Flow

interface OcrRepository {
    fun observeResults(documentId: String): Flow<List<OcrPageText>>

    fun observeJob(documentId: String): Flow<OcrJobState>

    fun enqueue(documentId: String)

    fun cancel(documentId: String)

    suspend fun recognizeDocument(
        documentId: String,
        onProgress: suspend (completedPages: Int, totalPages: Int) -> Unit,
    ): OcrRunSummary

    suspend fun createTextExport(
        documentId: String,
        mode: OcrTextExportMode,
    ): ExportedOcrText

    suspend fun saveTextExport(exportedText: ExportedOcrText, destinationUri: String)
}
