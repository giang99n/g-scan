package com.example.gscan.feature.scanner.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScannerPlaceholderScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Scanner feature", style = MaterialTheme.typography.headlineMedium)
        Text("ML Kit Scanner → lưu local → OCR/PDF/editor.")
        Button(onClick = onBackClick, modifier = Modifier.padding(top = 20.dp)) {
            Text("Quay lại")
        }
    }
}
