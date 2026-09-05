package com.example.gscan.feature.documents.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.core.designsystem.component.LocalFileImage
import com.example.gscan.feature.documents.domain.model.MAX_PAGES_PER_DOCUMENT
import com.example.gscan.feature.documents.domain.model.MAX_DOCUMENT_TITLE_LENGTH
import com.example.gscan.feature.documents.domain.model.ScannedPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun DocumentDetailRoute(
    onBackClick: () -> Unit,
    onExportClick: (String) -> Unit,
    onOcrClick: (String) -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val latestUiState by rememberUpdatedState(uiState)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PAGES_PER_DOCUMENT),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addPages(uris.map { it.toString() })
    }
    val singleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.addPages(listOf(it.toString())) }
    }

    BackHandler(enabled = uiState.isMutating) {
        // Không rời màn hình khi file và database đang được cập nhật.
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DocumentDetailEffect.ShowMessage -> launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is DocumentDetailEffect.PageMoved -> {
                    launch {
                        val position = withTimeoutOrNull(PAGE_MOVE_SCROLL_TIMEOUT_MILLIS) {
                            snapshotFlow {
                                latestUiState.details?.pages?.indexOfFirst { it.id == effect.pageId }
                            }.first { position -> position == effect.targetPosition }
                        }
                        if (position != null) {
                            listState.animateScrollToItem(position)
                        }
                    }
                }
            }
        }
    }

    DocumentDetailScreen(
        uiState = uiState,
        listState = listState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onExportClick = {
            uiState.details?.document?.id?.let(onExportClick)
        },
        onOcrClick = {
            uiState.details?.document?.id?.let(onOcrClick)
        },
        onAddPagesClick = {
            val remaining = MAX_PAGES_PER_DOCUMENT - (uiState.details?.pages?.size ?: 0)
            if (remaining > 0) {
                runCatching {
                    val request = PickVisualMediaRequest(
                        mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                        maxItems = maxOf(remaining, 2),
                        isOrderedSelection = true,
                    )
                    if (remaining == 1) {
                        singleImagePickerLauncher.launch(request)
                    } else {
                        imagePickerLauncher.launch(request)
                    }
                }.onFailure { viewModel.onAddPagesPickerFailure() }
            }
        },
        onCancelAddPages = viewModel::cancelAddingPages,
        onRenameDocument = viewModel::rename,
        onRotateClick = viewModel::rotateClockwise,
        onMoveClick = viewModel::movePage,
        onDeleteClick = viewModel::deletePage,
    )
}

private const val PAGE_MOVE_SCROLL_TIMEOUT_MILLIS = 2_000L

