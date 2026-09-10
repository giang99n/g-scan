package com.example.gscan.feature.ocr.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.ocr.domain.model.OcrJobStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageText
import com.example.gscan.feature.ocr.domain.model.OcrTextExportMode
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun OcrRoute(
    onBackClick: () -> Unit,
    viewModel: OcrViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(TEXT_MIME_TYPE),
    ) { uri -> uri?.let { viewModel.saveTextTo(it.toString()) } }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OcrEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    OcrScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onStartClick = viewModel::start,
        onCancelClick = viewModel::cancel,
        onCopyPage = { page ->
            context.copyText("Trang ${page.position + 1}", page.text)
            scope.launch { snackbarHostState.showSnackbar("Đã sao chép trang ${page.position + 1}.") }
        },
        onCopyAll = {
            val text = uiState.results
                .filter { it.text.isNotBlank() }
                .joinToString("\n\n") { "Trang ${it.position + 1}\n${it.text}" }
            if (text.isNotBlank()) {
                context.copyText(uiState.title ?: "GScan OCR", text)
                scope.launch { snackbarHostState.showSnackbar("Đã sao chép toàn bộ văn bản.") }
            }
        },
        onExportModeSelected = viewModel::selectExportMode,
        onCreateText = viewModel::createText,
        onSaveText = {
            uiState.exportedText?.let { saveLauncher.launch(it.displayName) }
        },
        onShareText = {
            val exported = uiState.exportedText
            if (exported != null) {
                runCatching {
                    val file = File(exported.filePath)
                    check(file.isFile) { "Exported TXT is no longer available" }
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = TEXT_MIME_TYPE
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        clipData = ClipData.newRawUri("GScan OCR", contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Chia sẻ văn bản OCR"))
                }.onFailure {
                    scope.launch {
                        snackbarHostState.showSnackbar("Không thể mở ứng dụng chia sẻ.")
                    }
                }
            }
        },
    )
}

