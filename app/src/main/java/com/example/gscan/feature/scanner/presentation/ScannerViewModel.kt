package com.example.gscan.feature.scanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.usecase.SaveScannedDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isPreparingScanner: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface ScannerEffect {
    data class DocumentSaved(val documentId: String) : ScannerEffect
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val saveScannedDocument: SaveScannedDocumentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ScannerEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun saveScan(sourceUris: List<String>) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.value = ScannerUiState(isSaving = true)
            try {
                val documentId = saveScannedDocument(sourceUris)
                _uiState.value = ScannerUiState()
                _effects.send(ScannerEffect.DocumentSaved(documentId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: ScanSaveException) {
                _uiState.value = ScannerUiState(
                    errorMessage = error.failure.toUserMessage(SaveInputKind.SCAN),
                )
            } catch (_: Exception) {
                _uiState.value = ScannerUiState(
                    errorMessage = "Không thể lưu tài liệu. Các file tạm đã được dọn dẹp, hãy thử lại.",
                )
            }
        }
    }

    fun beginScannerPreparation(): Boolean {
        if (_uiState.value.isSaving || _uiState.value.isPreparingScanner) return false
        _uiState.update { it.copy(isPreparingScanner = true, errorMessage = null) }
        return true
    }

    fun finishScannerPreparation() {
        _uiState.update { it.copy(isPreparingScanner = false) }
    }

    fun onScannerFailure(message: String) {
        _uiState.update { it.copy(isPreparingScanner = false, errorMessage = message) }
    }

}
