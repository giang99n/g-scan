package com.example.gscan.di

import com.example.gscan.feature.documents.data.OfflineDocumentRepository
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import com.example.gscan.feature.export.data.OfflinePdfExportRepository
import com.example.gscan.feature.export.domain.repository.PdfExportRepository
import com.example.gscan.feature.ocr.data.OfflineOcrRepository
import com.example.gscan.feature.ocr.domain.repository.OcrRepository
import com.example.gscan.feature.scanner.data.OfflineScanRepository
import com.example.gscan.feature.scanner.domain.repository.ScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindBarcodeScanner(
        implementation: com.example.gscan.feature.tools.data.GoogleBarcodeScanner,
    ): com.example.gscan.feature.tools.domain.BarcodeScanner

    @Binds
    @Singleton
    abstract fun bindBarcodeHistoryRepository(
        implementation: com.example.gscan.feature.tools.data.OfflineBarcodeHistoryRepository,
    ): com.example.gscan.feature.tools.domain.BarcodeHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSignatureRepository(
        implementation: com.example.gscan.feature.editor.data.OfflineSignatureRepository,
    ): com.example.gscan.feature.editor.domain.SignatureRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        implementation: OfflineDocumentRepository,
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindScanRepository(
        implementation: OfflineScanRepository,
    ): ScanRepository

    @Binds
    @Singleton
    abstract fun bindPdfExportRepository(
        implementation: OfflinePdfExportRepository,
    ): PdfExportRepository

    @Binds
    @Singleton
    abstract fun bindOcrRepository(
        implementation: OfflineOcrRepository,
    ): OcrRepository
}