@Composable
private fun OcrScreen(
    uiState: OcrUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onCopyPage: (OcrPageText) -> Unit,
    onCopyAll: () -> Unit,
    onExportModeSelected: (OcrTextExportMode) -> Unit,
    onCreateText: () -> Unit,
    onSaveText: () -> Unit,
    onShareText: () -> Unit,
) {
    val isRunning = uiState.job.status == OcrJobStatus.QUEUED ||
        uiState.job.status == OcrJobStatus.RUNNING
    val hasText = uiState.results.any { it.text.isNotBlank() }
    val exportBusy = uiState.isCreatingText || uiState.isSavingText
    val hasExportableText = when (uiState.exportMode) {
        OcrTextExportMode.SUCCESSFUL_ONLY -> uiState.results.any {
            it.status == OcrPageStatus.SUCCEEDED && it.text.isNotBlank()
        }
        OcrTextExportMode.KEEP_PAGE_PLACEHOLDERS -> hasText
    }

    BackHandler(enabled = exportBusy) {
        // Không rời màn hình khi file đang được tạo hoặc ghi vào URI người dùng đã chọn.
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GScanTopAppBar(
                title = uiState.title ?: "Nhận dạng văn bản",
                onBackClick = onBackClick,
                navigationEnabled = !exportBusy,
                actions = {
                    if (hasText) {
                        IconButton(onClick = onCopyAll, enabled = !exportBusy) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Sao chép toàn bộ")
                        }
                    }
                    if (isRunning) {
                        IconButton(onClick = onCancelClick, enabled = !exportBusy) {
                            Icon(Icons.Rounded.Stop, contentDescription = "Dừng nhận dạng")
                        }
                    } else if (uiState.detailsAvailable && uiState.pageCount > 0) {
                        IconButton(onClick = onStartClick, enabled = !exportBusy) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Chạy lại nhận dạng")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.errorMessage != null -> OcrMessage(
                message = uiState.errorMessage,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            !uiState.detailsAvailable -> OcrMessage(
                message = "Tài liệu không còn tồn tại.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            uiState.pageCount == 0 -> OcrMessage(
                message = "Tài liệu chưa có trang để nhận dạng.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                OcrJobHeader(
                    uiState = uiState,
                    isRunning = isRunning,
                    actionsEnabled = !exportBusy,
                    onStartClick = onStartClick,
                    onCancelClick = onCancelClick,
                )
                OcrTextExportCard(
                    uiState = uiState,
                    enabled = hasExportableText && !isRunning,
                    onModeSelected = onExportModeSelected,
                    onCreate = onCreateText,
                    onSave = onSaveText,
                    onShare = onShareText,
                )
                if (uiState.results.isEmpty()) {
                    OcrMessage(
                        message = "Chưa có nội dung OCR. Nhấn “Nhận dạng” để đọc chữ trên ${uiState.pageCount} trang.",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.results, key = { it.pageId }) { page ->
                            OcrPageCard(
                                page = page,
                                jobIsRunning = isRunning,
                                onCopy = { onCopyPage(page) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrTextExportCard(
    uiState: OcrUiState,
    enabled: Boolean,
    onModeSelected: (OcrTextExportMode) -> Unit,
    onCreate: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val busy = uiState.isCreatingText || uiState.isSavingText
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Xuất văn bản TXT", style = MaterialTheme.typography.titleMedium)
            Text(
                "Nội dung được sắp theo đúng thứ tự trang.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OcrExportModeRow(
                title = "Chỉ trang OCR thành công",
                description = "Bỏ qua trang lỗi, trống hoặc chưa nhận dạng.",
                selected = uiState.exportMode == OcrTextExportMode.SUCCESSFUL_ONLY,
                enabled = !busy,
                onClick = { onModeSelected(OcrTextExportMode.SUCCESSFUL_ONLY) },
            )
            OcrExportModeRow(
                title = "Giữ đủ vị trí trang",
                description = "Thêm placeholder cho trang chưa có văn bản.",
                selected = uiState.exportMode == OcrTextExportMode.KEEP_PAGE_PLACEHOLDERS,
                enabled = !busy,
                onClick = { onModeSelected(OcrTextExportMode.KEEP_PAGE_PLACEHOLDERS) },
            )
            Button(
                onClick = onCreate,
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isCreatingText) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                }
                Text(if (uiState.exportedText == null) "Tạo file TXT" else "Tạo lại file TXT")
            }
            if (!enabled && !busy) {
                Text(
                    "Không có trang phù hợp với chế độ xuất đã chọn, hoặc OCR đang chạy.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            uiState.exportErrorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            uiState.exportedText?.let { exported ->
                Text(
                    "Đã sẵn sàng: ${exported.displayName} (${exported.exportedPageCount}/${exported.pageCount} trang)",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = onSave,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                        Text(if (uiState.isSavingText) "Đang lưu" else "Lưu", Modifier.padding(start = 6.dp))
                    }
                    FilledTonalButton(
                        onClick = onShare,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Text("Chia sẻ", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrExportModeRow(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.padding(start = 6.dp)) {
            Text(title)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OcrJobHeader(
    uiState: OcrUiState,
    isRunning: Boolean,
    actionsEnabled: Boolean,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isRunning) {
            val total = uiState.job.totalPages.takeIf { it > 0 } ?: uiState.pageCount
            val progress = if (total > 0) {
                uiState.job.completedPages.toFloat() / total.toFloat()
            } else {
                0f
            }
            Text("Đang nhận dạng ${uiState.job.completedPages}/$total trang")
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onCancelClick, enabled = actionsEnabled) { Text("Dừng") }
        } else {
            if (uiState.unrecognizedPageCount > 0 && uiState.results.isNotEmpty()) {
                Text(
                    text = "Có ${uiState.unrecognizedPageCount} trang chưa được nhận dạng.",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            when (uiState.job.status) {
                OcrJobStatus.FAILED -> Text(
                    "Một số trang nhận dạng chưa thành công. Bạn có thể chạy lại.",
                    color = MaterialTheme.colorScheme.error,
                )
                OcrJobStatus.CANCELLED -> Text("Đã dừng nhận dạng.")
                OcrJobStatus.SUCCEEDED -> Text("Đã nhận dạng xong ${uiState.pageCount} trang.")
                else -> Unit
            }
            Button(onClick = onStartClick, enabled = actionsEnabled) {
                Text(if (uiState.results.isEmpty()) "Nhận dạng" else "Chạy lại OCR")
            }
        }
    }
}

@Composable
private fun OcrPageCard(
    page: OcrPageText,
    jobIsRunning: Boolean,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Trang ${page.position + 1}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (page.text.isNotBlank()) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Sao chép trang ${page.position + 1}")
                    }
                }
            }
            when (page.status) {
                OcrPageStatus.PENDING -> Text(
                    if (jobIsRunning) "Đang chờ…" else "OCR bị gián đoạn. Hãy chạy lại.",
                )
                OcrPageStatus.PROCESSING -> if (jobIsRunning) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Đang nhận dạng…")
                    }
                } else {
                    Text("OCR bị gián đoạn. Hãy chạy lại.")
                }
                OcrPageStatus.FAILED -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (page.text.isBlank()) {
                            "Không thể nhận dạng trang này."
                        } else {
                            "Lần nhận dạng mới thất bại; đang giữ kết quả trước."
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (page.text.isNotBlank()) {
                        SelectionContainer {
                            Text(page.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OcrPageStatus.SUCCEEDED -> if (page.text.isBlank()) {
                    Text(
                        "Không tìm thấy chữ trên trang.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        Text(page.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrMessage(
    message: String,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun Context.copyText(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, text))
}

private const val TEXT_MIME_TYPE = "text/plain"
