package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.usecase.CreateDraftDocumentUseCase
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<ScannedDocument> = emptyList(),
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    observeDocuments: ObserveDocumentsUseCase,
    private val createDraftDocument: CreateDraftDocumentUseCase,
) : ViewModel() {
    val uiState: StateFlow<DocumentsUiState> = observeDocuments()
        .map { DocumentsUiState(isLoading = false, documents = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DocumentsUiState(),
        )

    fun createDemoDocument() {
        viewModelScope.launch {
            createDraftDocument("Hóa đơn ${uiState.value.documents.size + 1}")
        }
    }
}
