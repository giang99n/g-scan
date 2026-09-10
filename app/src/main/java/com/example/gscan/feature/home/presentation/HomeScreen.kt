package com.example.gscan.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gscan.core.designsystem.theme.GScanTheme

enum class HomeFeature {
    SCANNER,
    DOCUMENTS,
    IMPORT,
    OCR,
    PDF_TOOLS,
    SIGNATURE,
    QR_BARCODE,
    SECURITY,
    BACKUP,
}

private data class QuickActionUiModel(
    val feature: HomeFeature,
    val title: String,
    val icon: ImageVector,
    val background: Color,
    val foreground: Color,
)

private data class ToolUiModel(
    val feature: HomeFeature,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
)

private val quickActions = listOf(
    QuickActionUiModel(HomeFeature.IMPORT, "Nhập file", Icons.Outlined.Image, Color(0xFFEFF4FF), Color(0xFF155EEF)),
    QuickActionUiModel(HomeFeature.DOCUMENTS, "Tài liệu", Icons.Outlined.Folder, Color(0xFFECFDF3), Color(0xFF039855)),
    QuickActionUiModel(HomeFeature.OCR, "Nhận dạng", Icons.Outlined.TextFields, Color(0xFFFFF6ED), Color(0xFFDC6803)),
    QuickActionUiModel(HomeFeature.PDF_TOOLS, "Công cụ PDF", Icons.Outlined.PictureAsPdf, Color(0xFFFFF1F3), Color(0xFFE31B54)),
)

private val tools = listOf(
    ToolUiModel(HomeFeature.SIGNATURE, "Chữ ký", "Ký và điền biểu mẫu", Icons.Outlined.Draw, Color(0xFF7F56D9)),
    ToolUiModel(HomeFeature.QR_BARCODE, "QR & barcode", "Quét mã trên thiết bị", Icons.Outlined.QrCodeScanner, Color(0xFF0E7090)),
    ToolUiModel(HomeFeature.SECURITY, "Bảo mật", "Khóa tài liệu riêng tư", Icons.Outlined.Lock, Color(0xFF344054)),
    ToolUiModel(HomeFeature.BACKUP, "Sao lưu", "Xuất và khôi phục dữ liệu", Icons.Outlined.CloudUpload, Color(0xFF155EEF)),
)

@Composable
fun HomeScreen(onFeatureClick: (HomeFeature) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { HomeBottomBar(onFeatureClick) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item { HomeHeader() }

            item {
                ScanHeroCard(
                    onClick = { onFeatureClick(HomeFeature.SCANNER) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            item {
                SectionTitle(
                    title = "Thao tác nhanh",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(quickActions, key = { it.feature }) { action ->
                        QuickActionCard(action) { onFeatureClick(action.feature) }
                    }
                }
            }

            item {
                RecentDocumentsCard(
                    onOpenDocuments = { onFeatureClick(HomeFeature.DOCUMENTS) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            item {
                SectionTitle(
                    title = "Khám phá công cụ",
                    action = "Xem tất cả",
                    onActionClick = { onFeatureClick(HomeFeature.PDF_TOOLS) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            items(tools.chunked(2)) { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowTools.forEach { tool ->
                        ToolCard(
                            item = tool,
                            onClick = { onFeatureClick(tool.feature) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                PrivacyBanner(modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "G",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = "GScan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tài liệu của bạn, gọn trong một chạm",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = {}) {
            Icon(Icons.Rounded.NotificationsNone, contentDescription = "Thông báo")
        }
    }
}

@Composable
private fun ScanHeroCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroGradient = Brush.linearGradient(listOf(Color(0xFF155EEF), Color(0xFF6941C6)))
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.background(heroGradient).padding(horizontal = 22.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                shape = RoundedCornerShape(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(34.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 18.dp)) {
                Text(
                    text = "Scan tài liệu mới",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Tự động căn chỉnh, cắt và làm rõ",
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Surface(color = Color.White, contentColor = Color(0xFF155EEF), shape = CircleShape) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.padding(6.dp))
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    item: QuickActionUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.size(width = 112.dp, height = 116.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(color = item.background, contentColor = item.foreground, shape = RoundedCornerShape(13.dp)) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(23.dp))
            }
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RecentDocumentsCard(
    onOpenDocuments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenDocuments),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.padding(14.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Tài liệu gần đây", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Mở thư viện để xem và quản lý bản scan",
                    modifier = Modifier.padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolCard(
    item: ToolUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(138.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Icon(item.icon, contentDescription = null, tint = item.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(14.dp))
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = item.description,
                modifier = Modifier.padding(top = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PrivacyBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFECFDF3),
        contentColor = Color(0xFF027A48),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = "Riêng tư mặc định · Ưu tiên xử lý và lưu tài liệu trên thiết bị.",
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onActionClick: () -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action != null) {
            Text(
                text = action,
                modifier = Modifier.clickable(onClick = onActionClick).padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeBottomBar(onFeatureClick: (HomeFeature) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            label = { Text("Trang chủ") },
        )
        NavigationBarItem(
            selected = false,
            onClick = { onFeatureClick(HomeFeature.DOCUMENTS) },
            icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
            label = { Text("Tài liệu") },
        )
        NavigationBarItem(
            selected = false,
            onClick = { onFeatureClick(HomeFeature.PDF_TOOLS) },
            icon = { Icon(Icons.Rounded.GridView, contentDescription = null) },
            label = { Text("Công cụ") },
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    GScanTheme(darkTheme = false) {
        HomeScreen(onFeatureClick = {})
    }
}
