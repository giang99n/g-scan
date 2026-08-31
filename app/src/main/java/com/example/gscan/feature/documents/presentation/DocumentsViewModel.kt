package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<ScannedDocument> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    observeDocuments: ObserveDocumentsUseCase,
) : ViewModel() {
    val uiState: StateFlow<DocumentsUiState> = observeDocuments()
        .map { DocumentsUiState(isLoading = false, documents = it) }
        .catch {
            emit(
                DocumentsUiState(
                    isLoading = false,
                    errorMessage = "Không thể tải thư viện tài liệu.",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DocumentsUiState(),
        )
}
