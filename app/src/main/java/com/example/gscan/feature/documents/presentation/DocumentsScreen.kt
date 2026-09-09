package com.example.gscan.feature.documents.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.core.designsystem.component.LocalFileImage
import com.example.gscan.feature.documents.domain.model.DocumentFolder
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import com.example.gscan.feature.documents.domain.model.DocumentTag
import com.example.gscan.feature.documents.domain.model.ScannedDocument

@Composable
fun DocumentsRoute(
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onDocumentClick: (String) -> Unit,
    onComposeClick: (() -> Unit)? = null,
    onTrashClick: (() -> Unit)? = null,
    allowDocumentDuplication: Boolean = false,
    title: String = "Tài liệu",
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is DocumentsEffect.ShowMessage) snackbarHostState.showSnackbar(effect.message)
        }
    }
    DocumentsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onScanClick = onScanClick,
        onDocumentClick = onDocumentClick,
        onDeleteDocument = viewModel::delete,
        onDuplicateDocument = viewModel::duplicate,
        onSearchQueryChange = viewModel::updateSearchQuery,
        title = title,
        onComposeClick = onComposeClick,
        onTrashClick = onTrashClick,
        organizationEnabled = allowDocumentDuplication,
        onSelectFolder = viewModel::selectFolder,
        onSelectTag = viewModel::selectTag,
        onToggleFavoriteFilter = viewModel::toggleFavoriteFilter,
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAllVisible,
        onFavoriteSelected = viewModel::favoriteSelected,
        onMoveSelected = viewModel::moveSelected,
        onUpdateTags = viewModel::updateTagsForSelected,
        onTrashSelected = viewModel::trashSelected,
        onCreateFolder = viewModel::createFolder,
        onRenameFolder = viewModel::renameFolder,
        onDeleteFolder = viewModel::deleteFolder,
        onCreateTag = viewModel::createTag,
        onRenameTag = viewModel::renameTag,
        onDeleteTag = viewModel::deleteTag,
        onClearFilters = viewModel::clearFilters,
        onClearOrganizationError = viewModel::clearOrganizationError,
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
    onDuplicateDocument: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    title: String,
    onComposeClick: (() -> Unit)?,
    onTrashClick: (() -> Unit)?,
    organizationEnabled: Boolean,
    onSelectFolder: (String?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onToggleFavoriteFilter: () -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onFavoriteSelected: (Boolean) -> Unit,
    onMoveSelected: (String?) -> Unit,
    onUpdateTags: (Set<String>, Set<String>) -> Unit,
    onTrashSelected: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (String, String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onClearFilters: () -> Unit,
    onClearOrganizationError: () -> Unit,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showMove by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var showTagManager by rememberSaveable { mutableStateOf(false) }
    var confirmBatchTrash by rememberSaveable { mutableStateOf(false) }
    var showLibraryMenu by rememberSaveable { mutableStateOf(false) }
    val selectedDocuments = uiState.documents.filter { it.id in uiState.selectedDocumentIds }
    val selectionMode = organizationEnabled && uiState.selectedDocumentIds.isNotEmpty()
    val allSelectedFavorite = selectedDocuments.isNotEmpty() && selectedDocuments.all { it.isFavorite }
    BackHandler(enabled = selectionMode, onBack = onClearSelection)

    pendingDeleteId?.let { id ->
        uiState.documents.firstOrNull { it.id == id }?.let { document ->
            ConfirmDialog(
                title = "Đưa vào thùng rác?",
                message = "“${document.title}” và ${document.pageCount} trang sẽ được ẩn khỏi thư viện. Bạn có thể khôi phục sau.",
                confirmLabel = "Đưa vào thùng rác",
                onDismiss = { pendingDeleteId = null },
                onConfirm = { pendingDeleteId = null; onDeleteDocument(id) },
            )
        }
    }
    if (confirmBatchTrash) ConfirmDialog(
        title = "Đưa ${uiState.selectedDocumentIds.size} tài liệu vào thùng rác?",
        message = "Bạn có thể khôi phục các tài liệu này từ Thùng rác.",
        confirmLabel = "Đưa vào thùng rác",
        onDismiss = { confirmBatchTrash = false },
        onConfirm = { confirmBatchTrash = false; onTrashSelected() },
    )
    if (showMove) MoveFolderDialog(
        folders = uiState.folders,
        initialFolderKey = selectedDocuments.map { it.folderId ?: UNFILED_FOLDER_FILTER_ID }.distinct().singleOrNull(),
        onDismiss = { showMove = false },
        onApply = { showMove = false; onMoveSelected(it) },
    )
    if (showTags) TagAssignmentDialog(
        tags = uiState.tags,
        allSelectedTagIds = uiState.tags.map { it.id }.filter { tagId -> selectedDocuments.all { document -> document.tags.any { it.id == tagId } } }.toSet(),
        partiallySelectedTagIds = uiState.tags.map { it.id }.filter { tagId -> selectedDocuments.any { document -> document.tags.any { it.id == tagId } } }.toSet(),
        onDismiss = { showTags = false },
        onApply = { added, removed -> showTags = false; onUpdateTags(added, removed) },
    )
    if (showFolders) OrganizationManagerDialog(
        title = "Quản lý folder",
        itemLabel = "folder",
        items = uiState.folders.map { it.id to it.name },
        onDismiss = { showFolders = false; onClearOrganizationError() },
        onCreate = onCreateFolder,
        onRename = onRenameFolder,
        onDelete = onDeleteFolder,
        isBusy = uiState.isOrganizing,
        errorMessage = uiState.organizationError,
        successVersion = uiState.organizationSuccessVersion,
        onClearError = onClearOrganizationError,
    )
    if (showTagManager) OrganizationManagerDialog(
        title = "Quản lý tag",
        itemLabel = "tag",
        items = uiState.tags.map { it.id to it.name },
        onDismiss = { showTagManager = false; onClearOrganizationError() },
        onCreate = onCreateTag,
        onRename = onRenameTag,
        onDelete = onDeleteTag,
        isBusy = uiState.isOrganizing,
        errorMessage = uiState.organizationError,
        successVersion = uiState.organizationSuccessVersion,
        onClearError = onClearOrganizationError,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GScanTopAppBar(
                title = if (selectionMode) "Đã chọn ${uiState.selectedDocumentIds.size}" else title,
                onBackClick = if (selectionMode) onClearSelection else onBackClick,
                actions = {
                    if (selectionMode) {
                        TextButton(onClick = onSelectAll, enabled = !uiState.isOrganizing) { Text("Chọn tất cả") }
                    } else {
                        if (organizationEnabled) {
                            Box {
                                IconButton(onClick = { showLibraryMenu = true }) { Icon(Icons.Rounded.MoreVert, "Tùy chọn thư viện") }
                                DropdownMenu(expanded = showLibraryMenu, onDismissRequest = { showLibraryMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Quản lý folder") },
                                        leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                                        onClick = { showLibraryMenu = false; onClearOrganizationError(); showFolders = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Quản lý tag") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                                        onClick = { showLibraryMenu = false; onClearOrganizationError(); showTagManager = true },
                                    )
                                    if (onComposeClick != null) DropdownMenuItem(
                                        text = { Text("Gộp / trích xuất trang") },
                                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                        onClick = { showLibraryMenu = false; onComposeClick() },
                                    )
                                    if (onTrashClick != null) DropdownMenuItem(
                                        text = { Text("Mở thùng rác") },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                                        onClick = { showLibraryMenu = false; onTrashClick() },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selectionMode) BatchActionBar(
                enabled = !uiState.isOrganizing,
                favorite = !allSelectedFavorite,
                onFavorite = { onFavoriteSelected(!allSelectedFavorite) },
                onMove = { showMove = true },
                onTags = {
                    if (uiState.tags.isEmpty()) {
                        onClearOrganizationError()
                        showTagManager = true
                    } else {
                        showTags = true
                    }
                },
                onDelete = { confirmBatchTrash = true },
            )
        },
        floatingActionButton = {
            if (!selectionMode) FloatingActionButton(onClick = onScanClick) { Text("Scan", modifier = Modifier.padding(horizontal = 16.dp)) }
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            DocumentSearchField(uiState.searchQuery, onSearchQueryChange, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            if (organizationEnabled) OrganizationFilters(uiState, onSelectFolder, onSelectTag, onToggleFavoriteFilter)
            if (uiState.folderLoadFailed || uiState.tagLoadFailed) {
                Text(
                    text = "Không thể tải đầy đủ folder hoặc tag. Hãy mở lại màn hình.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                uiState.errorMessage != null -> ErrorDocuments(Modifier.fillMaxWidth().weight(1f), uiState.errorMessage)
                uiState.documents.isEmpty() && (uiState.searchQuery.isNotBlank() || uiState.selectedFolderId != null || uiState.selectedTagId != null || uiState.favoriteOnly) -> NoMatchingDocuments(Modifier.fillMaxWidth().weight(1f), onClearFilters)
                uiState.documents.isEmpty() -> EmptyDocuments(Modifier.fillMaxWidth().weight(1f), onScanClick)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.documents, key = { it.id }) { document ->
                        val processing = uiState.deletingDocumentId == document.id || uiState.duplicatingDocumentId == document.id || uiState.isOrganizing
                        DocumentCard(
                            document = document,
                            selected = document.id in uiState.selectedDocumentIds,
                            selectionMode = selectionMode,
                            isProcessing = processing,
                            duplicateEnabled = organizationEnabled && document.pageCount > 0,
                            onClick = { if (selectionMode) onToggleSelection(document.id) else onDocumentClick(document.id) },
                            onLongClick = { if (organizationEnabled) onToggleSelection(document.id) },
                            onFavoriteClick = { onToggleFavorite(document.id, !document.isFavorite) },
                            onDuplicateClick = { onDuplicateDocument(document.id) },
                            onDeleteClick = { pendingDeleteId = document.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationFilters(
    state: DocumentsUiState,
    onFolder: (String?) -> Unit,
    onTag: (String?) -> Unit,
    onFavorite: () -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(state.selectedFolderId == null, { onFolder(null) }, { Text("Mọi folder") }) }
        item { FilterChip(state.favoriteOnly, onFavorite, { Text("Yêu thích") }, leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) }) }
        item { FilterChip(state.selectedFolderId == UNFILED_FOLDER_FILTER_ID, { onFolder(UNFILED_FOLDER_FILTER_ID) }, { Text("Chưa phân loại") }) }
        items(state.folders, key = { "folder-${it.id}" }) { folder ->
            FilterChip(state.selectedFolderId == folder.id, { onFolder(folder.id) }, { Text(folder.name) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) })
        }
    }
    if (state.tags.isNotEmpty()) LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(state.selectedTagId == null, { onTag(null) }, { Text("Mọi tag") }) }
        items(state.tags, key = { "tag-${it.id}" }) { tag -> FilterChip(state.selectedTagId == tag.id, { onTag(tag.id) }, { Text("#${tag.name}") }) }
    }
}

@Composable
private fun BatchActionBar(enabled: Boolean, favorite: Boolean, onFavorite: () -> Unit, onMove: () -> Unit, onTags: () -> Unit, onDelete: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            BatchButton(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (favorite) "Yêu thích" else "Bỏ thích", enabled, onFavorite)
            BatchButton(Icons.AutoMirrored.Rounded.DriveFileMove, "Di chuyển", enabled, onMove)
            BatchButton(Icons.AutoMirrored.Rounded.Label, "Gắn tag", enabled, onTags)
            BatchButton(Icons.Rounded.Delete, "Xóa", enabled, onDelete)
        }
    }
}

@Composable
private fun BatchButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label, style = MaterialTheme.typography.labelSmall) } }
}

@Composable
private fun MoveFolderDialog(
    folders: List<DocumentFolder>,
    initialFolderKey: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit,
) {
    var selectedKey by rememberSaveable { mutableStateOf(initialFolderKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Di chuyển tới folder") },
        text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
            item { ChoiceRow("Chưa phân loại", selectedKey == UNFILED_FOLDER_FILTER_ID) { selectedKey = UNFILED_FOLDER_FILTER_ID } }
            items(folders, key = { it.id }) { folder -> ChoiceRow(folder.name, selectedKey == folder.id) { selectedKey = folder.id } }
        } },
        confirmButton = {
            TextButton(
                onClick = { onApply(selectedKey.takeUnless { it == UNFILED_FOLDER_FILTER_ID }) },
                enabled = selectedKey != null,
            ) { Text("Di chuyển") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TagAssignmentDialog(
    tags: List<DocumentTag>,
    allSelectedTagIds: Set<String>,
    partiallySelectedTagIds: Set<String>,
    onDismiss: () -> Unit,
    onApply: (addedTagIds: Set<String>, removedTagIds: Set<String>) -> Unit,
) {
    var overrides by remember(tags, allSelectedTagIds, partiallySelectedTagIds) {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đặt tag cho tài liệu đã chọn") },
        text = { if (tags.isEmpty()) Text("Chưa có tag. Hãy tạo tag trong nút quản lý ở thanh trên.") else LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(tags, key = { it.id }) { tag ->
                val state = when (overrides[tag.id]) {
                    true -> ToggleableState.On
                    false -> ToggleableState.Off
                    null -> when {
                        tag.id in allSelectedTagIds -> ToggleableState.On
                        tag.id in partiallySelectedTagIds -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    }
                }
                val toggle = {
                    overrides = overrides + (tag.id to (state != ToggleableState.On))
                }
                Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(state = state, onClick = toggle)
                    Text(tag.name, Modifier.padding(start = 8.dp))
                }
            }
        } },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        overrides.filterValues { it }.keys,
                        overrides.filterValues { !it }.keys,
                    )
                },
                enabled = tags.isNotEmpty() && overrides.isNotEmpty(),
            ) { Text("Áp dụng") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun OrganizationManagerDialog(
    title: String,
    itemLabel: String,
    items: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    isBusy: Boolean,
    errorMessage: String?,
    successVersion: Int,
    onClearError: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    val initialSuccessVersion = remember { successVersion }
    LaunchedEffect(successVersion) {
        if (successVersion != initialSuccessVersion) {
            input = ""
            editingId = null
        }
    }
    deleteItem?.let { item -> ConfirmDialog(
        title = "Xóa $itemLabel “${item.second}”?",
        message = if (itemLabel == "folder") "Tài liệu trong folder sẽ về Chưa phân loại." else "Tag sẽ được gỡ khỏi mọi tài liệu.",
        confirmLabel = "Xóa",
        onDismiss = { deleteItem = null },
        onConfirm = { deleteItem = null; onDelete(item.first) },
    ) }
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(title) },
        text = { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(40); onClearError() },
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                    singleLine = true,
                    label = { Text("Tên $itemLabel") },
                    supportingText = errorMessage?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error) } },
                    isError = errorMessage != null,
                )
                TextButton(
                    onClick = {
                        val name = input.trim()
                        editingId?.let { onRename(it, name) } ?: onCreate(name)
                    },
                    enabled = input.isNotBlank() && !isBusy,
                ) {
                    if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (editingId == null) "Thêm" else "Lưu")
                }
            }
            LazyColumn(Modifier.heightIn(max = 330.dp)) {
                items(items, key = { it.first }) { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.second, Modifier.weight(1f))
                        IconButton(onClick = { onClearError(); editingId = item.first; input = item.second }, enabled = !isBusy) { Icon(Icons.Rounded.Edit, "Đổi tên") }
                        IconButton(onClick = { deleteItem = item }, enabled = !isBusy) { Icon(Icons.Rounded.Delete, "Xóa") }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Đóng") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } })
}

