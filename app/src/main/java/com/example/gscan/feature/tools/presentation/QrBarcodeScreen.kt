package com.example.gscan.feature.tools.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.tools.domain.webUrl
import java.text.DateFormat
import java.util.Date

@Composable
fun QrBarcodeScreen(onBackClick: () -> Unit, viewModel: QrBarcodeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) viewModel.gallerySelectionCancelled() else viewModel.scanImage(uri.toString())
    }
    LaunchedEffect(state.selected?.id) {
        if (state.selected != null) listState.animateScrollToItem(0)
    }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableStateOf(false) }
    var openUrl by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    BackHandler(state.busy || state.unsaved) { if (!state.busy) discard = true }

    if (state.galleryCandidates.isNotEmpty()) AlertDialog(
        onDismissRequest = viewModel::dismissGalleryResults,
        title = { Text("Chọn mã trong ảnh") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.galleryCandidates) { result ->
                    Card(
                        onClick = { viewModel.selectGalleryResult(result) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${result.format} · ${result.type}", style = MaterialTheme.typography.titleSmall)
                            Text(result.content, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = viewModel::dismissGalleryResults) { Text("Hủy") }
        },
    )

    if (deleteId != null || clear) AlertDialog(
        onDismissRequest = { deleteId = null; clear = false },
        title = { Text(if (clear) "Xóa toàn bộ lịch sử?" else "Xóa kết quả quét?") },
        text = { Text("Thao tác này không thể hoàn tác.") },
        confirmButton = { TextButton(onClick = {
            if (clear) viewModel.clearHistory() else deleteId?.let(viewModel::delete)
            deleteId = null; clear = false
        }, enabled = !state.busy) { Text("Xóa") } },
        dismissButton = { TextButton(onClick = { deleteId = null; clear = false }) { Text("Hủy") } },
    )
    openUrl?.let { url -> AlertDialog(
        onDismissRequest = { openUrl = null }, title = { Text("Mở đường dẫn?") },
        text = { SensitiveSelectionContainer(onCopyError = { viewModel.message("Không thể sao chép.") }) {
            Text(url, Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
        } },
        confirmButton = { TextButton(onClick = {
            openUrl = null
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
                .onFailure { viewModel.message("Không có ứng dụng phù hợp để mở đường dẫn.") }
        }) { Text("Mở") } },
        dismissButton = { TextButton(onClick = { openUrl = null }) { Text("Hủy") } },
    ) }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("Kết quả chưa lưu vào lịch sử") },
        text = { Text("Sao chép hoặc thử lưu lại trước khi rời màn hình.") },
        confirmButton = { TextButton(onClick = { discard = false; onBackClick() }) { Text("Rời màn hình") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Ở lại") } },
    )
    Scaffold(
        topBar = { GScanTopAppBar(title = "QR & barcode", onBackClick = {
            if (state.unsaved) discard = true else onBackClick()
        }, navigationEnabled = !state.busy) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Quét QR, mã sản phẩm và các barcode phổ biến bằng camera hoặc từ ảnh.")
                Text("Camera cần Google Play services; đọc từ ảnh chạy offline trên thiết bị.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::scan, enabled = !state.busy && !state.unsaved, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.scanning) "Đang mở máy quét…" else "Quét bằng camera")
                }
                OutlinedButton(
                    onClick = {
                        if (state.readingImage) viewModel.cancelImageScan()
                        else imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !state.saving && !state.scanning && !state.unsaved,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.readingImage) "Hủy đọc ảnh" else "Chọn ảnh từ thư viện")
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.message?.let { Text(it) }
            }
            state.selected?.let { selected -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${selected.result.format} · ${selected.result.type}", style = MaterialTheme.typography.titleMedium)
                        SensitiveSelectionContainer(onCopyError = { viewModel.message("Không thể sao chép.") }) {
                            Text(selected.result.content, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
                        }
                        Row {
                            TextButton(onClick = {
                                runCatching {
                                    val clip = ClipData.newPlainText("Kết quả quét", selected.result.content).markSensitive()
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                }.onSuccess { viewModel.message("Đã sao chép.") }.onFailure { viewModel.message("Không thể sao chép.") }
                            }) { Text("Sao chép") }
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, selected.result.content)
                                }, "Chia sẻ kết quả")) }.onFailure { viewModel.message("Không thể mở ứng dụng chia sẻ.") }
                            }) { Text("Chia sẻ") }
                        }
                        selected.result.webUrl()?.let { url -> TextButton(onClick = { openUrl = url }) { Text("Mở đường dẫn…") } }
                        if (state.unsaved) {
                            Text("Chưa lưu vào lịch sử.")
                            TextButton(onClick = viewModel::retrySave, enabled = !state.busy) { Text("Thử lưu lại") }
                        } else TextButton(onClick = viewModel::dismissResult, enabled = !state.busy) { Text("Đóng kết quả") }
                    }
                }
            } }
            item {
                Text("Lịch sử trên thiết bị", style = MaterialTheme.typography.titleMedium)
                Text("Kết quả được lưu local, có thể chứa mật khẩu Wi-Fi hoặc thông tin liên hệ.", style = MaterialTheme.typography.bodySmall)
                if (state.history.isNotEmpty()) TextButton(onClick = { clear = true }, enabled = !state.busy) { Text("Xóa lịch sử") }
                if (state.loading) CircularProgressIndicator()
                if (state.historyError) {
                    Text("Không tải được lịch sử.")
                    TextButton(onClick = viewModel::reload) { Text("Thử lại") }
                } else if (!state.loading && state.history.isEmpty()) Text("Chưa có kết quả quét.")
            }
            items(state.history, key = { it.id }) { item ->
                Card(onClick = { viewModel.select(item) }, enabled = !state.busy && !state.unsaved) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${item.result.format} · ${item.result.type}")
                        Text(item.result.content, maxLines = 3)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.scannedAt)), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { deleteId = item.id }, enabled = !state.busy) { Text("Xóa") }
                    }
                }
            }
        }
    }
}
