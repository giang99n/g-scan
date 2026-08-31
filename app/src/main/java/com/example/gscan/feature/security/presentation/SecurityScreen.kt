package com.example.gscan.feature.security.presentation

import androidx.compose.runtime.Composable
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun SecurityScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "Bảo mật tài liệu",
        onBackClick = onBackClick,
    )
}
