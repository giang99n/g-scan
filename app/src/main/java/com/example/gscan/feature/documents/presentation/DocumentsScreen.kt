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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
) {
    var pendingDeleteDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteDocument = uiState.documents.firstOrNull { it.id == pendingDeleteDocumentId }

    if (pendingDeleteDocument != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteDocumentId = null },
            title = { Text("Xóa tài liệu?") },
            text = {
                Text(
                    "“${pendingDeleteDocument.title}” và ${pendingDeleteDocument.pageCount} trang " +
                        "sẽ bị xóa vĩnh viễn khỏi thiết bị.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteDocumentId = null
                        onDeleteDocument(pendingDeleteDocument.id)
                    },
                ) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
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
                title = "Tài liệu",
                onBackClick = onBackClick,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Text("Scan", modifier = Modifier.padding(horizontal = 16.dp))
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.errorMessage != null -> ErrorDocuments(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = uiState.errorMessage,
            )

            uiState.documents.isEmpty() -> EmptyDocuments(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onScanClick = onScanClick,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.documents, key = { it.id }) { document ->
                    DocumentCard(
                        document = document,
                        isDeleting = uiState.deletingDocumentId == document.id,
                        deleteEnabled = uiState.deletingDocumentId == null,
                        onClick = { onDocumentClick(document.id) },
                        onDeleteClick = { pendingDeleteDocumentId = document.id },
                    )
                }
            }
        }
    }
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
    isDeleting: Boolean,
    deleteEnabled: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeleting, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                uri = document.thumbnailUri,
                title = document.title,
                rotationDegrees = document.thumbnailRotationDegrees,
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
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDeleteClick, enabled = deleteEnabled) {
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
