package com.example.gscan.feature.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.tools.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QrBarcodeState(
    val loading: Boolean = true,
    val history: List<BarcodeHistoryItem> = emptyList(),
    val selected: BarcodeHistoryItem? = null,
    val unsaved: Boolean = false,
    val scanning: Boolean = false,
    val saving: Boolean = false,
    val historyError: Boolean = false,
    val message: String? = null,
) { val busy get() = scanning || saving }

@HiltViewModel
class QrBarcodeViewModel @Inject constructor(
    private val scanner: BarcodeScanner,
    private val history: BarcodeHistoryUseCases,
) : ViewModel() {
    private val state = MutableStateFlow(QrBarcodeState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    private var cleared = false
    init { reload() }

    fun reload() {
        observation?.cancel()
        state.update { it.copy(loading = true, historyError = false) }
        observation = viewModelScope.launch {
            history.observe().catch {
                state.update { it.copy(loading = false, historyError = true) }
            }.collect { items -> state.update { it.copy(loading = false, history = items, historyError = false) } }
        }
    }
    fun scan() {
        if (state.value.busy) return
        state.update { it.copy(scanning = true, message = null) }
        scanner.scan(
            onResult = { result ->
                if (!cleared) {
                    state.update { it.copy(scanning = false, selected = BarcodeHistoryItem(UUID.randomUUID().toString(), result, System.currentTimeMillis()), unsaved = true) }
                    retrySave()
                }
            },
            onCancelled = { state.update { it.copy(scanning = false, message = "Đã hủy quét.") } },
            onError = { message -> state.update { it.copy(scanning = false, message = message) } },
        )
    }
    fun select(item: BarcodeHistoryItem) {
        if (!state.value.busy && !state.value.unsaved) state.update { it.copy(selected = item, unsaved = false, message = null) }
    }
    fun retrySave() {
        val current = state.value
        val item = current.selected ?: return
        if (current.busy || !current.unsaved) return
        mutate("Không lưu được lịch sử. Kết quả vẫn có thể sao chép hoặc chia sẻ.") {
            history.save(item)
            state.update { it.copy(unsaved = false) }
        }
    }
    fun delete(id: String) = mutate("Không xóa được mục lịch sử. Hãy thử lại.") {
        history.delete(id)
        state.update { if (it.selected?.id == id) it.copy(selected = null, unsaved = false) else it }
    }
    fun clearHistory() = mutate("Không xóa được lịch sử. Hãy thử lại.") {
        history.clear()
        state.update { if (it.unsaved) it else it.copy(selected = null) }
    }
    fun dismissResult() {
        if (!state.value.busy) state.update { it.copy(selected = null, unsaved = false, message = null) }
    }
    fun message(value: String) { state.update { it.copy(message = value) } }
    private fun mutate(errorMessage: String, action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try { action()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { state.update { it.copy(message = errorMessage) }
            } finally { state.update { it.copy(saving = false) } }
        }
    }
    override fun onCleared() { cleared = true }
}
