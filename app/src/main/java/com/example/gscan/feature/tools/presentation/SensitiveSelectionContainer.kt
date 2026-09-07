package com.example.gscan.feature.tools.presentation

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CancellationException

internal fun ClipData.markSensitive(): ClipData = apply {
    description.extras = (description.extras?.let(::PersistableBundle) ?: PersistableBundle()).apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
}

/** Cover selection-menu Copy and keyboard Copy, as well as the explicit copy button. */
@Composable
internal fun SensitiveSelectionContainer(onCopyError: () -> Unit, content: @Composable () -> Unit) {
    val clipboard = LocalClipboard.current
    val latestError = rememberUpdatedState(onCopyError)
    val sensitiveClipboard = remember(clipboard) {
        object : Clipboard by clipboard {
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                try {
                    clipEntry?.clipData?.markSensitive()
                    clipboard.setClipEntry(clipEntry)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    latestError.value()
                }
            }
        }
    }
    CompositionLocalProvider(LocalClipboard provides sensitiveClipboard) {
        SelectionContainer(content = content)
    }
}
