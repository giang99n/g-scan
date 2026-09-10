package com.example.gscan.feature.editor.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.core.designsystem.component.LocalFileImage
import com.example.gscan.core.image.SignatureInk
import com.example.gscan.feature.editor.domain.Ink
import com.example.gscan.feature.editor.domain.InkPoint

@Composable
fun SignatureScreen(
    onBackClick: () -> Unit,
    onChooseDocument: () -> Unit,
    viewModel: SignatureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val back = { if (!state.busy) {
        if (state.dirty || state.drawingDirty) confirmLeave = true else onBackClick()
    } }
    BackHandler(state.dirty || state.drawingDirty || state.busy) { back() }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false }, title = { Text("Bỏ thay đổi chưa lưu?") },
        text = { Text("Nét vẽ hoặc vị trí chữ ký chưa lưu sẽ bị bỏ.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onBackClick() }) { Text("Bỏ thay đổi") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Tiếp tục chỉnh") } },
    )
    pendingDelete?.let { id -> AlertDialog(
        onDismissRequest = { pendingDelete = null }, title = { Text("Xóa mẫu chữ ký?") },
        text = { Text("Chữ ký đã đặt trên tài liệu vẫn được giữ.") },
        confirmButton = { TextButton(onClick = { pendingDelete = null; viewModel.deleteTemplate(id) }) { Text("Xóa") } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Hủy") } },
    ) }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { GScanTopAppBar(title = "Chữ ký", onBackClick = back, navigationEnabled = !state.busy) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                    state.message?.let { Text(it) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.page != null) {
                        Button(onClick = viewModel::savePage, enabled = state.dirty && !state.busy && !state.loading, modifier = Modifier.fillMaxWidth()) {
                            Text("Lưu chữ ký trên trang")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) item { CircularProgressIndicator() }
            item { Text("Vẽ mẫu bên dưới hoặc chọn mẫu đã lưu. Mỗi trang đặt một chữ ký; có thể thay mẫu hoặc xóa trước khi xuất PDF.") }
            val page = state.page
            if (page != null && !state.loading) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.selectPage(-1) }, enabled = !state.dirty && !state.busy && state.pageIndex > 0) { Text("Trước") }
                        Text("Trang ${state.pageIndex + 1}/${state.details!!.pages.size}", Modifier.weight(1f).padding(top = 12.dp))
                        TextButton(onClick = { viewModel.selectPage(1) }, enabled = !state.dirty && !state.busy && state.pageIndex < state.details!!.pages.lastIndex) { Text("Sau") }
                    }
                    Text("Lưu hoặc hoàn tác trước khi chuyển trang.")
                    Box(Modifier.fillMaxWidth().aspectRatio(state.aspect).background(Color.White)) {
                        LocalFileImage(page.sourceUri, "Trang cần ký", Modifier.fillMaxSize(), rotationDegrees = page.rotationDegrees)
                        InkCanvas(state.ink, Modifier.matchParentSize().pointerInput(page.id, state.busy) {
                            if (!state.busy) detectDragGestures { change, delta ->
                                change.consume()
                                viewModel.move(delta.x / size.width, delta.y / size.height)
                            }
                        })
                    }
                    Text("Kéo trên trang để di chuyển chữ ký.")
                    Row {
                        TextButton(onClick = { viewModel.resize(0.9f) }, enabled = !state.busy && state.ink.isNotEmpty()) { Text("Thu nhỏ") }
                        TextButton(onClick = { viewModel.resize(1.1f) }, enabled = !state.busy && state.ink.isNotEmpty()) { Text("Phóng to") }
                        TextButton(onClick = viewModel::removeInk, enabled = !state.busy && state.ink.isNotEmpty()) { Text("Xóa ký") }
                    }
                    TextButton(onClick = viewModel::reload, enabled = !state.busy) { Text("Hoàn tác / tải lại trang") }
                }
            } else if (!state.loading) item {
                Button(onClick = onChooseDocument, enabled = !state.busy) { Text("Chọn tài liệu để ký") }
                TextButton(onClick = viewModel::reload, enabled = !state.busy) { Text("Tải lại") }
            }
            item {
                Text("Vẽ chữ ký", style = MaterialTheme.typography.titleMedium)
                InkCanvas(state.drawing, Modifier.fillMaxWidth().aspectRatio(2.5f).background(Color.White)
                    .pointerInput(state.busy) {
                        fun point(offset: Offset) = InkPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))
                        if (!state.busy) detectDragGestures(
                            onDragStart = { viewModel.startStroke(point(it)) },
                            onDrag = { change, _ -> change.consume(); viewModel.extendStroke(point(change.position)) },
                        )
                    })
                Row {
                    TextButton(onClick = viewModel::undoStroke, enabled = !state.busy && state.drawing.isNotEmpty()) { Text("Bỏ nét cuối") }
                    TextButton(onClick = viewModel::clearDrawing, enabled = !state.busy && state.drawing.isNotEmpty()) { Text("Vẽ lại") }
                }
                OutlinedTextField(state.templateName, viewModel::name, enabled = !state.busy, label = { Text("Tên mẫu chữ ký") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::saveTemplate, enabled = !state.busy && state.drawing.any { it.size > 1 } && state.templateName.isNotBlank()) { Text("Lưu mẫu") }
                    if (page != null) Button(onClick = { viewModel.useInk(state.drawing) }, enabled = !state.busy && state.drawing.any { it.size > 1 }) { Text("Đặt lên trang") }
                }
            }
            item {
                Text("Mẫu đã lưu", style = MaterialTheme.typography.titleMedium)
                if (state.templates.isEmpty()) Text("Chưa có mẫu chữ ký.")
            }
            items(state.templates, key = { it.id }) { template ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(template.name)
                        InkCanvas(template.ink, Modifier.fillMaxWidth().aspectRatio(2.5f).background(Color.White))
                        Row {
                            if (page != null) TextButton(onClick = { viewModel.useInk(template.ink) }, enabled = !state.busy) { Text("Dùng mẫu này") }
                            TextButton(onClick = { pendingDelete = template.id }, enabled = !state.busy) { Text("Xóa mẫu") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InkCanvas(ink: Ink, modifier: Modifier) {
    Canvas(modifier) {
        drawIntoCanvas { SignatureInk.draw(it.nativeCanvas, ink, size.width, size.height) }
    }
}
