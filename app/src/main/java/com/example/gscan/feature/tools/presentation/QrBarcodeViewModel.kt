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
    val readingImage: Boolean = false,
    val saving: Boolean = false,
    val galleryCandidates: List<BarcodeResult> = emptyList(),
    val historyError: Boolean = false,
    val message: String? = null,
) { val busy get() = scanning || readingImage || saving }

@HiltViewModel
class QrBarcodeViewModel @Inject constructor(
    private val scanner: BarcodeScanner,
    private val galleryScanner: GalleryBarcodeScanner,
    private val history: BarcodeHistoryUseCases,
) : ViewModel() {
    private val state = MutableStateFlow(QrBarcodeState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    private var imageScanJob: Job? = null
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
                    state.update { it.copy(scanning = false) }
                    acceptResult(result)
                }
            },
            onCancelled = { state.update { it.copy(scanning = false, message = "Đã hủy quét.") } },
            onError = { message -> state.update { it.copy(scanning = false, message = message) } },
        )
    }
    fun scanImage(imageUri: String) {
        if (state.value.busy || state.value.unsaved) return
        imageScanJob?.cancel()
        state.update {
            it.copy(readingImage = true, galleryCandidates = emptyList(), message = null)
        }
        imageScanJob = viewModelScope.launch {
            try {
                val results = galleryScanner.scan(imageUri)
                if (results.size == 1) {
                    state.update { it.copy(readingImage = false) }
                    acceptResult(results.single())
                } else {
                    state.update {
                        it.copy(
                            galleryCandidates = results,
                            message = "Tìm thấy ${results.size} mã. Chọn một mã để xem và lưu.",
                        )
                    }
                }
            } catch (error: CancellationException) {
                state.update { it.copy(message = "Đã hủy đọc ảnh.") }
                throw error
            } catch (error: GalleryBarcodeScanException) {
                state.update { it.copy(message = error.failure.toUserMessage()) }
            } catch (_: Exception) {
                state.update { it.copy(message = GalleryBarcodeScanFailure.PROCESSING_FAILED.toUserMessage()) }
            } finally {
                state.update { it.copy(readingImage = false) }
                imageScanJob = null
            }
        }
    }
    fun cancelImageScan() {
        imageScanJob?.cancel()
    }
    fun gallerySelectionCancelled() {
        if (!state.value.busy) state.update { it.copy(message = "Đã hủy chọn ảnh.") }
    }
    fun selectGalleryResult(result: BarcodeResult) {
        if (state.value.busy || result !in state.value.galleryCandidates) return
        state.update { it.copy(galleryCandidates = emptyList(), message = null) }
        acceptResult(result)
    }
    fun dismissGalleryResults() {
        if (!state.value.busy) state.update { it.copy(galleryCandidates = emptyList(), message = null) }
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
    private fun acceptResult(result: BarcodeResult) {
        state.update {
            it.copy(
                selected = BarcodeHistoryItem(UUID.randomUUID().toString(), result, System.currentTimeMillis()),
                unsaved = true,
            )
        }
        retrySave()
    }
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
    override fun onCleared() {
        cleared = true
        imageScanJob?.cancel()
    }
}

private fun GalleryBarcodeScanFailure.toUserMessage(): String = when (this) {
    GalleryBarcodeScanFailure.INVALID_URI -> "Ảnh đã chọn không còn quyền truy cập. Hãy chọn lại."
    GalleryBarcodeScanFailure.IMAGE_UNREADABLE -> "Không đọc được ảnh này. Hãy chọn ảnh JPEG, PNG hoặc WebP khác."
    GalleryBarcodeScanFailure.NO_READABLE_CODE -> "Không tìm thấy QR/barcode có nội dung đọc được trong ảnh."
    GalleryBarcodeScanFailure.CONTENT_TOO_LONG -> "Nội dung mã trong ảnh quá dài để hiển thị và lưu."
    GalleryBarcodeScanFailure.PROCESSING_FAILED -> "Không thể nhận dạng mã trong ảnh. Hãy thử ảnh rõ hơn."
}
