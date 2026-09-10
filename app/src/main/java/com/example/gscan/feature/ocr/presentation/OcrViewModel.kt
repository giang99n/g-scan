package com.example.gscan.feature.ocr.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentDetailsUseCase
import com.example.gscan.feature.documents.presentation.DOCUMENT_ID_ARGUMENT
import com.example.gscan.feature.ocr.domain.model.OcrJobState
import com.example.gscan.feature.ocr.domain.model.OcrJobStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageText
import com.example.gscan.feature.ocr.domain.model.ExportedOcrText
import com.example.gscan.feature.ocr.domain.model.OcrTextExportException
import com.example.gscan.feature.ocr.domain.model.OcrTextExportFailure
import com.example.gscan.feature.ocr.domain.model.OcrTextExportMode
import com.example.gscan.feature.ocr.domain.usecase.CancelOcrUseCase
import com.example.gscan.feature.ocr.domain.usecase.ObserveOcrJobUseCase
import com.example.gscan.feature.ocr.domain.usecase.ObserveOcrResultsUseCase
import com.example.gscan.feature.ocr.domain.usecase.StartOcrUseCase
import com.example.gscan.feature.ocr.domain.usecase.CreateOcrTextExportUseCase
import com.example.gscan.feature.ocr.domain.usecase.SaveOcrTextExportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class OcrUiState(
    val isLoading: Boolean = true,
    val detailsAvailable: Boolean = false,
    val title: String? = null,
    val pageCount: Int = 0,
    val unrecognizedPageCount: Int = 0,
    val results: List<OcrPageText> = emptyList(),
    val job: OcrJobState = OcrJobState(),
    val errorMessage: String? = null,
    val exportMode: OcrTextExportMode = OcrTextExportMode.SUCCESSFUL_ONLY,
    val isCreatingText: Boolean = false,
    val isSavingText: Boolean = false,
    val exportedText: ExportedOcrText? = null,
    val exportErrorMessage: String? = null,
)

sealed interface OcrEffect {
    data class ShowMessage(val message: String) : OcrEffect
}

@HiltViewModel
class OcrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocumentDetails: ObserveDocumentDetailsUseCase,
    observeOcrResults: ObserveOcrResultsUseCase,
    observeOcrJob: ObserveOcrJobUseCase,
    private val startOcr: StartOcrUseCase,
    private val cancelOcr: CancelOcrUseCase,
    private val createTextExport: CreateOcrTextExportUseCase,
    private val saveTextExport: SaveOcrTextExportUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[DOCUMENT_ID_ARGUMENT])
    private val _uiState = MutableStateFlow(OcrUiState())
    private val _effects = Channel<OcrEffect>(Channel.BUFFERED)

    val uiState: StateFlow<OcrUiState> = _uiState
    val effects = _effects.receiveAsFlow()

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
            }.collect { content ->
                _uiState.update { current ->
                    val ocrChanged = current.results != content.results ||
                        current.title != content.title ||
                        current.pageCount != content.pageCount ||
                        current.detailsAvailable != content.detailsAvailable ||
                        content.job.status == OcrJobStatus.QUEUED ||
                        content.job.status == OcrJobStatus.RUNNING
                    content.copy(
                        exportMode = current.exportMode,
                        isCreatingText = current.isCreatingText,
                        isSavingText = current.isSavingText,
                        exportedText = if (ocrChanged) null else current.exportedText,
                        exportErrorMessage = if (ocrChanged) null else current.exportErrorMessage,
                    )
                }
            }
        }
    }

    fun start() {
        val state = _uiState.value
        if (state.isCreatingText || state.isSavingText) return
        _uiState.update { it.copy(exportedText = null, exportErrorMessage = null) }
        startOcr(documentId)
    }

    fun cancel() = cancelOcr(documentId)

    fun selectExportMode(mode: OcrTextExportMode) {
        val state = _uiState.value
        if (state.isCreatingText || state.isSavingText || state.exportMode == mode) return
        _uiState.update {
            it.copy(exportMode = mode, exportedText = null, exportErrorMessage = null)
        }
    }

    fun createText() {
        val state = _uiState.value
        if (
            state.isCreatingText ||
            state.isSavingText ||
            state.job.status == OcrJobStatus.QUEUED ||
            state.job.status == OcrJobStatus.RUNNING
        ) return
        _uiState.update {
            it.copy(isCreatingText = true, exportedText = null, exportErrorMessage = null)
        }
        viewModelScope.launch {
            try {
                val result = createTextExport(documentId, state.exportMode)
                val current = _uiState.value
                val sourceStillCurrent = current.results == state.results &&
                    current.title == state.title &&
                    current.pageCount == state.pageCount &&
                    current.detailsAvailable == state.detailsAvailable &&
                    current.exportMode == state.exportMode &&
                    current.job.status != OcrJobStatus.QUEUED &&
                    current.job.status != OcrJobStatus.RUNNING
                if (sourceStillCurrent) {
                    _uiState.update { it.copy(exportedText = result) }
                    _effects.send(
                        OcrEffect.ShowMessage("Đã tạo TXT từ ${result.exportedPageCount} trang."),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: OcrTextExportException) {
                _uiState.update { it.copy(exportErrorMessage = error.toUserMessage()) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(exportErrorMessage = "Không thể tạo file TXT. Hãy thử lại.")
                }
            } finally {
                _uiState.update { it.copy(isCreatingText = false) }
            }
        }
    }

    fun saveTextTo(destinationUri: String) {
        val exported = _uiState.value.exportedText ?: return
        if (_uiState.value.isCreatingText || _uiState.value.isSavingText) return
        _uiState.update { it.copy(isSavingText = true, exportErrorMessage = null) }
        viewModelScope.launch {
            try {
                saveTextExport(exported, destinationUri)
                _effects.send(OcrEffect.ShowMessage("Đã lưu file TXT."))
            } catch (error: CancellationException) {
                throw error
            } catch (error: OcrTextExportException) {
                _uiState.update { it.copy(exportErrorMessage = error.toUserMessage()) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(exportErrorMessage = "Không thể lưu TXT vào vị trí đã chọn.")
                }
            } finally {
                _uiState.update { it.copy(isSavingText = false) }
            }
        }
    }
}

private fun OcrTextExportException.toUserMessage(): String = when (reason) {
    OcrTextExportFailure.DOCUMENT_NOT_FOUND -> "Tài liệu không còn tồn tại."
    OcrTextExportFailure.NO_TEXT -> "Chưa có nội dung OCR phù hợp để xuất."
    OcrTextExportFailure.STORAGE_FULL -> "Thiết bị không còn đủ dung lượng để tạo TXT."
    OcrTextExportFailure.WRITE_FAILED -> "Không thể ghi file TXT. Hãy kiểm tra dung lượng và thử lại."
    OcrTextExportFailure.SOURCE_UNAVAILABLE -> "File TXT tạm không còn khả dụng. Hãy tạo lại."
    OcrTextExportFailure.UNKNOWN -> "Không thể tạo file TXT. Hãy thử lại."
}
