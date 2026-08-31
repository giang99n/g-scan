package com.example.gscan.core.image

import androidx.exifinterface.media.ExifInterface
import java.io.File

data class ImageOrientation(
    val rotationDegrees: Int = 0,
    val isFlippedHorizontally: Boolean = false,
) {
    val swapsDimensions: Boolean
        get() = rotationDegrees == 90 || rotationDegrees == 270
}

fun File.readImageOrientation(): ImageOrientation = runCatching {
    val exif = ExifInterface(this)
    ImageOrientation(
        rotationDegrees = exif.rotationDegrees,
        isFlippedHorizontally = exif.isFlipped,
    )
}.getOrDefault(ImageOrientation())
