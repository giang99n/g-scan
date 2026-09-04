package com.example.gscan.feature.ocr.domain.usecase

import com.example.gscan.feature.ocr.domain.repository.OcrRepository
import javax.inject.Inject

class ObserveOcrResultsUseCase @Inject constructor(
    private val repository: OcrRepository,
) {
    operator fun invoke(documentId: String) = repository.observeResults(documentId)
}

class ObserveOcrJobUseCase @Inject constructor(
    private val repository: OcrRepository,
) {
    operator fun invoke(documentId: String) = repository.observeJob(documentId)
}

class StartOcrUseCase @Inject constructor(
    private val repository: OcrRepository,
) {
    operator fun invoke(documentId: String) = repository.enqueue(documentId)
}

class CancelOcrUseCase @Inject constructor(
    private val repository: OcrRepository,
) {
    operator fun invoke(documentId: String) = repository.cancel(documentId)
}
