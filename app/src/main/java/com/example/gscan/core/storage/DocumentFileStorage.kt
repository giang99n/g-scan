package com.example.gscan.core.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredPage(
    val sourceUri: String,
    val width: Int,
    val height: Int,
)

enum class StorageFailureReason {
    SOURCE_UNAVAILABLE,
    STORAGE_FULL,
    INVALID_IMAGE,
    WRITE_FAILED,
    CLEANUP_FAILED,
}

class DocumentStorageException(
    val reason: StorageFailureReason,
    cause: Throwable? = null,
) : IOException(reason.name, cause)

@Singleton
class DocumentFileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val documentsRoot: File
        get() = File(context.filesDir, DOCUMENTS_DIRECTORY)

    suspend fun copyScannerPages(
        documentId: String,
        sourceUris: List<String>,
    ): List<StoredPage> = withContext(Dispatchers.IO) {
        require(sourceUris.isNotEmpty()) { "sourceUris must not be empty" }

        val root = documentsRoot.apply {
            if (!exists() && !mkdirs()) {
                throw DocumentStorageException(StorageFailureReason.WRITE_FAILED)
            }
        }
        val stagingDirectory = File(root, ".$documentId-staging")
        val finalDirectory = File(root, documentId)
        stagingDirectory.deleteRecursively()

        if (finalDirectory.exists() || !stagingDirectory.mkdir()) {
            throw DocumentStorageException(StorageFailureReason.WRITE_FAILED)
        }

        var movedToFinal = false
        try {
            ensureFreeSpace(sourceUris.map(String::toUri), root)

            val stagedPages = sourceUris.mapIndexed { index, source ->
                copyPage(
                    sourceUri = source.toUri(),
                    destinationDirectory = stagingDirectory,
                    position = index,
                )
            }

            if (!stagingDirectory.renameTo(finalDirectory)) {
                throw DocumentStorageException(StorageFailureReason.WRITE_FAILED)
            }
            movedToFinal = true

            stagedPages.mapIndexed { index, page ->
                page.copy(
                    sourceUri = Uri.fromFile(pageFile(finalDirectory, index)).toString(),
                )
            }
        } catch (error: DocumentStorageException) {
            throw error
        } catch (error: SecurityException) {
            throw DocumentStorageException(StorageFailureReason.SOURCE_UNAVAILABLE, error)
        } catch (error: IOException) {
            val reason = if (availableBytes(root) < MIN_FREE_SPACE_BYTES) {
                StorageFailureReason.STORAGE_FULL
            } else {
                StorageFailureReason.WRITE_FAILED
            }
            throw DocumentStorageException(reason, error)
        } finally {
            if (!movedToFinal) {
                stagingDirectory.deleteRecursively()
            }
        }
    }

    suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        deleteDirectory(File(documentsRoot, documentId))
        deleteDirectory(File(documentsRoot, ".$documentId-staging"))
    }

    suspend fun reconcile(persistedDocumentIds: Set<String>) = withContext(Dispatchers.IO) {
        val root = documentsRoot
        if (!root.exists()) return@withContext

        root.listFiles().orEmpty().forEach { entry ->
            val isStaging = entry.name.startsWith(".") && entry.name.endsWith(STAGING_SUFFIX)
            val isOrphan = entry.isDirectory && entry.name !in persistedDocumentIds
            if (isStaging || isOrphan || !entry.isDirectory) {
                deleteDirectory(entry)
            }
        }
    }

    private fun ensureFreeSpace(sourceUris: List<Uri>, root: File) {
        val knownSize = sourceUris.sumOf { uri ->
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.length.coerceAtLeast(0L)
                } ?: 0L
            }.getOrDefault(0L)
        }
        if (availableBytes(root) < knownSize + MIN_FREE_SPACE_BYTES) {
            throw DocumentStorageException(StorageFailureReason.STORAGE_FULL)
        }
    }

    @Suppress("DEPRECATION")
    private fun availableBytes(root: File): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val storageManager = context.getSystemService(StorageManager::class.java)
        storageManager.getAllocatableBytes(storageManager.getUuidForPath(root))
    } else {
        root.usableSpace
    }

    private fun copyPage(
        sourceUri: Uri,
        destinationDirectory: File,
        position: Int,
    ): StoredPage {
        val mimeType = context.contentResolver.getType(sourceUri)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw DocumentStorageException(StorageFailureReason.INVALID_IMAGE)
        }

        val destination = pageFile(destinationDirectory, position)
        val temporary = File(destinationDirectory, "${destination.name}.part")
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw DocumentStorageException(StorageFailureReason.SOURCE_UNAVAILABLE)

        input.use { source ->
            FileOutputStream(temporary).use { output ->
                source.copyTo(output, COPY_BUFFER_BYTES)
                output.fd.sync()
            }
        }

        if (temporary.length() <= 0L || !temporary.renameTo(destination)) {
            temporary.delete()
            throw DocumentStorageException(StorageFailureReason.WRITE_FAILED)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(destination.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DocumentStorageException(StorageFailureReason.INVALID_IMAGE)
        }

        return StoredPage(
            sourceUri = Uri.fromFile(destination).toString(),
            width = bounds.outWidth,
            height = bounds.outHeight,
        )
    }

    private fun pageFile(directory: File, position: Int): File =
        File(directory, "page-${(position + 1).toString().padStart(4, '0')}.jpg")

    private fun deleteDirectory(file: File) {
        if (file.exists() && !file.deleteRecursively()) {
            throw DocumentStorageException(StorageFailureReason.CLEANUP_FAILED)
        }
    }

    private companion object {
        const val DOCUMENTS_DIRECTORY = "documents"
        const val STAGING_SUFFIX = "-staging"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MIN_FREE_SPACE_BYTES = 10L * 1024L * 1024L
    }
}
