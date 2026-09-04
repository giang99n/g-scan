package com.example.gscan.feature.scanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.usecase.ImportPdfUseCase
import com.example.gscan.feature.scanner.domain.usecase.SaveImportedImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val isSaving: Boolean = false,
    val inputKind: SaveInputKind = SaveInputKind.IMPORT,
    val completedPages: Int = 0,
    val totalPages: Int = 0,
    val errorMessage: String? = null,
)

sealed interface ImportEffect {
    data class DocumentSaved(val documentId: String) : ImportEffect
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val saveImportedImages: SaveImportedImagesUseCase,
    private val importPdfUseCase: ImportPdfUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ImportEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
    private var importJob: Job? = null

    fun importImages(sourceUris: List<String>) {
        if (_uiState.value.isSaving || sourceUris.isEmpty()) return

        startImport(SaveInputKind.IMPORT) { saveImportedImages(sourceUris) }
    }

    fun importPdf(sourceUri: String) {
        if (_uiState.value.isSaving || sourceUri.isBlank()) return

        startImport(SaveInputKind.PDF) {
            importPdfUseCase(sourceUri) { completedPages, totalPages ->
                _uiState.update { state ->
                    state.copy(
                        completedPages = completedPages,
                        totalPages = totalPages,
                    )
                }
            }
        }
    }

    private fun startImport(
        inputKind: SaveInputKind,
        operation: suspend () -> String,
    ) {
        importJob = viewModelScope.launch {
            _uiState.value = ImportUiState(isSaving = true, inputKind = inputKind)
            var committedDocumentId: String? = null
            try {
                val documentId = operation()
                committedDocumentId = documentId
                _uiState.value = ImportUiState()
                _effects.send(ImportEffect.DocumentSaved(documentId))
            } catch (error: CancellationException) {
                _uiState.value = ImportUiState()
                val documentId = committedDocumentId
                if (documentId != null) {
                    _effects.trySend(ImportEffect.DocumentSaved(documentId))
                } else {
                    throw error
                }
            } catch (error: ScanSaveException) {
                _uiState.value = ImportUiState(
                    inputKind = inputKind,
                    errorMessage = error.failure.toUserMessage(inputKind),
                )
            } catch (_: Exception) {
                _uiState.value = ImportUiState(
                    inputKind = inputKind,
                    errorMessage = "Không thể nhập tài liệu. Các file tạm đã được dọn dẹp, hãy thử lại.",
                )
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onPickerFailure(inputKind: SaveInputKind) {
        _uiState.update {
            it.copy(
                inputKind = inputKind,
                errorMessage = if (inputKind == SaveInputKind.PDF) {
                    "Không thể mở trình chọn PDF. Vui lòng thử lại."
                } else {
                    "Không thể mở thư viện ảnh. Vui lòng thử lại."
                },
            )
        }
    }
}