@Composable
private fun DocumentSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(query, onQueryChange, modifier, singleLine = true, label = { Text("Tìm tài liệu") }, placeholder = { Text("Tên, tag hoặc nội dung OCR") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = if (query.isNotEmpty()) {{ IconButton({ onQueryChange("") }) { Icon(Icons.Rounded.Clear, "Xóa tìm kiếm") } }} else null)
}

@Composable
private fun EmptyDocuments(modifier: Modifier, onScanClick: () -> Unit) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Chưa có tài liệu", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.size(8.dp)); Text("Scan tài liệu đầu tiên để lưu an toàn ngay trên thiết bị."); Spacer(Modifier.size(20.dp)); Button(onClick = onScanClick) { Text("Bắt đầu scan") } }
}

@Composable
private fun NoMatchingDocuments(modifier: Modifier, onClearFilters: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text("Không có tài liệu phù hợp", style = MaterialTheme.typography.titleMedium)
        Text(
            "Thử từ khóa khác hoặc xóa các bộ lọc đang bật.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onClearFilters) { Text("Xóa bộ lọc") }
    }
}

@Composable
private fun ErrorDocuments(modifier: Modifier, message: String) { Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(
    document: ScannedDocument,
    selected: Boolean,
    selectionMode: Boolean,
    isProcessing: Boolean,
    duplicateEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(enabled = !isProcessing, onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) Checkbox(selected, { onClick() })
            DocumentThumbnail(document.thumbnailUri, document.title, document.thumbnailRotationDegrees, document.thumbnailSignatureInk)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = document.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${document.pageCount} trang · ${document.status.toLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                document.folderName?.let { Text("📁 $it", style = MaterialTheme.typography.labelSmall) }
                if (document.tags.isNotEmpty()) Text(document.tags.joinToString("  ") { "#${it.name}" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (isProcessing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else if (!selectionMode) {
                IconButton(onClick = onFavoriteClick) { Icon(if (document.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Yêu thích", tint = if (document.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    IconButton(onClick = { showActions = true }) { Icon(Icons.Rounded.MoreVert, "Tùy chọn tài liệu") }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        if (duplicateEnabled) DropdownMenuItem(
                            text = { Text("Nhân bản") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                            onClick = { showActions = false; onDuplicateClick() },
                        )
                        DropdownMenuItem(
                            text = { Text("Đưa vào thùng rác", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showActions = false; onDeleteClick() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentThumbnail(uri: String?, title: String, rotationDegrees: Int, signatureInk: String) {
    Card(Modifier.size(width = 64.dp, height = 82.dp).clip(RoundedCornerShape(10.dp)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        LocalFileImage(uri, "Trang đầu của $title", Modifier.fillMaxSize(), THUMBNAIL_MAX_SIZE_PX, ContentScale.Crop, rotationDegrees, signatureInk)
    }
}

private fun DocumentStatus.toLabel(): String = when (this) { DocumentStatus.DRAFT -> "Bản nháp"; DocumentStatus.PROCESSING -> "Đang xử lý"; DocumentStatus.READY -> "Sẵn sàng"; DocumentStatus.FAILED -> "Có lỗi" }
private const val THUMBNAIL_MAX_SIZE_PX = 256
