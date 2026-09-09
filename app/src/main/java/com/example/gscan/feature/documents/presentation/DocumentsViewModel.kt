package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.DocumentFolder
import com.example.gscan.feature.documents.domain.model.DocumentTag
import com.example.gscan.feature.documents.domain.model.PageEditException
import com.example.gscan.feature.documents.domain.model.PageEditFailure
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.usecase.BatchTrashDocumentsUseCase
import com.example.gscan.feature.documents.domain.usecase.DeleteDocumentUseCase
import com.example.gscan.feature.documents.domain.usecase.DuplicateDocumentUseCase
import com.example.gscan.feature.documents.domain.usecase.ManageFoldersUseCase
import com.example.gscan.feature.documents.domain.usecase.ManageTagsUseCase
import com.example.gscan.feature.documents.domain.usecase.MoveDocumentsToFolderUseCase
import com.example.gscan.feature.documents.domain.usecase.ObserveDocumentsUseCase
import com.example.gscan.feature.documents.domain.usecase.ObserveFoldersUseCase
import com.example.gscan.feature.documents.domain.usecase.ObserveTagsUseCase
import com.example.gscan.feature.documents.domain.usecase.SetDocumentTagsUseCase
import com.example.gscan.feature.documents.domain.usecase.SetFavoriteUseCase
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
    val folders: List<DocumentFolder> = emptyList(),
    val tags: List<DocumentTag> = emptyList(),
    val selectedFolderId: String? = null,
    val selectedTagId: String? = null,
    val favoriteOnly: Boolean = false,
    val selectedDocumentIds: Set<String> = emptySet(),
    val isOrganizing: Boolean = false,
    val organizationError: String? = null,
    val organizationSuccessVersion: Int = 0,
    val folderLoadFailed: Boolean = false,
    val tagLoadFailed: Boolean = false,
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
    observeFolders: ObserveFoldersUseCase,
    observeTags: ObserveTagsUseCase,
    private val setFavorite: SetFavoriteUseCase,
    private val moveDocuments: MoveDocumentsToFolderUseCase,
    private val setDocumentTags: SetDocumentTagsUseCase,
    private val batchTrashDocuments: BatchTrashDocumentsUseCase,
    private val manageFolders: ManageFoldersUseCase,
    private val manageTags: ManageTagsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DocumentsUiState())
    private val _effects = Channel<DocumentsEffect>(Channel.BUFFERED)
    private val searchQuery = MutableStateFlow("")
    private var sourceDocuments: List<ScannedDocument> = emptyList()

    val uiState: StateFlow<DocumentsUiState> = _uiState
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            searchQuery.collectLatest { query ->
                observeDocuments(query)
                    .catch {
                        _uiState.update { state -> state.copy(isLoading = false, errorMessage = "Không thể tải thư viện tài liệu.") }
                    }
                    .collect { documents ->
                        sourceDocuments = documents
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                documents = filterDocuments(documents, state),
                                selectedDocumentIds = state.selectedDocumentIds.intersect(documents.map { it.id }.toSet()),
                                errorMessage = null,
                            )
                        }
                    }
            }
        }
        viewModelScope.launch {
            observeFolders().catch {
                _uiState.update { state -> state.copy(folderLoadFailed = true) }
            }.collect { folders ->
                _uiState.update { state ->
                    val next = state.copy(
                        folders = folders,
                        folderLoadFailed = false,
                        selectedFolderId = state.selectedFolderId?.takeIf { id ->
                            id == UNFILED_FOLDER_FILTER_ID || folders.any { it.id == id }
                        },
                    )
                    next.copy(documents = filterDocuments(sourceDocuments, next))
                }
            }
        }
        viewModelScope.launch {
            observeTags().catch {
                _uiState.update { state -> state.copy(tagLoadFailed = true) }
            }.collect { tags ->
                _uiState.update { state ->
                    val next = state.copy(
                        tags = tags,
                        tagLoadFailed = false,
                        selectedTagId = state.selectedTagId?.takeIf { id -> tags.any { it.id == id } },
                    )
                    next.copy(documents = filterDocuments(sourceDocuments, next))
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query, selectedDocumentIds = emptySet()) }
    }

    fun selectFolder(folderId: String?) = updateFilter { copy(selectedFolderId = folderId) }
    fun selectTag(tagId: String?) = updateFilter { copy(selectedTagId = tagId) }
    fun toggleFavoriteFilter() = updateFilter { copy(favoriteOnly = !favoriteOnly) }

    fun clearFilters() {
        searchQuery.value = ""
        _uiState.update { state ->
            val next = state.copy(
                searchQuery = "",
                selectedFolderId = null,
                selectedTagId = null,
                favoriteOnly = false,
                selectedDocumentIds = emptySet(),
            )
            next.copy(documents = filterDocuments(sourceDocuments, next))
        }
    }

    fun clearOrganizationError() = _uiState.update { it.copy(organizationError = null) }

    fun toggleSelection(documentId: String) {
        if (_uiState.value.isOrganizing) return
        _uiState.update { state ->
            val selected = state.selectedDocumentIds.toMutableSet().apply { if (!add(documentId)) remove(documentId) }
            state.copy(selectedDocumentIds = selected)
        }
    }

    fun selectAllVisible() = _uiState.update { it.copy(selectedDocumentIds = it.documents.map(ScannedDocument::id).toSet()) }
    fun clearSelection() = _uiState.update { it.copy(selectedDocumentIds = emptySet()) }

    fun toggleFavorite(documentId: String, favorite: Boolean) = organize("Đã cập nhật yêu thích.") {
        setFavorite(setOf(documentId), favorite)
    }

    fun favoriteSelected(favorite: Boolean) = organize("Đã cập nhật tài liệu đã chọn.", clearSelection = true) {
        setFavorite(requireSelection(), favorite)
    }

    fun moveSelected(folderId: String?) = organize("Đã di chuyển tài liệu.", clearSelection = true) {
        moveDocuments(requireSelection(), folderId)
    }

    fun updateTagsForSelected(addedTagIds: Set<String>, removedTagIds: Set<String>) =
        organize("Đã cập nhật tag.", clearSelection = true) {
            setDocumentTags(requireSelection(), addedTagIds, removedTagIds)
        }

    fun trashSelected() = organize("Đã chuyển tài liệu vào thùng rác.", clearSelection = true) {
        batchTrashDocuments(requireSelection())
    }

    fun createFolder(name: String) = organize("Đã tạo folder.") { manageFolders.create(name) }
    fun renameFolder(id: String, name: String) = organize("Đã đổi tên folder.") { manageFolders.rename(id, name) }
    fun deleteFolder(id: String) = organize("Đã xóa folder; tài liệu được chuyển về Chưa phân loại.") { manageFolders.delete(id) }
    fun createTag(name: String) = organize("Đã tạo tag.") { manageTags.create(name) }
    fun renameTag(id: String, name: String) = organize("Đã đổi tên tag.") { manageTags.rename(id, name) }
    fun deleteTag(id: String) = organize("Đã xóa tag khỏi các tài liệu.") { manageTags.delete(id) }

    fun delete(documentId: String) {
        val state = _uiState.value
        if (state.deletingDocumentId != null || state.duplicatingDocumentId != null || state.isOrganizing || state.documents.none { it.id == documentId }) return
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
        if (state.deletingDocumentId != null || state.duplicatingDocumentId != null || state.isOrganizing || state.documents.none { it.id == documentId && it.pageCount > 0 }) return
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

    private fun updateFilter(transform: DocumentsUiState.() -> DocumentsUiState) {
        _uiState.update { state ->
            val next = state.transform().copy(selectedDocumentIds = emptySet())
            next.copy(documents = filterDocuments(sourceDocuments, next))
        }
    }

    private fun organize(message: String, clearSelection: Boolean = false, block: suspend () -> Unit) {
        if (_uiState.value.isOrganizing) return
        _uiState.update { it.copy(isOrganizing = true, organizationError = null) }
        viewModelScope.launch {
            try {
                block()
                if (clearSelection) clearSelection()
                _uiState.update { it.copy(organizationSuccessVersion = it.organizationSuccessVersion + 1) }
                _effects.send(DocumentsEffect.ShowMessage(message))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                val errorMessage = error.message ?: "Dữ liệu không hợp lệ."
                _uiState.update { it.copy(organizationError = errorMessage) }
                _effects.send(DocumentsEffect.ShowMessage(errorMessage))
            } catch (_: Exception) {
                val errorMessage = "Không thể cập nhật. Tên có thể đã tồn tại hoặc dữ liệu vừa thay đổi."
                _uiState.update { it.copy(organizationError = errorMessage) }
                _effects.send(DocumentsEffect.ShowMessage(errorMessage))
            } finally {
                _uiState.update { it.copy(isOrganizing = false) }
            }
        }
    }

    private fun requireSelection(): Set<String> = _uiState.value.selectedDocumentIds.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Hãy chọn ít nhất một tài liệu.")

    private fun filterDocuments(documents: List<ScannedDocument>, state: DocumentsUiState): List<ScannedDocument> =
        documents.filter { document ->
            (state.selectedFolderId == null ||
                (state.selectedFolderId == UNFILED_FOLDER_FILTER_ID && document.folderId == null) ||
                document.folderId == state.selectedFolderId) &&
                (!state.favoriteOnly || document.isFavorite) &&
                (state.selectedTagId == null || document.tags.any { it.id == state.selectedTagId })
        }
}

const val UNFILED_FOLDER_FILTER_ID = "__unfiled__"

private fun PageEditException.toDuplicateDocumentMessage(): String = when (reason) {
    PageEditFailure.DOCUMENT_NOT_FOUND -> "Tài liệu không còn tồn tại."
    PageEditFailure.EMPTY_DOCUMENT -> "Không thể nhân bản tài liệu chưa có trang."
    PageEditFailure.SOURCE_UNAVAILABLE -> "Không thể đọc file của tài liệu."
    PageEditFailure.STORAGE_FULL -> "Thiết bị không còn đủ dung lượng trống."
    PageEditFailure.INVALID_IMAGE -> "Tài liệu chứa file ảnh không hợp lệ."
    else -> "Không thể nhân bản tài liệu. Hãy thử lại."
}
