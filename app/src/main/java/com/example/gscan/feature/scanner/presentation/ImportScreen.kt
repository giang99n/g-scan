package com.example.gscan.feature.scanner.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun ImportScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "Nhập tài liệu",
        onBackClick = onBackClick,
    )
}
