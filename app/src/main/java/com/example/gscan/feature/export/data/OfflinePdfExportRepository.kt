package com.example.gscan.feature.export.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.database.model.PageEntity
import com.example.gscan.core.image.readImageOrientation
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.feature.export.domain.model.ExportedPdf
import com.example.gscan.feature.export.domain.model.PdfExportException
import com.example.gscan.feature.export.domain.model.PdfExportFailure
import com.example.gscan.feature.export.domain.model.PdfQualityPreset
import com.example.gscan.feature.export.domain.repository.PdfExportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class OfflinePdfExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val operationLock: DocumentOperationLock,
) : PdfExportRepository {
    override suspend fun createPdf(
        documentId: String,
        preset: PdfQualityPreset,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit,
    ): ExportedPdf = withContext(Dispatchers.IO) {
        operationLock.mutex.withLock {
            val details = documentDao.getWithPages(documentId)
                ?: throw PdfExportException(PdfExportFailure.DOCUMENT_NOT_FOUND)
            val pages = details.pages.sortedBy { it.position }
            if (pages.isEmpty()) throw PdfExportException(PdfExportFailure.NO_PAGES)

            val exportDirectory = File(context.cacheDir, EXPORT_DIRECTORY).apply {
                if (!exists() && !mkdirs()) {
                    throw PdfExportException(PdfExportFailure.WRITE_FAILED)
                }
            }
            cleanupExpiredExports(exportDirectory)
            if (availableBytes(exportDirectory) < MIN_FREE_SPACE_BYTES) {
                throw PdfExportException(PdfExportFailure.STORAGE_FULL)
            }

            val displayName = "${details.document.title.toSafeFileName()}-${System.currentTimeMillis()}.pdf"
            val destination = File(exportDirectory, displayName)
            val temporary = File(exportDirectory, ".$displayName.part")
            var committed = false
            try {
                writeImagePdf(temporary, pages, preset, onProgress)
                currentCoroutineContext().ensureActive()
                validatePdf(temporary, pages.size)
                currentCoroutineContext().ensureActive()
                if (!temporary.renameTo(destination)) {
                    throw PdfExportException(PdfExportFailure.WRITE_FAILED)
                }
                committed = true
                ExportedPdf(
                    filePath = destination.absolutePath,
                    displayName = displayName,
                    pageCount = pages.size,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: PdfExportException) {
                throw error
            } catch (error: IOException) {
                val reason = if (availableBytes(exportDirectory) < MIN_FREE_SPACE_BYTES) {
                    PdfExportFailure.STORAGE_FULL
                } else {
                    PdfExportFailure.WRITE_FAILED
                }
                throw PdfExportException(reason, error)
            } catch (error: OutOfMemoryError) {
                throw PdfExportException(PdfExportFailure.INSUFFICIENT_MEMORY, error)
            } catch (error: Exception) {
                throw PdfExportException(PdfExportFailure.UNKNOWN, error)
            } finally {
                if (!committed) temporary.delete()
            }
        }
    }

    override suspend fun savePdf(exportedPdf: ExportedPdf, destinationUri: String) =
        withContext(Dispatchers.IO) {
            val source = requireValidExportFile(exportedPdf.filePath)
            try {
                val output = context.contentResolver.openOutputStream(destinationUri.toUri(), "w")
                    ?: throw PdfExportException(PdfExportFailure.WRITE_FAILED)
                source.inputStream().use { input ->
                    output.use { target -> copyCancellable(input::read, target) }
                }
                Unit
            } catch (error: PdfExportException) {
                throw error
            } catch (error: IOException) {
                throw PdfExportException(PdfExportFailure.WRITE_FAILED, error)
            } catch (error: SecurityException) {
                throw PdfExportException(PdfExportFailure.WRITE_FAILED, error)
            }
        }

    private suspend fun writeImagePdf(
        destination: File,
        pages: List<PageEntity>,
        preset: PdfQualityPreset,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit,
    ) {
        FileOutputStream(destination).use { fileOutput ->
            val output = CountingOutputStream(fileOutput)
            val objectCount = PDF_BASE_OBJECT_COUNT + pages.size * PDF_OBJECTS_PER_PAGE
            val offsets = LongArray(objectCount + 1)

            output.writeText("%PDF-1.4\n")
            output.write(PDF_BINARY_COMMENT)
            output.writeText("\n")

            output.beginObject(PDF_CATALOG_OBJECT, offsets)
            output.writeText("<< /Type /Catalog /Pages $PDF_PAGES_OBJECT 0 R >>\nendobj\n")

            output.beginObject(PDF_PAGES_OBJECT, offsets)
            val pageReferences = pages.indices.joinToString(" ") { index ->
                "${pageObjectId(index)} 0 R"
            }
            output.writeText("<< /Type /Pages /Count ${pages.size} /Kids [$pageReferences] >>\nendobj\n")

            pages.forEachIndexed { index, page ->
                currentCoroutineContext().ensureActive()
                val jpegFile = File(destination.parentFile, ".${destination.name}.page-$index.jpg.part")
                try {
                    val image = encodePageAsJpeg(page, preset, jpegFile)
                    writePdfPage(output, offsets, index, image, jpegFile)
                } finally {
                    jpegFile.delete()
                }
                onProgress(index + 1, pages.size)
            }

            val xrefOffset = output.bytesWritten
            output.writeText("xref\n0 ${objectCount + 1}\n")
            output.writeText("0000000000 65535 f \n")
            for (objectId in 1..objectCount) {
                output.writeText(String.format(Locale.US, "%010d 00000 n \n", offsets[objectId]))
            }
            output.writeText(
                "trailer\n<< /Size ${objectCount + 1} /Root $PDF_CATALOG_OBJECT 0 R >>\n" +
                    "startxref\n$xrefOffset\n%%EOF\n",
            )
            currentCoroutineContext().ensureActive()
            fileOutput.fd.sync()
        }
    }

    private suspend fun encodePageAsJpeg(
        page: PageEntity,
        preset: PdfQualityPreset,
        destination: File,
    ): EncodedImage {
        var bitmap = decodePage(page, preset.maxImageDimension)
        try {
            if (bitmap.hasAlpha()) {
                val flattened = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
                Canvas(flattened).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                bitmap.recycle()
                bitmap = flattened
            }
            currentCoroutineContext().ensureActive()
            FileOutputStream(destination).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, output)) {
                    throw PdfExportException(PdfExportFailure.WRITE_FAILED)
                }
                output.fd.sync()
            }
            return EncodedImage(bitmap.width, bitmap.height, destination.length())
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun writePdfPage(
        output: CountingOutputStream,
        offsets: LongArray,
        index: Int,
        image: EncodedImage,
        jpegFile: File,
    ) {
        val landscape = image.width > image.height
        val pageWidth = if (landscape) A4_LONG_EDGE else A4_SHORT_EDGE
        val pageHeight = if (landscape) A4_SHORT_EDGE else A4_LONG_EDGE
        val scale = minOf(pageWidth.toFloat() / image.width, pageHeight.toFloat() / image.height)
        val drawWidth = (image.width * scale).toInt().coerceAtLeast(1)
        val drawHeight = (image.height * scale).toInt().coerceAtLeast(1)
        val left = (pageWidth - drawWidth) / 2
        val bottom = (pageHeight - drawHeight) / 2
        val pageObject = pageObjectId(index)
        val contentObject = pageObject + 1
        val imageObject = pageObject + 2
        val content = "q\n$drawWidth 0 0 $drawHeight $left $bottom cm\n/Im0 Do\nQ\n"
        val contentBytes = content.toByteArray(StandardCharsets.US_ASCII)

        output.beginObject(pageObject, offsets)
        output.writeText(
            "<< /Type /Page /Parent $PDF_PAGES_OBJECT 0 R /MediaBox [0 0 $pageWidth $pageHeight] " +
                "/Resources << /XObject << /Im0 $imageObject 0 R >> >> /Contents $contentObject 0 R >>\n" +
                "endobj\n",
        )

        output.beginObject(contentObject, offsets)
        output.writeText("<< /Length ${contentBytes.size} >>\nstream\n")
        output.write(contentBytes)
        output.writeText("endstream\nendobj\n")

        output.beginObject(imageObject, offsets)
        output.writeText(
            "<< /Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                "/Length ${image.byteCount} >>\nstream\n",
        )
        jpegFile.inputStream().use { input -> copyCancellable(input::read, output) }
        output.writeText("\nendstream\nendobj\n")
    }

    private suspend fun copyCancellable(
        read: (ByteArray) -> Int,
        output: OutputStream,
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private fun decodePage(page: PageEntity, maxDimension: Int): Bitmap {
        val file = page.sourceUri.toUri().path?.let(::File)
            ?: throw PdfExportException(PdfExportFailure.SOURCE_UNAVAILABLE)
        if (!file.isFile) throw PdfExportException(PdfExportFailure.SOURCE_UNAVAILABLE)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PdfExportException(PdfExportFailure.SOURCE_UNAVAILABLE)
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = if (bounds.outMimeType.supportsAlpha()) {
                    Bitmap.Config.ARGB_8888
                } else {
                    Bitmap.Config.RGB_565
                }
            },
        ) ?: throw PdfExportException(PdfExportFailure.SOURCE_UNAVAILABLE)

        val orientation = file.readImageOrientation()
        val userRotation = page.rotationDegrees.normalizedRotation()
        if (orientation.rotationDegrees == 0 &&
            !orientation.isFlippedHorizontally &&
            userRotation == 0
        ) {
            return decoded
        }

        val transform = Matrix().apply {
            if (orientation.isFlippedHorizontally) postScale(-1f, 1f)
            if (orientation.rotationDegrees != 0) postRotate(orientation.rotationDegrees.toFloat())
            if (userRotation != 0) postRotate(userRotation.toFloat())
        }
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
                .also { transformed -> if (transformed !== decoded) decoded.recycle() }
        } catch (error: OutOfMemoryError) {
            decoded.recycle()
            throw error
        } catch (error: Exception) {
            decoded.recycle()
            throw error
        }
    }

    private fun requireValidExportFile(filePath: String): File {
        val exportDirectory = File(context.cacheDir, EXPORT_DIRECTORY).canonicalFile
        val file = File(filePath).canonicalFile
        if (file.parentFile != exportDirectory || !file.isFile || file.extension.lowercase() != "pdf") {
            throw PdfExportException(PdfExportFailure.SOURCE_UNAVAILABLE)
        }
        return file
    }

    private fun validatePdf(file: File, expectedPageCount: Int) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount != expectedPageCount) {
                    throw PdfExportException(PdfExportFailure.WRITE_FAILED)
                }
            }
        }
    }

    private fun cleanupExpiredExports(directory: File) {
        val cutoff = System.currentTimeMillis() - EXPORT_RETENTION_MILLIS
        directory.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun availableBytes(directory: File): Long =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageManager = context.getSystemService(StorageManager::class.java)
                storageManager.getAllocatableBytes(storageManager.getUuidForPath(directory))
            } else {
                directory.usableSpace
            }
        }.getOrDefault(directory.usableSpace)

    private fun String.toSafeFileName(): String =
        replace(INVALID_FILE_NAME_CHARACTERS, "_")
            .trim()
            .trim('.')
            .takeCodePoints(MAX_FILE_NAME_CODE_POINTS)
            .ifBlank { DEFAULT_FILE_NAME }

    private fun String.takeCodePoints(maxCodePoints: Long): String {
        val points = codePoints().limit(maxCodePoints).toArray()
        return String(points, 0, points.size)
    }

    private val PdfQualityPreset.maxImageDimension: Int
        get() = when (this) {
            PdfQualityPreset.SMALL -> 1_200
            PdfQualityPreset.BALANCED -> 1_800
            PdfQualityPreset.HIGH -> 2_600
        }

    private val PdfQualityPreset.jpegQuality: Int
        get() = when (this) {
            PdfQualityPreset.SMALL -> 65
            PdfQualityPreset.BALANCED -> 82
            PdfQualityPreset.HIGH -> 94
        }

    private fun String?.supportsAlpha(): Boolean =
        this == "image/png" || this == "image/webp"

    private fun pageObjectId(index: Int): Int = PDF_BASE_OBJECT_COUNT + 1 + index * PDF_OBJECTS_PER_PAGE

    private fun Int.normalizedRotation(): Int = ((this % 360) + 360) % 360

    private companion object {
        const val EXPORT_DIRECTORY = "pdf_exports"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MIN_FREE_SPACE_BYTES = 10L * 1024L * 1024L
        const val EXPORT_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L
        const val MAX_FILE_NAME_CODE_POINTS = 40L
        const val DEFAULT_FILE_NAME = "GScan"
        const val A4_SHORT_EDGE = 595
        const val A4_LONG_EDGE = 842
        const val PDF_CATALOG_OBJECT = 1
        const val PDF_PAGES_OBJECT = 2
        const val PDF_BASE_OBJECT_COUNT = 2
        const val PDF_OBJECTS_PER_PAGE = 3
        val PDF_BINARY_COMMENT = byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte())
        val INVALID_FILE_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|]")
    }
}

private data class EncodedImage(
    val width: Int,
    val height: Int,
    val byteCount: Long,
)

private class CountingOutputStream(
    private val delegate: OutputStream,
) : OutputStream() {
    var bytesWritten: Long = 0
        private set

    override fun write(value: Int) {
        delegate.write(value)
        bytesWritten++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        delegate.write(buffer, offset, length)
        bytesWritten += length
    }

    fun writeText(value: String) {
        write(value.toByteArray(StandardCharsets.US_ASCII))
    }

    fun beginObject(objectId: Int, offsets: LongArray) {
        offsets[objectId] = bytesWritten
        writeText("$objectId 0 obj\n")
    }
}
