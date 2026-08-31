package com.example.gscan.feature.editor.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun SignatureScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "Chữ ký",
        onBackClick = onBackClick,
    )
}
