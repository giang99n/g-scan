package com.example.gscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.example.gscan.app.GScanApp
import com.example.gscan.core.designsystem.theme.GScanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var sharedPdfUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPdfUri = if (savedInstanceState?.containsKey(STATE_SHARED_PDF_URI) == true) {
            savedInstanceState.getString(STATE_SHARED_PDF_URI)
        } else {
            intent.sharedPdfUri()
        }
        enableEdgeToEdge()
        setContent {
            GScanTheme {
                GScanApp(
                    sharedPdfUri = sharedPdfUri,
                    onSharedPdfConsumed = { sharedPdfUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedPdfUri = intent.sharedPdfUri()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SHARED_PDF_URI, sharedPdfUri)
        super.onSaveInstanceState(outState)
    }

    private fun Intent.sharedPdfUri(): String? {
        if (action != Intent.ACTION_SEND || PDF_MIME_TYPES.none { type.equals(it, ignoreCase = true) }) {
            return null
        }
        val streamUri = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
        val clipUri = clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
        return (streamUri ?: clipUri)?.toString()
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val LEGACY_PDF_MIME_TYPE = "application/x-pdf"
        val PDF_MIME_TYPES = setOf(PDF_MIME_TYPE, LEGACY_PDF_MIME_TYPE)
        const val STATE_SHARED_PDF_URI = "sharedPdfUri"
    }
}
