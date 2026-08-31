package com.example.gscan.feature.scanner.domain.usecase

import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.model.ScanSaveFailure
import com.example.gscan.feature.scanner.domain.repository.ScanRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SaveImportedImagesUseCase @Inject constructor(
    private val repository: ScanRepository,
) {
    suspend operator fun invoke(sourceUris: List<String>): String {
        if (sourceUris.isEmpty()) {
            throw ScanSaveException(ScanSaveFailure.NO_PAGES)
        }
        if (sourceUris.size > MAX_DOCUMENT_PAGES) {
            throw ScanSaveException(ScanSaveFailure.TOO_MANY_PAGES)
        }

        val title = SimpleDateFormat("'Ảnh nhập' dd-MM-yyyy HH:mm", Locale.getDefault())
            .format(Date())
        return repository.saveDocument(title, sourceUris)
    }
}
