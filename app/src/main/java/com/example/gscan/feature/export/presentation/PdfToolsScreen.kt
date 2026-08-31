package com.example.gscan.feature.export.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun PdfToolsScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "Công cụ PDF",
        onBackClick = onBackClick,
    )
}
