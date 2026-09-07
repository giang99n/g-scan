package com.example.gscan.feature.documents.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.documents.domain.usecase.parsePageSelection

@Composable
fun ComposeDocumentScreen(
    onBackClick: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: ComposeDocumentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnCreated by rememberUpdatedState(onCreated)
    LaunchedEffect(viewModel) { viewModel.documentsCreated.collect { currentOnCreated(it) } }
    BackHandler(enabled = state.busy) { viewModel.cancel() }
    val docs = state.documents.associateBy { it.id }
    val counts = state.sources.map { source ->
        runCatching { parsePageSelection(source.range, docs[source.id]?.pageCount ?: 0).size }
    }
    val valid = counts.all { it.isSuccess }
    val total = counts.sumOf { it.getOrDefault(0) }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            GScanTopAppBar(title = "Gộp / trích xuất trang", onBackClick = {
                if (state.busy) viewModel.cancel() else onBackClick()
            })
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Đang sao chép ${state.completed}/${state.total} trang…")
                        TextButton(onClick = viewModel::cancel) { Text("Hủy") }
                    } else {
                        Text(if (valid) "$total / 100 trang · Tạo bản mới, giữ nguyên bản nguồn" else "Kiểm tra lựa chọn trang bên dưới")
                        Button(
                            onClick = viewModel::create,
                            enabled = !state.loading && !state.loadFailed && valid && total in 1..100 && state.title.isNotBlank() && state.title.length <= 120,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.sources.size > 1) "Gộp thành tài liệu mới" else "Trích xuất thành tài liệu mới") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Chọn một tài liệu để tách trang hoặc nhiều tài liệu để gộp. Thứ tự bên dưới là thứ tự trong bản mới.")
                OutlinedTextField(
                    value = state.title, onValueChange = viewModel::title, enabled = !state.busy,
                    label = { Text("Tên tài liệu mới") }, singleLine = true,
                    isError = state.title.isBlank() || state.title.length > 120,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(state.sources, key = { "selected-${it.id}" }) { source ->
                val index = state.sources.indexOf(source)
                val doc = docs[source.id]
                val rangeError = runCatching { parsePageSelection(source.range, doc?.pageCount ?: 0) }.exceptionOrNull()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${index + 1}. ${doc?.title ?: "Tài liệu đã bị xóa"}", style = MaterialTheme.typography.titleMedium)
                        Text("${doc?.pageCount ?: 0} trang")
                        OutlinedTextField(
                            value = source.range, onValueChange = { viewModel.range(source.id, it) },
                            label = { Text("Trang cần lấy, ví dụ: 1, 3-5") },
                            supportingText = { Text(rangeError?.message ?: "Để trống để lấy tất cả. Giữ thứ tự nhập, bỏ trang trùng.") },
                            isError = rangeError != null, enabled = !state.busy, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row {
                            TextButton(onClick = { viewModel.move(source.id, -1) }, enabled = !state.busy && index > 0) { Text("Lên") }
                            TextButton(onClick = { viewModel.move(source.id, 1) }, enabled = !state.busy && index < state.sources.lastIndex) { Text("Xuống") }
                            TextButton(onClick = { viewModel.toggle(source.id) }, enabled = !state.busy) { Text("Bỏ chọn") }
                        }
                    }
                }
            }
            item {
                Text("Thêm tài liệu", style = MaterialTheme.typography.titleMedium)
                if (state.loading) CircularProgressIndicator()
                if (state.loadFailed) TextButton(onClick = viewModel::reload) { Text("Thử lại") }
                if (!state.loading && !state.loadFailed && state.documents.isEmpty()) Text("Chưa có tài liệu. Hãy scan hoặc nhập tài liệu trước.")
            }
            items(state.documents.filter { doc -> state.sources.none { it.id == doc.id } }, key = { it.id }) { doc ->
                OutlinedButton(
                    onClick = { viewModel.toggle(doc.id) }, enabled = !state.busy && doc.pageCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("${doc.title} · ${doc.pageCount} trang") }
            }
        }
    }
}
