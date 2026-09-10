package com.example.gscan.feature.scanner.domain.usecase

import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.model.ScanSaveFailure
import com.example.gscan.feature.scanner.domain.repository.ScanRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ImportPdfUseCase @Inject constructor(
    private val repository: ScanRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit,
    ): String {
        if (sourceUri.isBlank()) {
            throw ScanSaveException(ScanSaveFailure.SOURCE_UNAVAILABLE)
        }
        val title = SimpleDateFormat("'PDF nhập' dd-MM-yyyy HH:mm", Locale.getDefault())
            .format(Date())
        return repository.savePdfDocument(title, sourceUri, onProgress)
    }
}
