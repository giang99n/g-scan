package com.example.gscan.feature.ocr.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun OcrScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "OCR & tìm kiếm",
        onBackClick = onBackClick,
    )
}
