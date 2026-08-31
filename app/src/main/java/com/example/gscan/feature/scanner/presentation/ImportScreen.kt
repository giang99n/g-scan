package com.example.gscan.feature.scanner.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.scanner.domain.usecase.MAX_DOCUMENT_PAGES

@Composable
fun ImportScreen(
    onBackClick: () -> Unit,
    onDocumentSaved: (String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_DOCUMENT_PAGES),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris.map { uri -> uri.toString() })
        }
    }

    BackHandler(enabled = uiState.isSaving) {
        // Không rời màn hình trong lúc commit file + database.
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportEffect.DocumentSaved -> onDocumentSaved(effect.documentId)
            }
        }
    }

    ImportContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onPickImages = {
            viewModel.clearError()
            runCatching {
                pickerLauncher.launch(
                    PickVisualMediaRequest(
                        mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                        maxItems = MAX_DOCUMENT_PAGES,
                        isOrderedSelection = true,
                    ),
                )
            }.onFailure {
                viewModel.onPickerFailure()
            }
        },
        onCancelImport = viewModel::cancelImport,
    )
}

@Composable
private fun ImportContent(
    uiState: ImportUiState,
    onBackClick: () -> Unit,
    onPickImages: () -> Unit,
    onCancelImport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GScanTopAppBar(
                title = "Nhập ảnh",
                onBackClick = onBackClick,
                navigationEnabled = !uiState.isSaving,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.size(128.dp),
                shape = RoundedCornerShape(36.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(58.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = if (uiState.isSaving) "Đang nhập ảnh…" else "Tạo tài liệu từ thư viện ảnh",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (uiState.isSaving) {
                    "GScan đang sao chép ảnh vào vùng lưu trữ riêng và tạo tài liệu."
                } else {
                    "Chọn nhiều ảnh; trình chọn hỗ trợ sẽ cho phép sắp thứ tự trang trước khi lưu."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            ImportBenefit(Icons.Rounded.Collections, "Tối đa $MAX_DOCUMENT_PAGES ảnh mỗi tài liệu")
            ImportBenefit(Icons.Rounded.Lock, "Không cần quyền truy cập toàn bộ thư viện")
            ImportBenefit(Icons.Rounded.Image, "Hỗ trợ các định dạng ảnh thiết bị có thể đọc")

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = if (uiState.isSaving) onCancelImport else onPickImages,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    text = if (uiState.isSaving) "Hủy nhập" else "Chọn ảnh",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ImportBenefit(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
