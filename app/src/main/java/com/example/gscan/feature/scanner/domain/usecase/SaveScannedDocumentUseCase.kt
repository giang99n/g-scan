package com.example.gscan.feature.scanner.domain.usecase

import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.model.ScanSaveFailure
import com.example.gscan.feature.scanner.domain.repository.ScanRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SaveScannedDocumentUseCase @Inject constructor(
    private val repository: ScanRepository,
) {
    suspend operator fun invoke(sourceUris: List<String>): String {
        if (sourceUris.isEmpty()) {
            throw ScanSaveException(ScanSaveFailure.NO_PAGES)
        }
        val title = SimpleDateFormat("'Tài liệu' dd-MM-yyyy HH:mm", Locale.getDefault())
            .format(Date())
        return repository.saveScan(title, sourceUris)
    }
}
