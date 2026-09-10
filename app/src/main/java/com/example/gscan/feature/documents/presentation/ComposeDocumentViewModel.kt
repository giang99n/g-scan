package com.example.gscan.feature.documents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import com.example.gscan.feature.documents.domain.model.PageEditException
import com.example.gscan.feature.documents.domain.model.PageEditFailure
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import com.example.gscan.feature.documents.domain.usecase.ComposeDocumentUseCase
import com.example.gscan.feature.documents.domain.model.DocumentPageSelection
import com.example.gscan.feature.documents.domain.usecase.parsePageSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComposeSource(val id: String, val range: String = "")

data class ComposeDocumentState(
    val loading: Boolean = true,
    val documents: List<ScannedDocument> = emptyList(),
    val sources: List<ComposeSource> = emptyList(),
    val title: String = "Tài liệu mới",
    val busy: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val loadFailed: Boolean = false,
)

@HiltViewModel
class ComposeDocumentViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val composeDocument: ComposeDocumentUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ComposeDocumentState())
    val uiState = state.asStateFlow()
    private val created = Channel<String>(Channel.BUFFERED)
    val documentsCreated = created.receiveAsFlow()
    private var work: Job? = null
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        state.update { it.copy(loading = true, loadFailed = false, message = null) }
        observation = viewModelScope.launch {
            repository.observeDocuments().catch {
                state.update { it.copy(loading = false, loadFailed = true, message = "Không thể tải tài liệu. Hãy thử lại.") }
            }.collect { docs ->
                state.update { it.copy(loading = false, documents = docs) }
            }
        }
    }

    fun title(value: String) = edit { it.copy(title = value) }
    fun toggle(id: String) = edit { current ->
        current.copy(sources = if (current.sources.any { it.id == id }) {
            current.sources.filterNot { it.id == id }
        } else current.sources + ComposeSource(id))
    }
    fun range(id: String, value: String) = edit { current ->
        current.copy(sources = current.sources.map { if (it.id == id) it.copy(range = value) else it })
    }
    fun move(id: String, delta: Int) = edit { current ->
        val sources = current.sources.toMutableList()
        val from = sources.indexOfFirst { it.id == id }
        if (from >= 0 && from + delta in sources.indices) sources.add(from + delta, sources.removeAt(from))
        current.copy(sources = sources)
    }
    private fun edit(transform: (ComposeDocumentState) -> ComposeDocumentState) {
        state.update { if (it.busy) it else transform(it).copy(message = null) }
    }

    fun create() {
        val snapshot = state.value
        if (snapshot.busy || snapshot.sources.isEmpty()) return
        state.update { it.copy(busy = true, completed = 0, total = 0, message = null) }
        work = viewModelScope.launch {
            try {
                val selections = snapshot.sources.map { source ->
                    val details = repository.observeDocumentDetails(source.id).first()
                    requireNotNull(details) { "Tài liệu nguồn đã bị xóa. Hãy chọn lại." }
                    val pages = details.pages.sortedBy { it.position }
                    DocumentPageSelection(source.id, parsePageSelection(source.range, pages.size).map { pages[it - 1].id })
                }
                val id = composeDocument(snapshot.title, selections) { done, total ->
                    state.update { it.copy(completed = done, total = total) }
                }
                // trySend still delivers a committed result if cancellation raced with commit.
                created.trySend(id)
            } catch (error: CancellationException) {
                state.update { it.copy(message = "Đã hủy tạo tài liệu.") }
                throw error
            } catch (error: Exception) {
                val message = when {
                    error is IllegalArgumentException -> error.message ?: "Lựa chọn không hợp lệ."
                    error is PageEditException && error.reason == PageEditFailure.STORAGE_FULL -> "Không đủ dung lượng để sao chép tài liệu."
                    error is PageEditException && error.reason == PageEditFailure.SOURCE_UNAVAILABLE -> "Không đọc được ảnh nguồn. Hãy kiểm tra lại tài liệu."
                    else -> "Không thể tạo tài liệu. Hãy kiểm tra dung lượng và thử lại."
                }
                state.update { it.copy(message = message) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    fun cancel() { work?.cancel() }
}
