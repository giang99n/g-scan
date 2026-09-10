package com.example.gscan.feature.export.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentDetailsUseCase
import com.example.gscan.feature.documents.presentation.DOCUMENT_ID_ARGUMENT
import com.example.gscan.feature.export.domain.model.ExportedPdf
import com.example.gscan.feature.export.domain.model.PdfExportException
import com.example.gscan.feature.export.domain.model.PdfExportFailure
import com.example.gscan.feature.export.domain.model.PdfQualityPreset
import com.example.gscan.feature.export.domain.usecase.CreatePdfUseCase
import com.example.gscan.feature.export.domain.usecase.SavePdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfToolsUiState(
    val isLoading: Boolean = true,
    val details: ScannedDocumentDetails? = null,
    val preset: PdfQualityPreset = PdfQualityPreset.BALANCED,
    val isExporting: Boolean = false,
    val completedPages: Int = 0,
    val totalPages: Int = 0,
    val exportedPdf: ExportedPdf? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface PdfToolsEffect {
    data class ShowMessage(val message: String) : PdfToolsEffect
}

@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocumentDetails: ObserveDocumentDetailsUseCase,
    private val createPdf: CreatePdfUseCase,
    private val savePdf: SavePdfUseCase,
) : ViewModel() {
    private val documentId: String? = savedStateHandle[DOCUMENT_ID_ARGUMENT]
    private val _uiState = MutableStateFlow(PdfToolsUiState(isLoading = documentId != null))
    private val _effects = Channel<PdfToolsEffect>(Channel.BUFFERED)
    private var exportJob: Job? = null

    val uiState: StateFlow<PdfToolsUiState> = _uiState
    val effects = _effects.receiveAsFlow()

    init {
        documentId?.let { id ->
            viewModelScope.launch {
                observeDocumentDetails(id)
                    .catch {
                        _uiState.update { state ->
                            state.copy(isLoading = false, errorMessage = "Không thể mở tài liệu.")
                        }
                    }
                    .collect { details ->
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                details = details,
                                errorMessage = if (details == null) "Tài liệu không còn tồn tại." else null,
                            )
                        }
                    }
            }
        }
    }

    fun selectPreset(preset: PdfQualityPreset) {
        if (_uiState.value.isExporting || _uiState.value.isSaving) return
        _uiState.update { it.copy(preset = preset, exportedPdf = null, errorMessage = null) }
    }

    fun export() {
        val id = documentId ?: return
        val state = _uiState.value
        val details = state.details ?: return
        if (state.isExporting || state.isSaving) return

        _uiState.update {
            it.copy(
                isExporting = true,
                completedPages = 0,
                totalPages = details.pages.size,
                exportedPdf = null,
                errorMessage = null,
            )
        }
        exportJob = viewModelScope.launch {
            try {
                val result = createPdf(id, state.preset) { completed, total ->
                    _uiState.update { it.copy(completedPages = completed, totalPages = total) }
                }
                _uiState.update { it.copy(isExporting = false, exportedPdf = result) }
                _effects.send(PdfToolsEffect.ShowMessage("Đã tạo PDF ${result.pageCount} trang."))
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isExporting = false, completedPages = 0) }
                _effects.trySend(PdfToolsEffect.ShowMessage("Đã hủy xuất PDF."))
            } catch (error: PdfExportException) {
                _uiState.update { it.copy(isExporting = false, errorMessage = error.toUserMessage()) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, errorMessage = "Không thể tạo PDF. Hãy thử lại.")
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    fun saveTo(destinationUri: String) {
        val exported = _uiState.value.exportedPdf ?: return
        if (_uiState.value.isSaving || _uiState.value.isExporting) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                savePdf(exported, destinationUri)
                _effects.send(PdfToolsEffect.ShowMessage("Đã lưu PDF."))
            } catch (error: CancellationException) {
                throw error
            } catch (error: PdfExportException) {
                _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Không thể lưu PDF vào vị trí đã chọn.") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}

private fun PdfExportException.toUserMessage(): String = when (reason) {
    PdfExportFailure.DOCUMENT_NOT_FOUND -> "Tài liệu không còn tồn tại."
    PdfExportFailure.NO_PAGES -> "Tài liệu chưa có trang để xuất."
    PdfExportFailure.SOURCE_UNAVAILABLE -> "Không thể đọc một hoặc nhiều trang của tài liệu."
    PdfExportFailure.STORAGE_FULL -> "Thiết bị không còn đủ dung lượng để tạo PDF."
    PdfExportFailure.INSUFFICIENT_MEMORY -> "Thiết bị không đủ bộ nhớ để xử lý trang ảnh này. Hãy chọn chất lượng thấp hơn."
    PdfExportFailure.WRITE_FAILED -> "Không thể ghi file PDF. Hãy kiểm tra dung lượng và thử lại."
    PdfExportFailure.UNKNOWN -> "Không thể tạo PDF. Hãy thử lại."
}
