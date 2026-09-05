package com.example.gscan.feature.ocr.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.ocr.domain.model.OcrJobStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageStatus
import com.example.gscan.feature.ocr.domain.model.OcrPageText
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
) {
    val isRunning = uiState.job.status == OcrJobStatus.QUEUED ||
        uiState.job.status == OcrJobStatus.RUNNING
    val hasText = uiState.results.any { it.text.isNotBlank() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GScanTopAppBar(
                title = uiState.title ?: "Nhận dạng văn bản",
                onBackClick = onBackClick,
                actions = {
                    if (hasText) {
                        IconButton(onClick = onCopyAll) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Sao chép toàn bộ")
                        }
                    }
                    if (isRunning) {
                        IconButton(onClick = onCancelClick) {
                            Icon(Icons.Rounded.Stop, contentDescription = "Dừng nhận dạng")
                        }
                    } else if (uiState.detailsAvailable && uiState.pageCount > 0) {
                        IconButton(onClick = onStartClick) {
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
                    onStartClick = onStartClick,
                    onCancelClick = onCancelClick,
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
private fun OcrJobHeader(
    uiState: OcrUiState,
    isRunning: Boolean,
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
            OutlinedButton(onClick = onCancelClick) { Text("Dừng") }
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
            Button(onClick = onStartClick) {
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
