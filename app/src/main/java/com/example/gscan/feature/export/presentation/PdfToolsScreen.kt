package com.example.gscan.feature.export.presentation

import android.content.Intent
import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.export.domain.model.PdfQualityPreset
import java.io.File

@Composable
fun PdfToolsScreen(
    onBackClick: () -> Unit,
    onChooseDocument: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
    ) { uri -> uri?.let { viewModel.saveTo(it.toString()) } }

    BackHandler(enabled = uiState.isExporting || uiState.isSaving) {
        if (uiState.isExporting) viewModel.cancelExport()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PdfToolsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    PdfToolsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onChooseDocument = onChooseDocument,
        onPresetSelected = viewModel::selectPreset,
        onExport = viewModel::export,
        onCancelExport = viewModel::cancelExport,
        onSave = { uiState.exportedPdf?.let { saveLauncher.launch(it.displayName) } },
        onShare = {
            val exported = uiState.exportedPdf
            if (exported != null) {
                runCatching {
                    val exportFile = File(exported.filePath)
                    check(exportFile.isFile) { "Exported PDF is no longer available" }
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exportFile,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = PDF_MIME_TYPE
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        clipData = ClipData.newRawUri("GScan PDF", contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Chia sẻ PDF"))
                }.onFailure {
                    Toast.makeText(context, "Không thể mở ứng dụng chia sẻ.", Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

@Composable
private fun PdfToolsContent(
    uiState: PdfToolsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onChooseDocument: () -> Unit,
    onPresetSelected: (PdfQualityPreset) -> Unit,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GScanTopAppBar(
                title = "Xuất PDF",
                onBackClick = onBackClick,
                navigationEnabled = !uiState.isSaving,
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.details == null -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(uiState.errorMessage ?: "Chọn một tài liệu để xuất thành PDF.")
                Spacer(Modifier.height(20.dp))
                Button(onClick = onChooseDocument) { Text("Chọn tài liệu") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(uiState.details.document.title, style = MaterialTheme.typography.titleLarge)
                        Text("${uiState.details.pages.size} trang")
                    }
                }

                Text("Chất lượng PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PdfQualityPreset.entries.forEach { preset ->
                    QualityOption(
                        preset = preset,
                        selected = preset == uiState.preset,
                        enabled = !uiState.isExporting && !uiState.isSaving,
                        onClick = { onPresetSelected(preset) },
                    )
                }

                if (uiState.isExporting) {
                    val progress = if (uiState.totalPages > 0) {
                        uiState.completedPages.toFloat() / uiState.totalPages
                    } else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("Đang xử lý ${uiState.completedPages}/${uiState.totalPages} trang…")
                    Button(onClick = onCancelExport, modifier = Modifier.fillMaxWidth()) {
                        Text("Hủy xuất")
                    }
                } else {
                    Button(
                        onClick = onExport,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text(if (uiState.exportedPdf == null) "Tạo PDF" else "Tạo lại PDF")
                    }
                }

                uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                uiState.exportedPdf?.let { exported ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("PDF đã sẵn sàng", style = MaterialTheme.typography.titleMedium)
                            Text(exported.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilledTonalButton(
                                    onClick = onSave,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (uiState.isSaving) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                                    }
                                    Spacer(Modifier.size(8.dp))
                                    Text("Lưu")
                                }
                                FilledTonalButton(
                                    onClick = onShare,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Chia sẻ")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOption(
    preset: PdfQualityPreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(Modifier.padding(start = 8.dp)) {
                Text(preset.title, fontWeight = FontWeight.SemiBold)
                Text(preset.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val PdfQualityPreset.title: String
    get() = when (this) {
        PdfQualityPreset.SMALL -> "Dung lượng nhỏ"
        PdfQualityPreset.BALANCED -> "Cân bằng"
        PdfQualityPreset.HIGH -> "Chất lượng cao"
    }

private val PdfQualityPreset.description: String
    get() = when (this) {
        PdfQualityPreset.SMALL -> "Phù hợp gửi nhanh và tài liệu chủ yếu là chữ"
        PdfQualityPreset.BALANCED -> "Cân bằng độ rõ và dung lượng"
        PdfQualityPreset.HIGH -> "Ưu tiên ảnh rõ, cần nhiều bộ nhớ hơn"
    }

private const val PDF_MIME_TYPE = "application/pdf"
