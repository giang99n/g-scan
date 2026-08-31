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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    DocumentsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onScanClick = onScanClick,
        onDocumentClick = onDocumentClick,
    )
}

@Composable
private fun DocumentsScreen(
    uiState: DocumentsUiState,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onDocumentClick: (String) -> Unit,
) {
    Scaffold(
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
                        onClick = { onDocumentClick(document.id) },
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
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
