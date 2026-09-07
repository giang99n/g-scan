package com.example.gscan.feature.documents.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.core.designsystem.component.LocalFileImage
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.documents.domain.model.ScannedDocument

@Composable
fun DocumentsRoute(
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onDocumentClick: (String) -> Unit,
    onComposeClick: (() -> Unit)? = null,
    onTrashClick: (() -> Unit)? = null,
    allowDocumentDuplication: Boolean = false,
    title: String = "Tài liệu",
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DocumentsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    DocumentsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onScanClick = onScanClick,
        onDocumentClick = onDocumentClick,
        onDeleteDocument = viewModel::delete,
        onDuplicateDocument = viewModel::duplicate,
        onSearchQueryChange = viewModel::updateSearchQuery,
        title = title,
        onComposeClick = onComposeClick,
        onTrashClick = onTrashClick,
        allowDocumentDuplication = allowDocumentDuplication,
    )
}

@Composable
private fun DocumentsScreen(
    uiState: DocumentsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onDocumentClick: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onDuplicateDocument: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    title: String,
    onComposeClick: (() -> Unit)?,
    onTrashClick: (() -> Unit)?,
    allowDocumentDuplication: Boolean,
) {
    var pendingDeleteDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteDocument = uiState.documents.firstOrNull { it.id == pendingDeleteDocumentId }

    if (pendingDeleteDocument != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteDocumentId = null },
            title = { Text("Đưa vào thùng rác?") },
            text = {
                Text(
                    "“${pendingDeleteDocument.title}” và ${pendingDeleteDocument.pageCount} trang " +
                        "sẽ được ẩn khỏi thư viện. Bạn có thể khôi phục sau.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteDocumentId = null
                        onDeleteDocument(pendingDeleteDocument.id)
                    },
                ) {
                    Text("Đưa vào thùng rác", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDocumentId = null }) {
                    Text("Hủy")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GScanTopAppBar(
                title = title,
                onBackClick = onBackClick,
                actions = {
                    if (onTrashClick != null) {
                        IconButton(onClick = onTrashClick) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Mở thùng rác")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Text("Scan", modifier = Modifier.padding(horizontal = 16.dp))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (onComposeClick != null) {
                TextButton(onClick = onComposeClick, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Gộp / trích xuất trang")
                }
            }
            DocumentSearchField(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.errorMessage != null -> ErrorDocuments(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    message = uiState.errorMessage,
                )

                uiState.documents.isEmpty() && uiState.searchQuery.isNotBlank() -> ErrorDocuments(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    message = "Không tìm thấy tài liệu phù hợp.",
                )

                uiState.documents.isEmpty() -> EmptyDocuments(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onScanClick = onScanClick,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.documents, key = { it.id }) { document ->
                        val isProcessing = uiState.deletingDocumentId == document.id ||
                            uiState.duplicatingDocumentId == document.id
                        val actionsEnabled = uiState.deletingDocumentId == null &&
                            uiState.duplicatingDocumentId == null
                        DocumentCard(
                            document = document,
                            isProcessing = isProcessing,
                            actionsEnabled = actionsEnabled,
                            duplicateEnabled = allowDocumentDuplication && document.pageCount > 0,
                            onClick = { onDocumentClick(document.id) },
                            onDuplicateClick = { onDuplicateDocument(document.id) },
                            onDeleteClick = { pendingDeleteDocumentId = document.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        label = { Text("Tìm tài liệu") },
        placeholder = { Text("Tên hoặc nội dung đã nhận dạng") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Xóa nội dung tìm kiếm")
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun EmptyDocuments(
    modifier: Modifier,
    onScanClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Chưa có tài liệu", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        Text("Scan tài liệu đầu tiên để lưu an toàn ngay trên thiết bị.")
        Spacer(Modifier.size(20.dp))
        Button(onClick = onScanClick) {
            Text("Bắt đầu scan")
        }
    }
}

@Composable
private fun ErrorDocuments(
    modifier: Modifier,
    message: String,
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DocumentCard(
    document: ScannedDocument,
    isProcessing: Boolean,
    actionsEnabled: Boolean,
    duplicateEnabled: Boolean,
    onClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isProcessing, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                uri = document.thumbnailUri,
                title = document.title,
                rotationDegrees = document.thumbnailRotationDegrees,
                signatureInk = document.thumbnailSignatureInk,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "${document.pageCount} trang · ${document.status.toLabel()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                if (duplicateEnabled) {
                    IconButton(onClick = onDuplicateClick, enabled = actionsEnabled) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Nhân bản ${document.title}",
                        )
                    }
                }
                IconButton(onClick = onDeleteClick, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Xóa ${document.title}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentThumbnail(
    uri: String?,
    title: String,
    rotationDegrees: Int,
    signatureInk: String,
) {
    Card(
        modifier = Modifier
            .size(width = 72.dp, height = 92.dp)
            .clip(RoundedCornerShape(10.dp)),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        LocalFileImage(
            uri = uri,
            contentDescription = "Trang đầu của $title",
            modifier = Modifier.fillMaxSize(),
            maxDecodeSizePx = THUMBNAIL_MAX_SIZE_PX,
            contentScale = ContentScale.Crop,
            rotationDegrees = rotationDegrees,
            signatureInk = signatureInk,
        )
    }
}

private fun DocumentStatus.toLabel(): String = when (this) {
    DocumentStatus.DRAFT -> "Bản nháp"
    DocumentStatus.PROCESSING -> "Đang xử lý"
    DocumentStatus.READY -> "Sẵn sàng"
    DocumentStatus.FAILED -> "Có lỗi"
}

private const val THUMBNAIL_MAX_SIZE_PX = 256
