package com.example.gscan.core.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.example.gscan.core.image.readImageOrientation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    suspend fun copyDocumentPages(
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

            val stagedPages = mutableListOf<StagedPage>()
            for ((index, source) in sourceUris.withIndex()) {
                currentCoroutineContext().ensureActive()
                stagedPages += copyPage(
                    sourceUri = source.toUri(),
                    destinationDirectory = stagingDirectory,
                    position = index,
                )
            }

            currentCoroutineContext().ensureActive()
            if (!stagingDirectory.renameTo(finalDirectory)) {
                throw DocumentStorageException(StorageFailureReason.WRITE_FAILED)
            }
            movedToFinal = true

            stagedPages.map { page ->
                StoredPage(
                    sourceUri = Uri.fromFile(File(finalDirectory, page.fileName)).toString(),
                    width = page.width,
                    height = page.height,
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

    suspend fun deletePage(documentId: String, sourceUri: String) = withContext(Dispatchers.IO) {
        val source = sourceUri.toUri().path?.let(::File)
            ?: throw DocumentStorageException(StorageFailureReason.CLEANUP_FAILED)
        val root = documentsRoot.canonicalFile
        val expectedDirectory = File(root, documentId).canonicalFile
        val sourceFile = source.canonicalFile
        val isExpectedDirectory = expectedDirectory.parentFile == root
        val isExpectedSource = sourceFile.parentFile == expectedDirectory
        if (!isExpectedDirectory || !isExpectedSource) {
            throw DocumentStorageException(StorageFailureReason.CLEANUP_FAILED)
        }
        if (sourceFile.exists() && !sourceFile.delete()) {
            throw DocumentStorageException(StorageFailureReason.CLEANUP_FAILED)
        }
    }

    suspend fun reconcile(
        persistedDocumentIds: Set<String>,
        persistedPageUris: Set<String>,
    ) = withContext(Dispatchers.IO) {
        val root = documentsRoot
        if (!root.exists()) return@withContext

        val persistedPagePaths = persistedPageUris.mapNotNullTo(mutableSetOf()) { uri ->
            runCatching { uri.toUri().path?.let(::File)?.canonicalPath }.getOrNull()
        }

        root.listFiles().orEmpty().forEach { entry ->
            val isStaging = entry.name.startsWith(".") && entry.name.endsWith(STAGING_SUFFIX)
            val isOrphan = entry.isDirectory && entry.name !in persistedDocumentIds
            if (isStaging || isOrphan) {
                deleteDirectory(entry)
            } else if (entry.isDirectory) {
                entry.listFiles().orEmpty().forEach { pageFile ->
                    val isUnreferencedSource = pageFile.isFile &&
                        pageFile.name.matches(SOURCE_PAGE_FILE_PATTERN) &&
                        pageFile.canonicalPath !in persistedPagePaths
                    if (isUnreferencedSource) {
                        deleteDirectory(pageFile)
                    }
                }
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

    private suspend fun copyPage(
        sourceUri: Uri,
        destinationDirectory: File,
        position: Int,
    ): StagedPage {
        val mimeType = context.contentResolver.getType(sourceUri)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw DocumentStorageException(StorageFailureReason.INVALID_IMAGE)
        }

        val temporary = File(
            destinationDirectory,
            "page-${(position + 1).toString().padStart(4, '0')}.part",
        )
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw DocumentStorageException(StorageFailureReason.SOURCE_UNAVAILABLE)

        input.use { source ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val bytesRead = source.read(buffer)
                    if (bytesRead < 0) break
                    output.write(buffer, 0, bytesRead)
                }
                output.fd.sync()
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temporary.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DocumentStorageException(StorageFailureReason.INVALID_IMAGE)
        }

        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(bounds.outMimeType ?: mimeType)
            ?.lowercase()
            ?.takeIf { it.length <= MAX_EXTENSION_LENGTH && it.all(Char::isLetterOrDigit) }
            ?: DEFAULT_IMAGE_EXTENSION
        val destination = pageFile(destinationDirectory, position, extension)
        if (!temporary.renameTo(destination)) {
            throw DocumentStorageException(StorageFailureReason.WRITE_FAILED)
        }

        val orientation = destination.readImageOrientation()
        val displayedWidth = if (orientation.swapsDimensions) bounds.outHeight else bounds.outWidth
        val displayedHeight = if (orientation.swapsDimensions) bounds.outWidth else bounds.outHeight

        return StagedPage(
            fileName = destination.name,
            width = displayedWidth,
            height = displayedHeight,
        )
    }

    private fun pageFile(directory: File, position: Int, extension: String): File =
        File(directory, "page-${(position + 1).toString().padStart(4, '0')}.$extension")

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
        const val MAX_EXTENSION_LENGTH = 8
        const val DEFAULT_IMAGE_EXTENSION = "img"
        val SOURCE_PAGE_FILE_PATTERN = Regex("^page-\\d{4,}\\.[A-Za-z0-9]{1,8}$")
    }
}

private data class StagedPage(
    val fileName: String,
    val width: Int,
    val height: Int,
)
