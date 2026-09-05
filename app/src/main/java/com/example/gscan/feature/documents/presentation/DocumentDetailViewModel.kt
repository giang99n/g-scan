package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.DocumentEditException
import com.example.gscan.feature.documents.domain.model.DocumentEditFailure
import com.example.gscan.feature.documents.domain.model.PageEditException
import com.example.gscan.feature.documents.domain.model.PageEditFailure
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.usecase.ManageDocumentPagesUseCase
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentDetailsUseCase
import com.example.gscan.feature.documents.domain.usecase.RenameDocumentUseCase
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

data class DocumentDetailUiState(
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val isAddingPages: Boolean = false,
    val details: ScannedDocumentDetails? = null,
    val errorMessage: String? = null,
)

sealed interface DocumentDetailEffect {
    data class ShowMessage(val message: String) : DocumentDetailEffect
    data class PageMoved(
        val pageId: String,
        val targetPosition: Int,
    ) : DocumentDetailEffect
}

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocumentDetails: ObserveDocumentDetailsUseCase,
    private val managePages: ManageDocumentPagesUseCase,
    private val renameDocument: RenameDocumentUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[DOCUMENT_ID_ARGUMENT])
    private val _uiState = MutableStateFlow(DocumentDetailUiState())
    private val _effects = Channel<DocumentDetailEffect>(Channel.BUFFERED)
    private var mutationJob: Job? = null

    val uiState: StateFlow<DocumentDetailUiState> = _uiState
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeDocumentDetails(documentId)
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = "Không thể mở tài liệu.",
                        )
                    }
                }
                .collect { details ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            details = details,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    fun rotateClockwise(pageId: String) = mutate {
        managePages.rotateClockwise(documentId, pageId)
    }

    fun movePage(pageId: String, targetPosition: Int) = mutate {
        managePages.move(documentId, pageId, targetPosition)
        _effects.send(DocumentDetailEffect.PageMoved(pageId, targetPosition))
    }

    fun deletePage(pageId: String) = mutate {
        managePages.delete(documentId, pageId)
        _effects.send(DocumentDetailEffect.ShowMessage("Đã xóa trang."))
    }

    fun addPages(sourceUris: List<String>) = mutate(isAddingPages = true) {
        managePages.add(documentId, sourceUris)
        _effects.send(
            DocumentDetailEffect.ShowMessage(
                if (sourceUris.size == 1) "Đã thêm 1 trang." else "Đã thêm ${sourceUris.size} trang.",
            ),
        )
    }

    fun cancelAddingPages() {
        if (_uiState.value.isAddingPages) mutationJob?.cancel()
    }

    fun onAddPagesPickerFailure() {
        _effects.trySend(DocumentDetailEffect.ShowMessage("Không thể mở thư viện ảnh."))
    }

    fun rename(title: String) = mutate {
        renameDocument(documentId, title)
        _effects.send(DocumentDetailEffect.ShowMessage("Đã đổi tên tài liệu."))
    }

    private fun mutate(
        isAddingPages: Boolean = false,
        block: suspend () -> Unit,
    ) {
        if (mutationJob?.isActive == true) return
        mutationJob = viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, isAddingPages = isAddingPages) }
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: PageEditException) {
                _effects.send(DocumentDetailEffect.ShowMessage(error.toUserMessage()))
            } catch (error: DocumentEditException) {
                _effects.send(DocumentDetailEffect.ShowMessage(error.toUserMessage()))
            } catch (_: Exception) {
                _effects.send(DocumentDetailEffect.ShowMessage("Không thể cập nhật trang."))
            } finally {
                _uiState.update { it.copy(isMutating = false, isAddingPages = false) }
            }
        }
    }
}

private fun DocumentEditException.toUserMessage(): String = when (reason) {
    DocumentEditFailure.DOCUMENT_NOT_FOUND -> "Tài liệu không còn tồn tại."
    DocumentEditFailure.EMPTY_TITLE -> "Tên tài liệu không được để trống."
    DocumentEditFailure.TITLE_TOO_LONG -> "Tên tài liệu không được vượt quá 120 ký tự."
    DocumentEditFailure.UNKNOWN -> "Không thể đổi tên tài liệu."
}

private fun PageEditException.toUserMessage(): String = when (reason) {
    PageEditFailure.DOCUMENT_NOT_FOUND -> "Tài liệu không còn tồn tại."
    PageEditFailure.PAGE_NOT_FOUND -> "Trang không còn tồn tại."
    PageEditFailure.LAST_PAGE -> "Không thể xóa trang duy nhất của tài liệu."
    PageEditFailure.INVALID_POSITION -> "Vị trí trang không hợp lệ."
    PageEditFailure.NO_PAGES -> "Bạn chưa chọn ảnh nào."
    PageEditFailure.TOO_MANY_PAGES -> "Mỗi tài liệu chỉ hỗ trợ tối đa 100 trang."
    PageEditFailure.SOURCE_UNAVAILABLE -> "Không thể đọc một hoặc nhiều ảnh đã chọn."
    PageEditFailure.STORAGE_FULL -> "Thiết bị không còn đủ dung lượng trống."
    PageEditFailure.INVALID_IMAGE -> "Một hoặc nhiều file đã chọn không phải ảnh hợp lệ."
    PageEditFailure.STORAGE -> "Không thể cập nhật file trang."
    PageEditFailure.UNKNOWN -> "Không thể cập nhật trang."
}

const val DOCUMENT_ID_ARGUMENT = "documentId"