@Composable
private fun DocumentDetailScreen(
    uiState: DocumentDetailUiState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onExportClick: () -> Unit,
    onOcrClick: () -> Unit,
    onAddPagesClick: () -> Unit,
    onCancelAddPages: () -> Unit,
    onRenameDocument: (String) -> Unit,
    onRotateClick: (String) -> Unit,
    onMoveClick: (String, Int) -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    var pendingDeletePageId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeletePage = uiState.details?.pages?.firstOrNull { it.id == pendingDeletePageId }

    renameTitle?.let { title ->
        val normalizedTitle = title.trim()
        val canSave = normalizedTitle.isNotEmpty() && title.length <= MAX_DOCUMENT_TITLE_LENGTH
        val submitRename = {
            if (canSave) {
                renameTitle = null
                onRenameDocument(title)
            }
        }
        AlertDialog(
            onDismissRequest = { if (!uiState.isMutating) renameTitle = null },
            title = { Text("Đổi tên tài liệu") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { value ->
                        if (value.length <= MAX_DOCUMENT_TITLE_LENGTH) renameTitle = value
                    },
                    enabled = !uiState.isMutating,
                    singleLine = true,
                    label = { Text("Tên tài liệu") },
                    supportingText = { Text("${title.length}/$MAX_DOCUMENT_TITLE_LENGTH") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitRename() }),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = submitRename,
                    enabled = canSave && !uiState.isMutating,
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { renameTitle = null },
                    enabled = !uiState.isMutating,
                ) {
                    Text("Hủy")
                }
            },
        )
    }

    if (pendingDeletePage != null) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isMutating) pendingDeletePageId = null },
            title = { Text("Xóa trang ${pendingDeletePage.position + 1}?") },
            text = { Text("Trang sẽ bị xóa khỏi tài liệu và không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isMutating,
                    onClick = {
                        pendingDeletePageId = null
                        onDeleteClick(pendingDeletePage.id)
                    },
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isMutating,
                    onClick = { pendingDeletePageId = null },
                ) {
                    Text("Hủy")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GScanTopAppBar(
                title = uiState.details?.document?.title ?: "Tài liệu",
                onBackClick = onBackClick,
                navigationEnabled = !uiState.isMutating,
                onTitleClick = if (!uiState.isMutating) {
                    { uiState.details?.document?.title?.let { renameTitle = it } }
                } else {
                    null
                },
                actions = {
                    IconButton(
                        onClick = onAddPagesClick,
                        enabled = !uiState.isMutating &&
                            (uiState.details?.pages?.size ?: MAX_PAGES_PER_DOCUMENT) < MAX_PAGES_PER_DOCUMENT,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Thêm trang từ ảnh")
                    }
                    IconButton(
                        onClick = onOcrClick,
                        enabled = !uiState.isMutating && uiState.details?.pages?.isNotEmpty() == true,
                    ) {
                        Icon(Icons.Rounded.TextFields, contentDescription = "Nhận dạng văn bản")
                    }
                    IconButton(
                        onClick = onExportClick,
                        enabled = !uiState.isMutating && uiState.details?.pages?.isNotEmpty() == true,
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Xuất PDF")
                    }
                },
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

            uiState.details.pages.isEmpty() -> if (uiState.isAddingPages) {
                AddingPagesIndicator(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    onCancel = onCancelAddPages,
                )
            } else {
                DocumentDetailMessage(
                    message = "Tài liệu này chưa có trang nào.",
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uiState.isAddingPages) {
                    item(key = "adding-pages") {
                        AddingPagesIndicator(onCancel = onCancelAddPages)
                    }
                }
                itemsIndexed(uiState.details.pages, key = { _, page -> page.id }) { index, page ->
                    DocumentPage(
                        page = page,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.details.pages.lastIndex,
                        canDelete = uiState.details.pages.size > 1,
                        controlsEnabled = !uiState.isMutating,
                        onMoveUp = { onMoveClick(page.id, index - 1) },
                        onMoveDown = { onMoveClick(page.id, index + 1) },
                        onRotate = { onRotateClick(page.id) },
                        onDelete = { pendingDeletePageId = page.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddingPagesIndicator(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(
            text = "Đang thêm trang…",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onCancel) {
            Text("Hủy")
        }
    }
}

@Composable
private fun DocumentPage(
    page: ScannedPage,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    controlsEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
) {
    val swapsDimensions = page.rotationDegrees % 180 != 0
    val displayedWidth = if (swapsDimensions) page.height else page.width
    val displayedHeight = if (swapsDimensions) page.width else page.height
    val aspectRatio = if (displayedWidth > 0 && displayedHeight > 0) {
        (displayedWidth.toFloat() / displayedHeight.toFloat()).coerceIn(0.35f, 2.5f)
    } else {
        0.7f
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Trang ${page.position + 1}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onMoveUp, enabled = controlsEnabled && canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Đưa trang lên")
            }
            IconButton(onClick = onMoveDown, enabled = controlsEnabled && canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Đưa trang xuống")
            }
            IconButton(onClick = onRotate, enabled = controlsEnabled) {
                Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Xoay trang sang phải")
            }
            IconButton(onClick = onDelete, enabled = controlsEnabled && canDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Xóa trang")
            }
        }
        LocalFileImage(
            uri = page.sourceUri,
            contentDescription = "Trang ${page.position + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
            maxDecodeSizePx = 1200,
            rotationDegrees = page.rotationDegrees,
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
