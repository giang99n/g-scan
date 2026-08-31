package com.example.gscan.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LocalFileImage(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxDecodeSizePx: Int = 1200,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val cacheKey = "$uri@$maxDecodeSizePx"
    val imageState by produceState<LocalFileImageState>(
        initialValue = LocalFileImageState.Loading,
        cacheKey,
    ) {
        value = withContext(Dispatchers.IO) {
            if (uri == null) {
                LocalFileImageState.Empty
            } else {
                val cached = LocalBitmapCache.get(cacheKey)
                if (cached != null) {
                    LocalFileImageState.Success(cached)
                } else {
                    val decoded = uri.decodeSampledBitmap(maxDecodeSizePx)
                    if (decoded != null) {
                        LocalBitmapCache.put(cacheKey, decoded)
                        LocalFileImageState.Success(decoded)
                    } else {
                        LocalFileImageState.Failed
                    }
                }
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val state = imageState) {
            LocalFileImageState.Loading -> CircularProgressIndicator()
            LocalFileImageState.Empty -> Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LocalFileImageState.Failed -> Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = contentDescription?.let { "Không thể tải $it" } ?: "Không thể tải ảnh",
                tint = MaterialTheme.colorScheme.error,
            )
            is LocalFileImageState.Success -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

private sealed interface LocalFileImageState {
    data object Loading : LocalFileImageState
    data object Empty : LocalFileImageState
    data object Failed : LocalFileImageState
    data class Success(val bitmap: Bitmap) : LocalFileImageState
}

private object LocalBitmapCache : LruCache<String, Bitmap>(CACHE_SIZE_KB) {
    override fun sizeOf(key: String, value: Bitmap): Int =
        (value.allocationByteCount / BYTES_PER_KILOBYTE).coerceAtLeast(1)
}

private fun String.decodeSampledBitmap(maxSizePx: Int): Bitmap? {
    val path = toUri().path ?: return null
    val file = File(path)
    if (!file.isFile) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxSizePx ||
        bounds.outHeight / sampleSize > maxSizePx
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private const val BYTES_PER_KILOBYTE = 1024
private val CACHE_SIZE_KB = (
    (Runtime.getRuntime().maxMemory() / 16L)
        .coerceIn(4L * 1024L * 1024L, 32L * 1024L * 1024L) /
        BYTES_PER_KILOBYTE
    ).toInt()
