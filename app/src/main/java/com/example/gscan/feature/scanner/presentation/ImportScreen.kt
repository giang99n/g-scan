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
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    initialPdfUri: String? = null,
    onInitialPdfConsumed: () -> Unit = {},
    onBackClick: () -> Unit,
    onDocumentSaved: (String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_DOCUMENT_PAGES),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris.map { uri -> uri.toString() })
        }
    }
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importPdf(it.toString()) }
    }

    BackHandler(enabled = uiState.isSaving) {
        // Không rời màn hình trong lúc commit file + database.
    }

    LaunchedEffect(initialPdfUri, uiState.isSaving) {
        if (initialPdfUri != null && !uiState.isSaving) {
            viewModel.importPdf(initialPdfUri)
            onInitialPdfConsumed()
        }
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
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(
                        mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                        maxItems = MAX_DOCUMENT_PAGES,
                        isOrderedSelection = true,
                    ),
                )
            }.onFailure {
                viewModel.onPickerFailure(SaveInputKind.IMPORT)
            }
        },
        onPickPdf = {
            viewModel.clearError()
            runCatching {
                pdfPickerLauncher.launch(arrayOf("application/pdf", "application/x-pdf"))
            }.onFailure {
                viewModel.onPickerFailure(SaveInputKind.PDF)
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
    onPickPdf: () -> Unit,
    onCancelImport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GScanTopAppBar(
                title = "Nhập tài liệu",
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
                        imageVector = if (uiState.inputKind == SaveInputKind.PDF) {
                            Icons.Rounded.PictureAsPdf
                        } else {
                            Icons.Rounded.PhotoLibrary
                        },
                        contentDescription = null,
                        modifier = Modifier.size(58.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = when {
                    !uiState.isSaving -> "Tạo tài liệu từ ảnh hoặc PDF"
                    uiState.inputKind == SaveInputKind.PDF -> "Đang nhập PDF…"
                    else -> "Đang nhập ảnh…"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (uiState.isSaving) {
                    if (uiState.inputKind == SaveInputKind.PDF) {
                        "GScan đang chuyển từng trang PDF thành tài liệu trên thiết bị."
                    } else {
                        "GScan đang sao chép ảnh vào vùng lưu trữ riêng và tạo tài liệu."
                    }
                } else {
                    "Chọn nhiều ảnh hoặc một PDF; dữ liệu được xử lý và lưu hoàn toàn trên thiết bị."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            ImportBenefit(Icons.Rounded.Collections, "Tối đa $MAX_DOCUMENT_PAGES trang mỗi tài liệu")
            ImportBenefit(Icons.Rounded.Lock, "Không cần quyền truy cập toàn bộ bộ nhớ")
            ImportBenefit(Icons.Rounded.Image, "Hỗ trợ ảnh và PDF không có mật khẩu")

            if (uiState.isSaving && uiState.totalPages > 0) {
                Spacer(Modifier.height(24.dp))
                val progress = uiState.completedPages.toFloat() / uiState.totalPages.toFloat()
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Đã xử lý ${uiState.completedPages}/${uiState.totalPages} trang",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

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
            if (uiState.isSaving) {
                Button(
                    onClick = onCancelImport,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Hủy nhập", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onPickImages,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text("Chọn ảnh", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPickPdf,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text("Chọn PDF", fontWeight = FontWeight.SemiBold)
                }
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
