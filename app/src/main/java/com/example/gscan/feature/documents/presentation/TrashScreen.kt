package com.example.gscan.feature.documents.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.documents.domain.model.ScannedDocument
import java.text.DateFormat
import java.util.Date

@Composable
fun TrashRoute(
    onBackClick: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TrashEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    TrashScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRestore = viewModel::restore,
        onPermanentlyDelete = viewModel::permanentlyDelete,
        onEmptyTrash = viewModel::emptyTrash,
    )
}

@Composable
private fun TrashScreen(
    uiState: TrashUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentlyDelete: (String) -> Unit,
    onEmptyTrash: () -> Unit,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    val pendingDocument = uiState.documents.firstOrNull { it.id == pendingDeleteId }

    if (pendingDocument != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Xóa vĩnh viễn?") },
            text = { Text("“${pendingDocument.title}” và toàn bộ ${pendingDocument.pageCount} trang sẽ không thể khôi phục.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    onPermanentlyDelete(pendingDocument.id)
                }) { Text("Xóa vĩnh viễn", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Hủy") } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Dọn thùng rác?") },
            text = { Text("Toàn bộ ${uiState.documents.size} tài liệu sẽ bị xóa vĩnh viễn và không thể khôi phục.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    onEmptyTrash()
                }) { Text("Xóa tất cả", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Hủy") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GScanTopAppBar(
                title = "Thùng rác",
                onBackClick = onBackClick,
                navigationEnabled = !uiState.isEmptying,
                actions = {
                    TextButton(
                        onClick = { confirmEmpty = true },
                        enabled = uiState.documents.isNotEmpty() &&
                            !uiState.isEmptying && uiState.processingDocumentId == null,
                    ) { Text("Dọn sạch") }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) { Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error) }

            uiState.documents.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Thùng rác trống", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(8.dp))
                Text("Tài liệu bạn xóa khỏi thư viện sẽ xuất hiện tại đây.")
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.documents, key = { it.id }) { document ->
                    TrashDocumentCard(
                        document = document,
                        isProcessing = uiState.processingDocumentId == document.id,
                        actionsEnabled = !uiState.isEmptying && uiState.processingDocumentId == null,
                        onRestore = { onRestore(document.id) },
                        onDelete = { pendingDeleteId = document.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashDocumentCard(
    document: ScannedDocument,
    isProcessing: Boolean,
    actionsEnabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(
                    "${document.pageCount} trang · Đã xóa ${document.deletedAtEpochMillis.toDeletedLabel()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRestore, enabled = actionsEnabled) {
                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Khôi phục ${document.title}")
                }
                IconButton(onClick = onDelete, enabled = actionsEnabled) {
                    Icon(
                        Icons.Rounded.DeleteForever,
                        contentDescription = "Xóa vĩnh viễn ${document.title}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun Long?.toDeletedLabel(): String = this?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
} ?: "gần đây"
