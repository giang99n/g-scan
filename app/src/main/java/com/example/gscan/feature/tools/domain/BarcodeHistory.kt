package com.example.gscan.feature.tools.domain

import java.net.URI
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

data class BarcodeResult(val content: String, val format: String, val type: String)
data class BarcodeHistoryItem(val id: String, val result: BarcodeResult, val scannedAt: Long)

interface BarcodeScanner {
    fun scan(onResult: (BarcodeResult) -> Unit, onCancelled: () -> Unit, onError: (String) -> Unit)
}

interface BarcodeHistoryRepository {
    fun observe(): Flow<List<BarcodeHistoryItem>>
    suspend fun save(item: BarcodeHistoryItem)
    suspend fun delete(id: String)
    suspend fun clear()
}

class BarcodeHistoryUseCases @Inject constructor(private val repository: BarcodeHistoryRepository) {
    fun observe() = repository.observe()
    suspend fun save(item: BarcodeHistoryItem) {
        require(item.result.content.isNotEmpty() && item.result.content.length <= 32768) { "Nội dung mã rỗng hoặc quá dài." }
        repository.save(item)
    }
    suspend fun delete(id: String) = repository.delete(id)
    suspend fun clear() = repository.clear()
}

/** Only explicit web URLs can be opened. Never launch intent:, file:, javascript: or Wi-Fi actions. */
fun BarcodeResult.webUrl(): String? = runCatching {
    val candidate = content.trim()
    val uri = URI(candidate)
    candidate.takeIf {
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }?.let { uri.scheme.lowercase(Locale.ROOT) + candidate.substring(uri.scheme.length) }
}.getOrNull()
