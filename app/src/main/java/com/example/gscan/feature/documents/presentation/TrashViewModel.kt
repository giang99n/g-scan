package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.usecase.ManageTrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrashUiState(
    val isLoading: Boolean = true,
    val documents: List<ScannedDocument> = emptyList(),
    val processingDocumentId: String? = null,
    val isEmptying: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface TrashEffect {
    data class ShowMessage(val message: String) : TrashEffect
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val manageTrash: ManageTrashUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrashUiState())
    private val _effects = Channel<TrashEffect>(Channel.BUFFERED)

    val uiState: StateFlow<TrashUiState> = _uiState
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            manageTrash.observe()
                .catch {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Không thể tải thùng rác.")
                    }
                }
                .collect { documents ->
                    _uiState.update {
                        it.copy(isLoading = false, documents = documents, errorMessage = null)
                    }
                }
        }
    }

    fun restore(documentId: String) = runDocumentAction(
        documentId = documentId,
        successMessage = "Đã khôi phục tài liệu.",
        errorMessage = "Không thể khôi phục tài liệu. Hãy thử lại.",
    ) { manageTrash.restore(documentId) }

    fun permanentlyDelete(documentId: String) = runDocumentAction(
        documentId = documentId,
        successMessage = "Đã xóa vĩnh viễn tài liệu.",
        errorMessage = "Không thể xóa vĩnh viễn tài liệu. Hãy thử lại.",
    ) { manageTrash.permanentlyDelete(documentId) }

    fun emptyTrash() {
        val state = _uiState.value
        if (state.isEmptying || state.processingDocumentId != null || state.documents.isEmpty()) return
        _uiState.update { it.copy(isEmptying = true) }
        viewModelScope.launch {
            try {
                val count = manageTrash.empty()
                _effects.send(TrashEffect.ShowMessage("Đã xóa vĩnh viễn $count tài liệu."))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _effects.send(TrashEffect.ShowMessage("Không thể dọn thùng rác. Hãy thử lại."))
            } finally {
                _uiState.update { it.copy(isEmptying = false) }
            }
        }
    }

    private fun runDocumentAction(
        documentId: String,
        successMessage: String,
        errorMessage: String,
        action: suspend () -> Unit,
    ) {
        val state = _uiState.value
        if (state.isEmptying || state.processingDocumentId != null ||
            state.documents.none { it.id == documentId }
        ) return
        _uiState.update { it.copy(processingDocumentId = documentId) }
        viewModelScope.launch {
            try {
                action()
                _effects.send(TrashEffect.ShowMessage(successMessage))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _effects.send(TrashEffect.ShowMessage(errorMessage))
            } finally {
                _uiState.update { it.copy(processingDocumentId = null) }
            }
        }
    }
}
