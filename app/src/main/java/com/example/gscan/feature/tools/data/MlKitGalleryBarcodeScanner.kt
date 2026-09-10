package com.example.gscan.feature.tools.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.gscan.feature.tools.domain.BarcodeResult
import com.example.gscan.feature.tools.domain.GalleryBarcodeScanException
import com.example.gscan.feature.tools.domain.GalleryBarcodeScanFailure
import com.example.gscan.feature.tools.domain.GalleryBarcodeScanner
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MlKitGalleryBarcodeScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : GalleryBarcodeScanner {
    override suspend fun scan(imageUri: String): List<BarcodeResult> = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(imageUri) }
            .getOrNull()
            ?.takeIf { it.scheme == "content" }
            ?: throw GalleryBarcodeScanException(GalleryBarcodeScanFailure.INVALID_URI)
        val bitmap = decodeBoundedBitmap(uri)
        val rotation = readRotation(uri)
        val scanner = BarcodeScanning.getClient()
        try {
            val barcodes = runCatching {
                scanner.process(InputImage.fromBitmap(bitmap, rotation)).awaitResult()
            }.getOrElse {
                throw GalleryBarcodeScanException(GalleryBarcodeScanFailure.PROCESSING_FAILED)
            }
            val readable = barcodes.mapNotNull { barcode ->
                barcode.rawValue?.takeIf(String::isNotEmpty)?.let { barcode to it }
            }
            if (readable.isEmpty()) {
                throw GalleryBarcodeScanException(GalleryBarcodeScanFailure.NO_READABLE_CODE)
            }
            val results = readable
                .filter { (_, content) -> content.length <= MAX_CONTENT_LENGTH }
                .map { (barcode, content) -> barcode.toDomainResult(content) }
                .distinctBy { Triple(it.content, it.format, it.type) }
            if (results.isEmpty()) {
                throw GalleryBarcodeScanException(GalleryBarcodeScanFailure.CONTENT_TOO_LONG)
            }
            results
        } finally {
            scanner.close()
            bitmap.recycle()
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { continuation ->
        addOnSuccessListener(continuation::resume)
        addOnFailureListener(continuation::resumeWithException)
        addOnCanceledListener { continuation.resumeWithException(IllegalStateException("ML Kit task cancelled")) }
    }

    private fun decodeBoundedBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw GalleryBarcodeScanException(GalleryBarcodeScanFailure.IMAGE_UNREADABLE)
        boundsStream.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw GalleryBarcodeScanException(GalleryBarcodeScanFailure.IMAGE_UNREADABLE)
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        } ?: throw GalleryBarcodeScanException(GalleryBarcodeScanFailure.IMAGE_UNREADABLE)
    }

    private fun readRotation(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    }.getOrDefault(0)

    private companion object {
        const val MAX_IMAGE_DIMENSION = 2048
        const val MAX_CONTENT_LENGTH = 32768
    }
}
