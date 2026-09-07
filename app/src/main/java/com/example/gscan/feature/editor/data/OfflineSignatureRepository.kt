package com.example.gscan.feature.editor.data

import androidx.room.withTransaction
import com.example.gscan.core.database.GScanDatabase
import com.example.gscan.core.database.model.SignatureTemplateEntity
import com.example.gscan.core.image.SignatureInk
import com.example.gscan.core.storage.DocumentOperationLock
import com.example.gscan.feature.editor.domain.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineSignatureRepository @Inject constructor(
    private val database: GScanDatabase,
    private val lock: DocumentOperationLock,
) : SignatureRepository {
    override fun observeTemplates() = database.documentDao().observeSignatureTemplates().map { rows ->
        rows.map { SignatureTemplate(it.id, it.name, SignatureInk.decode(it.ink)) }
    }.flowOn(Dispatchers.Default)

    override suspend fun saveTemplate(name: String, ink: Ink) = withContext(Dispatchers.IO) {
        val title = name.trim()
        require(title.isNotEmpty() && title.length <= 60) { "Tên mẫu cần từ 1 đến 60 ký tự." }
        require(ink.any { it.size > 1 }) { "Hãy vẽ chữ ký trước khi lưu." }
        database.documentDao().insertSignatureTemplate(SignatureTemplateEntity(
            UUID.randomUUID().toString(), title, SignatureInk.encode(ink),
        ))
    }

    override suspend fun deleteTemplate(id: String) { database.documentDao().deleteSignatureTemplate(id) }

    override suspend fun savePage(documentId: String, pageId: String, rotation: Int, ink: Ink) {
        withContext(Dispatchers.IO) {
            val encoded = SignatureInk.encode(ink)
            lock.mutex.withLock {
                database.withTransaction {
                    val page = database.documentDao().getWithPages(documentId)?.pages?.firstOrNull { it.id == pageId }
                    requireNotNull(page) { "Trang đã bị xóa. Hãy mở lại tài liệu." }
                    require(page.rotationDegrees == rotation) { "Trang đã xoay. Hãy mở lại để đặt chữ ký đúng vị trí." }
                    check(database.documentDao().updateSignature(documentId, pageId, encoded) == 1)
                    database.documentDao().touchDocument(documentId, System.currentTimeMillis())
                }
            }
        }
    }
}
