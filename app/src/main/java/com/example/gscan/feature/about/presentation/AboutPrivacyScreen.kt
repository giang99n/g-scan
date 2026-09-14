package com.example.gscan.feature.about.presentation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.gscan.BuildConfig
import com.example.gscan.core.designsystem.component.GScanTopAppBar

private const val SUPPORT_EMAIL = "giang99n@gmail.com"

@Composable
fun AboutPrivacyScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = { GScanTopAppBar("Giới thiệu & quyền riêng tư", onBackClick) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AboutCard()

            Text(
                text = "Chính sách quyền riêng tư",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Có hiệu lực từ ngày 14 tháng 9 năm 2026",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "Chính sách này mô tả cách GScan (com.aloalo.gscan), do Giang Nguyen phát hành, xử lý dữ liệu khi bạn sử dụng ứng dụng.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrivacyItem(
                icon = Icons.Rounded.Storage,
                title = "Dữ liệu được xử lý",
                description = "GScan xử lý tài liệu và ảnh bạn chọn hoặc quét, tên tài liệu, thư mục, tag, thứ tự và góc xoay trang, kết quả OCR, chữ ký bạn tạo, lịch sử QR/barcode và cài đặt khóa ứng dụng để cung cấp các chức năng bạn yêu cầu.",
            )
            PrivacyItem(
                icon = Icons.Rounded.CloudOff,
                title = "Lưu trữ và truyền dữ liệu",
                description = "Dữ liệu được lưu trong vùng riêng của ứng dụng trên thiết bị. GScan không yêu cầu tài khoản, không có quảng cáo, không vận hành máy chủ và không tự tải nội dung tài liệu lên máy chủ của nhà phát hành.",
            )
            PrivacyItem(
                icon = Icons.Rounded.Lock,
                title = "Chia sẻ và bảo mật",
                description = "File chỉ được chuyển ra ngoài khi bạn chủ động lưu, xuất hoặc chia sẻ. Bản sao bên ngoài chịu chính sách của ứng dụng hoặc nơi nhận. App Lock bảo vệ giao diện, nhưng không mã hóa cơ sở dữ liệu hay file tài liệu.",
            )
            PrivacyItem(
                icon = Icons.Rounded.DeleteOutline,
                title = "Lưu giữ và xóa",
                description = "Dữ liệu được giữ trên thiết bị tới khi bạn xóa. Bạn có thể chuyển tài liệu vào thùng rác rồi xóa vĩnh viễn. Gỡ ứng dụng sẽ xóa dữ liệu nội bộ theo cơ chế Android, nhưng không xóa các bản đã xuất hoặc chia sẻ.",
            )

            Text(
                text = "Dịch vụ bên thứ ba",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Một số chức năng quét, OCR và đọc mã sử dụng Google ML Kit hoặc Google Play services. Việc xử lý hình ảnh được thiết kế để diễn ra trên thiết bị; thành phần cần thiết có thể được tải xuống và Google có thể xử lý dữ liệu kỹ thuật theo chính sách của họ.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Trẻ em và thay đổi chính sách",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "GScan không chủ đích thu thập dữ liệu cá nhân của trẻ em và không cung cấp tài khoản hay nội dung hướng tới trẻ em. Nếu cách xử lý dữ liệu thay đổi, nội dung chính sách và ngày có hiệu lực trong ứng dụng sẽ được cập nhật.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(
                onClick = { context.emailSupport() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.MailOutline, contentDescription = null)
                Text("Hỗ trợ: $SUPPORT_EMAIL", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text("GScan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Máy scan và bộ công cụ tài liệu ưu tiên xử lý trên thiết bị",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Phiên bản ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun PrivacyItem(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                modifier = Modifier.padding(top = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun Context.emailSupport() {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_EMAIL".toUri()).apply {
        putExtra(Intent.EXTRA_SUBJECT, "Hỗ trợ GScan")
    }
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, "Không tìm thấy ứng dụng email.", Toast.LENGTH_SHORT).show() }
}
