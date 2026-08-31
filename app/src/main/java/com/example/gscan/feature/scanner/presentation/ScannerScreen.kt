package com.example.gscan.feature.scanner.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerScreen(
    onBackClick: () -> Unit,
    onDocumentSaved: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val isCompositionActive = remember { AtomicBoolean(true) }

    DisposableEffect(Unit) {
        isCompositionActive.set(true)
        onDispose {
            isCompositionActive.set(false)
            viewModel.finishScannerPreparation()
        }
    }

    BackHandler(enabled = uiState.isSaving) {
        // Giữ màn hình trong lúc commit file + database; repository vẫn xử lý cancellation an toàn.
    }

    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(MAX_SCAN_PAGES)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    val scannerLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { activityResult ->
        viewModel.finishScannerPreparation()
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val pageUris = result?.pages.orEmpty().map { page -> page.imageUri.toString() }
            if (pageUris.isEmpty()) {
                viewModel.onScannerFailure("Không nhận được trang nào từ trình scan.")
            } else {
                viewModel.saveScan(pageUris)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScannerEffect.DocumentSaved -> onDocumentSaved(effect.documentId)
            }
        }
    }

    ScannerContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onStartScan = {
            if (activity == null) {
                viewModel.onScannerFailure("Không thể mở trình scan trên màn hình hiện tại.")
                return@ScannerContent
            }
            if (!viewModel.beginScannerPreparation()) return@ScannerContent
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    if (!isCompositionActive.get()) return@addOnSuccessListener
                    runCatching {
                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }.onFailure { error ->
                        viewModel.onScannerFailure(error.toScannerMessage())
                    }
                }
                .addOnFailureListener { error ->
                    if (isCompositionActive.get()) {
                        viewModel.onScannerFailure(error.toScannerMessage())
                    }
                }
        },
    )
}

@Composable
private fun ScannerContent(
    uiState: ScannerUiState,
    onBackClick: () -> Unit,
    onStartScan: () -> Unit,
) {
    val isBusy = uiState.isSaving || uiState.isPreparingScanner
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GScanTopAppBar(
                title = "Scan tài liệu",
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(58.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = if (uiState.isSaving) "Đang lưu tài liệu…" else "Scan rõ nét, lưu ngay trên máy",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (uiState.isSaving) {
                    "GScan đang sao chép các trang vào vùng lưu trữ an toàn."
                } else {
                    "Tự động nhận diện viền, chỉnh phối cảnh, xoay và áp dụng bộ lọc bằng ML Kit."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            ScannerBenefit(Icons.Rounded.Collections, "Tối đa $MAX_SCAN_PAGES trang mỗi tài liệu")
            ScannerBenefit(Icons.Rounded.CheckCircle, "Không cần cấp quyền camera cho GScan")
            ScannerBenefit(Icons.Rounded.CloudDownload, "Lần đầu có thể cần tải module từ Google Play services")

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
                onClick = onStartScan,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    text = when {
                        uiState.isSaving -> "Đang lưu…"
                        uiState.isPreparingScanner -> "Đang mở trình scan…"
                        else -> "Bắt đầu scan"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ScannerBenefit(
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Throwable.toScannerMessage(): String = when {
    this is MlKitException && errorCode == MlKitException.UNSUPPORTED ->
        "Thiết bị không hỗ trợ ML Kit Document Scanner (cần tối thiểu khoảng 1,7 GB RAM)."
    this is MlKitException && errorCode == MlKitException.UNAVAILABLE ->
        "Module scan chưa sẵn sàng. Hãy kiểm tra Google Play services và kết nối mạng rồi thử lại."
    else -> "Không thể mở trình scan. Vui lòng thử lại."
}

private const val MAX_SCAN_PAGES = 100
