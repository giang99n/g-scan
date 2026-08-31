package com.example.gscan.feature.scanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.scanner.domain.model.ScanSaveException
import com.example.gscan.feature.scanner.domain.model.ScanSaveFailure
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
                _uiState.value = ScannerUiState(errorMessage = error.failure.toMessage())
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

private fun ScanSaveFailure.toMessage(): String = when (this) {
    ScanSaveFailure.NO_PAGES -> "Không có trang nào để lưu."
    ScanSaveFailure.SOURCE_UNAVAILABLE -> "Không còn quyền đọc kết quả scan. Vui lòng scan lại."
    ScanSaveFailure.STORAGE_FULL -> "Thiết bị không đủ dung lượng để lưu tài liệu."
    ScanSaveFailure.INVALID_IMAGE -> "Một trang scan bị lỗi hoặc không đúng định dạng ảnh."
    ScanSaveFailure.DATABASE_ERROR ->
        "Không thể ghi tài liệu vào thư viện. Các file vừa tạo đã được dọn dẹp."
    ScanSaveFailure.CLEANUP_FAILED ->
        "Lưu tài liệu thất bại và chưa thể dọn hết file tạm. GScan sẽ thử dọn lại khi mở app."
    ScanSaveFailure.UNKNOWN -> "Không thể lưu tài liệu. Vui lòng thử lại."
}
