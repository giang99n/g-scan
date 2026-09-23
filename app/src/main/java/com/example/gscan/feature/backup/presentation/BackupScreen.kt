package com.example.gscan.feature.backup.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.gscan.core.designsystem.component.FeatureScreenScaffold

@Composable
fun BackupScreen(onBackClick: () -> Unit) {
    FeatureScreenScaffold(
        title = "Sao lưu & khôi phục",
        onBackClick = onBackClick,
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun BackupScreenPreview() {
    com.example.gscan.core.designsystem.theme.GScanTheme(darkTheme = false) {
        BackupScreen(onBackClick = {})
    }
}
