package com.example.gscan.feature.tools.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun QrBarcodeScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "QR & barcode",
        onBackClick = onBackClick,
    )
}
