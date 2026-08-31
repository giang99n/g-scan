package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DocumentDetailUiState(
    val isLoading: Boolean = true,
    val details: ScannedDocumentDetails? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocumentDetails: ObserveDocumentDetailsUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[DOCUMENT_ID_ARGUMENT])

    val uiState: StateFlow<DocumentDetailUiState> = observeDocumentDetails(documentId)
        .map { details -> DocumentDetailUiState(isLoading = false, details = details) }
        .catch {
            emit(
                DocumentDetailUiState(
                    isLoading = false,
                    errorMessage = "Không thể mở tài liệu.",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DocumentDetailUiState(),
        )
}

const val DOCUMENT_ID_ARGUMENT = "documentId"
