package com.example.gscan.feature.scanner.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun ScannerScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "Scan tài liệu",
        onBackClick = onBackClick,
    )
}
