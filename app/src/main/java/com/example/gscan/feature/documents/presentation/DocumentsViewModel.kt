package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.usecase.DeleteDocumentUseCase
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentsUseCase
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

data class DocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<ScannedDocument> = emptyList(),
    val deletingDocumentId: String? = null,
    val errorMessage: String? = null,
)

sealed interface DocumentsEffect {
    data class ShowMessage(val message: String) : DocumentsEffect
}

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    observeDocuments: ObserveDocumentsUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DocumentsUiState())
    private val _effects = Channel<DocumentsEffect>(Channel.BUFFERED)

    val uiState: StateFlow<DocumentsUiState> = _uiState
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeDocuments()
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = "Không thể tải thư viện tài liệu.",
                        )
                    }
                }
                .collect { documents ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            documents = documents,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    fun delete(documentId: String) {
        val state = _uiState.value
        if (state.deletingDocumentId != null || state.documents.none { it.id == documentId }) return

        _uiState.update { it.copy(deletingDocumentId = documentId) }
        viewModelScope.launch {
            try {
                deleteDocument(documentId)
                _effects.send(DocumentsEffect.ShowMessage("Đã xóa tài liệu."))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _effects.send(DocumentsEffect.ShowMessage("Không thể xóa tài liệu. Hãy thử lại."))
            } finally {
                _uiState.update { it.copy(deletingDocumentId = null) }
            }
        }
    }
}
