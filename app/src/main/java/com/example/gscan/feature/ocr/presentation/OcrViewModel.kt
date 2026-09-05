package com.example.gscan.feature.ocr.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentDetailsUseCase
import com.example.gscan.feature.documents.presentation.DOCUMENT_ID_ARGUMENT
import com.example.gscan.feature.ocr.domain.model.OcrJobState
import com.example.gscan.feature.ocr.domain.model.OcrJobStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageText
import com.example.gscan.feature.ocr.domain.usecase.CancelOcrUseCase
import com.example.gscan.feature.ocr.domain.usecase.ObserveOcrJobUseCase
import com.example.gscan.feature.ocr.domain.usecase.ObserveOcrResultsUseCase
import com.example.gscan.feature.ocr.domain.usecase.StartOcrUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OcrUiState(
    val isLoading: Boolean = true,
    val detailsAvailable: Boolean = false,
    val title: String? = null,
    val pageCount: Int = 0,
    val unrecognizedPageCount: Int = 0,
    val results: List<OcrPageText> = emptyList(),
    val job: OcrJobState = OcrJobState(),
    val errorMessage: String? = null,
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocumentDetails: ObserveDocumentDetailsUseCase,
    observeOcrResults: ObserveOcrResultsUseCase,
    observeOcrJob: ObserveOcrJobUseCase,
    private val startOcr: StartOcrUseCase,
    private val cancelOcr: CancelOcrUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[DOCUMENT_ID_ARGUMENT])
    private val _uiState = MutableStateFlow(OcrUiState())

    val uiState: StateFlow<OcrUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                observeDocumentDetails(documentId),
                observeOcrResults(documentId),
                observeOcrJob(documentId),
            ) { details, results, job ->
                val pageIds = details?.pages?.mapTo(mutableSetOf()) { it.id }.orEmpty()
                val resultPageIds = results.mapTo(mutableSetOf()) { it.pageId }
                val unrecognizedPageCount = (pageIds - resultPageIds).size
                val effectiveJob = if (
                    job.status == OcrJobStatus.SUCCEEDED && unrecognizedPageCount > 0
                ) {
                    OcrJobState()
                } else {
                    job
                }
                OcrUiState(
                    isLoading = false,
                    detailsAvailable = details != null,
                    title = details?.document?.title,
                    pageCount = details?.pages?.size ?: 0,
                    unrecognizedPageCount = unrecognizedPageCount,
                    results = results,
                    job = effectiveJob,
                )
            }.catch {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Không thể tải kết quả OCR.")
                }
            }.collect { state -> _uiState.value = state }
        }
    }

    fun start() = startOcr(documentId)

    fun cancel() = cancelOcr(documentId)
}
