package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.PageEditException
import com.example.gscan.feature.documents.domain.model.PageEditFailure
import com.example.gscan.feature.documents.domain.usecase.DeleteDocumentUseCase
import com.example.gscan.feature.documents.domain.usecase.DuplicateDocumentUseCase
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<ScannedDocument> = emptyList(),
    val searchQuery: String = "",
    val deletingDocumentId: String? = null,
    val duplicatingDocumentId: String? = null,
    val errorMessage: String? = null,
)

sealed interface DocumentsEffect {
    data class ShowMessage(val message: String) : DocumentsEffect
}

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val observeDocuments: ObserveDocumentsUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val duplicateDocument: DuplicateDocumentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DocumentsUiState())
    private val _effects = Channel<DocumentsEffect>(Channel.BUFFERED)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<DocumentsUiState> = _uiState
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            searchQuery.collectLatest { query ->
                observeDocuments(query)
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
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun delete(documentId: String) {
        val state = _uiState.value
        if (state.deletingDocumentId != null || state.duplicatingDocumentId != null ||
            state.documents.none { it.id == documentId }
        ) return

        _uiState.update { it.copy(deletingDocumentId = documentId) }
        viewModelScope.launch {
            try {
                deleteDocument(documentId)
                _effects.send(DocumentsEffect.ShowMessage("Đã chuyển tài liệu vào thùng rác."))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _effects.send(DocumentsEffect.ShowMessage("Không thể chuyển tài liệu vào thùng rác. Hãy thử lại."))
            } finally {
                _uiState.update { it.copy(deletingDocumentId = null) }
            }
        }
    }

    fun duplicate(documentId: String) {
        val state = _uiState.value
        if (state.deletingDocumentId != null || state.duplicatingDocumentId != null ||
            state.documents.none { it.id == documentId && it.pageCount > 0 }
        ) return

        _uiState.update { it.copy(duplicatingDocumentId = documentId) }
        viewModelScope.launch {
            try {
                duplicateDocument(documentId)
                _effects.send(DocumentsEffect.ShowMessage("Đã nhân bản tài liệu."))
            } catch (error: CancellationException) {
                throw error
            } catch (error: PageEditException) {
                _effects.send(DocumentsEffect.ShowMessage(error.toDuplicateDocumentMessage()))
            } catch (_: Exception) {
                _effects.send(DocumentsEffect.ShowMessage("Không thể nhân bản tài liệu. Hãy thử lại."))
            } finally {
                _uiState.update { it.copy(duplicatingDocumentId = null) }
            }
        }
    }
}

private fun PageEditException.toDuplicateDocumentMessage(): String = when (reason) {
    PageEditFailure.DOCUMENT_NOT_FOUND -> "Tài liệu không còn tồn tại."
    PageEditFailure.EMPTY_DOCUMENT -> "Không thể nhân bản tài liệu chưa có trang."
    PageEditFailure.SOURCE_UNAVAILABLE -> "Không thể đọc file của tài liệu."
    PageEditFailure.STORAGE_FULL -> "Thiết bị không còn đủ dung lượng trống."
    PageEditFailure.INVALID_IMAGE -> "Tài liệu chứa file ảnh không hợp lệ."
    else -> "Không thể nhân bản tài liệu. Hãy thử lại."
}
