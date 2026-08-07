package com.example.gscan.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF155EEF),
    secondary = Color(0xFF475467),
    surface = Color(0xFFF8FAFC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF84ADFF),
    secondary = Color(0xFFD0D5DD),
)

@Composable
fun GScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
