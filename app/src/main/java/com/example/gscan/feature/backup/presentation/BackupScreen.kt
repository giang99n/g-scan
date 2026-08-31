package com.example.gscan.feature.backup.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun BackupScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "Sao lưu & khôi phục",
        onBackClick = onBackClick,
    )
}
