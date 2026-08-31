package com.example.gscan.feature.documents.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.core.designsystem.component.LocalFileImage
import com.example.gscan.feature.documents.domain.model.ScannedPage

@Composable
fun DocumentDetailRoute(
    onBackClick: () -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentDetailScreen(uiState = uiState, onBackClick = onBackClick)
}

@Composable
private fun DocumentDetailScreen(
    uiState: DocumentDetailUiState,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            GScanTopAppBar(
                title = uiState.details?.document?.title ?: "Tài liệu",
                onBackClick = onBackClick,
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

            uiState.errorMessage != null -> DocumentDetailMessage(
                message = uiState.errorMessage,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            uiState.details == null -> DocumentDetailMessage(
                message = "Tài liệu không còn tồn tại.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            uiState.details.pages.isEmpty() -> DocumentDetailMessage(
                message = "Tài liệu này chưa có trang nào.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.details.pages, key = { it.id }) { page ->
                    DocumentPage(page)
                }
            }
        }
    }
}

@Composable
private fun DocumentPage(page: ScannedPage) {
    val aspectRatio = if (page.width > 0 && page.height > 0) {
        (page.width.toFloat() / page.height.toFloat()).coerceIn(0.35f, 2.5f)
    } else {
        0.7f
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = "Trang ${page.position + 1}",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LocalFileImage(
            uri = page.sourceUri,
            contentDescription = "Trang ${page.position + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
            maxDecodeSizePx = 1200,
        )
    }
}

@Composable
private fun DocumentDetailMessage(
    message: String,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
