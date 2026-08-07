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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.feature.documents.domain.model.ScannedDocument

@Composable
fun DocumentsRoute(
    onScanClick: () -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentsScreen(
        uiState = uiState,
        onScanClick = onScanClick,
        onCreateDemoClick = viewModel::createDemoDocument,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DocumentsScreen(
    uiState: DocumentsUiState,
    onScanClick: () -> Unit,
    onCreateDemoClick: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("GScan") }) },
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

            uiState.documents.isEmpty() -> EmptyDocuments(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onCreateDemoClick = onCreateDemoClick,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.documents, key = { it.id }) { document ->
                    DocumentCard(document)
                }
            }
        }
    }
}

@Composable
private fun EmptyDocuments(
    modifier: Modifier,
    onCreateDemoClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Chưa có tài liệu", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        Text("Bắt đầu bằng camera hoặc tạo dữ liệu mẫu để kiểm tra luồng local-first.")
        Spacer(Modifier.size(20.dp))
        Button(onClick = onCreateDemoClick) {
            Text("Tạo tài liệu mẫu")
        }
    }
}

@Composable
private fun DocumentCard(document: ScannedDocument) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium)
                Text("${document.pageCount} trang · ${document.status.name}")
            }
        }
    }
}
