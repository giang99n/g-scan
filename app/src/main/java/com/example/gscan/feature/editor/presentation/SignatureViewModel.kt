package com.example.gscan.feature.editor.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.core.image.SignatureInk
import com.example.gscan.feature.documents.domain.model.ScannedDocumentDetails
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import com.example.gscan.feature.editor.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SignatureState(
    val loading: Boolean = true,
    val details: ScannedDocumentDetails? = null,
    val pageIndex: Int = 0,
    val templates: List<SignatureTemplate> = emptyList(),
    val ink: Ink = emptyList(),
    val drawing: Ink = emptyList(),
    val drawingDirty: Boolean = false,
    val templateName: String = "Chữ ký của tôi",
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val page get() = details?.pages?.getOrNull(pageIndex)
    val aspect: Float get() {
        val p = page ?: return 1f
        return if (p.rotationDegrees % 180 == 0) p.width.toFloat() / p.height else p.height.toFloat() / p.width
    }
}

@HiltViewModel
class SignatureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val signatures: SignatureUseCases,
) : ViewModel() {
    private val documentId: String? = savedStateHandle["documentId"]
    private val state = MutableStateFlow(SignatureState())
    val uiState = state.asStateFlow()
    private var acceptingStroke = false

    init {
        reload()
        viewModelScope.launch {
            signatures.templates().catch {
                state.update { it.copy(message = "Không đọc được mẫu chữ ký. Hãy mở lại màn hình.") }
            }.collect { templates -> state.update { it.copy(templates = templates) } }
        }
    }

    fun reload() {
        if (state.value.busy) return
        state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            try {
                val details = documentId?.let { documents.observeDocumentDetails(it).first() }
                val index = state.value.pageIndex.coerceIn(0, (details?.pages?.lastIndex ?: 0).coerceAtLeast(0))
                val page = details?.pages?.getOrNull(index)
                val ink = withContext(Dispatchers.Default) {
                    page?.let { p -> SignatureInk.decode(p.signatureInk).map { stroke -> stroke.map { it.rotated(p.rotationDegrees) } } } ?: emptyList()
                }
                state.update { it.copy(loading = false, details = details, pageIndex = index, ink = ink, dirty = false,
                    message = if (documentId != null && page == null) "Tài liệu không còn trang để ký." else null) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                state.update { it.copy(loading = false, message = "Không tải được trang. Hãy thử lại.") }
            }
        }
    }

    fun selectPage(delta: Int) {
        val current = state.value
        if (current.dirty || current.busy || current.loading) return
        val index = current.pageIndex + delta
        if (index !in (current.details?.pages?.indices ?: IntRange.EMPTY)) return
        state.update { it.copy(pageIndex = index) }
        reload()
    }

    fun name(value: String) { if (!state.value.busy && value.length <= 60) state.update { it.copy(templateName = value) } }
    fun startStroke(point: InkPoint) {
        val current = state.value
        acceptingStroke = !current.busy && current.drawing.size < 100 && current.drawing.sumOf { it.size } < 8000
        if (acceptingStroke) state.update { it.copy(drawing = it.drawing + listOf(listOf(point)), drawingDirty = true) }
        else if (!current.busy) state.update { it.copy(message = "Đã đạt giới hạn nét vẽ. Hãy lưu mẫu hoặc bỏ bớt nét.") }
    }
    fun extendStroke(point: InkPoint) {
        state.update { if (!acceptingStroke || it.busy || it.drawing.isEmpty() || it.drawing.sumOf { stroke -> stroke.size } >= 8000) it
            else it.copy(drawing = it.drawing.dropLast(1) + listOf(it.drawing.last() + point)) }
    }
    fun undoStroke() { if (!state.value.busy) state.update { it.copy(drawing = it.drawing.dropLast(1), drawingDirty = it.drawing.size > 1) } }
    fun clearDrawing() { if (!state.value.busy) state.update { it.copy(drawing = emptyList(), drawingDirty = false) } }

    fun useInk(ink: Ink) {
        val current = state.value
        if (current.page == null || current.busy || current.loading || ink.isEmpty()) return
        val width = minOf(0.4f, 0.5f * 2.5f / current.aspect)
        val height = width * current.aspect / 2.5f
        state.update { it.copy(ink = ink.map { stroke -> stroke.map { p ->
            InkPoint(0.5f - width / 2 + p.x * width, 0.7f - height / 2 + p.y * height)
        } }, dirty = true, message = null) }
    }

    fun move(dx: Float, dy: Float) {
        val current = state.value
        val points = current.ink.flatten()
        if (points.isEmpty() || current.busy) return
        val x = dx.coerceIn(-points.minOf { it.x }, 1f - points.maxOf { it.x })
        val y = dy.coerceIn(-points.minOf { it.y }, 1f - points.maxOf { it.y })
        state.update { it.copy(ink = it.ink.map { stroke -> stroke.map { p ->
            InkPoint((p.x + x).coerceIn(0f, 1f), (p.y + y).coerceIn(0f, 1f))
        } }, dirty = true) }
    }

    fun resize(factor: Float) {
        val current = state.value
        val points = current.ink.flatten()
        if (points.isEmpty() || current.busy) return
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        if (factor < 1f && maxOf(maxX - minX, maxY - minY) < 0.025f) return
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        val scaled = current.ink.map { stroke -> stroke.map { p -> InkPoint(cx + (p.x - cx) * factor, cy + (p.y - cy) * factor) } }
        if (scaled.flatten().any { it.x !in 0f..1f || it.y !in 0f..1f }) return
        state.update { it.copy(ink = scaled, dirty = true) }
    }

    fun removeInk() { if (!state.value.busy) state.update { it.copy(ink = emptyList(), dirty = true) } }
    fun saveTemplate() {
        val current = state.value
        perform("Đã lưu mẫu chữ ký.") {
            signatures.saveTemplate(current.templateName, current.drawing)
            state.update { it.copy(drawingDirty = false) }
        }
    }
    fun deleteTemplate(id: String) = perform("Đã xóa mẫu. Chữ ký đã đặt trên trang vẫn được giữ.") { signatures.deleteTemplate(id) }
    fun savePage() {
        val current = state.value
        val page = current.page ?: return
        perform("Đã lưu chữ ký trên trang.") {
            val ink = current.ink.map { stroke -> stroke.map { it.rotated(-page.rotationDegrees) } }
            signatures.savePage(current.details!!.document.id, page.id, page.rotationDegrees, ink)
            state.update { it.copy(dirty = false) }
        }
    }
    private fun perform(success: String, action: suspend () -> Unit) {
        if (state.value.busy || state.value.loading) return
        state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                action()
                state.update { it.copy(message = success) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                state.update { it.copy(message = if (error is IllegalArgumentException) error.message else "Không lưu được thay đổi. Kiểm tra dung lượng và thử lại.") }
            } finally { state.update { it.copy(busy = false) } }
        }
    }
}
