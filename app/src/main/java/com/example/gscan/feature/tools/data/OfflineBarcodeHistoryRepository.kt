package com.example.gscan.feature.tools.data

import com.example.gscan.core.database.GScanDatabase
import com.example.gscan.core.database.model.BarcodeHistoryEntity
import com.example.gscan.feature.tools.domain.*
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

class OfflineBarcodeHistoryRepository @Inject constructor(database: GScanDatabase) : BarcodeHistoryRepository {
    private val dao = database.barcodeHistoryDao()
    override fun observe() = dao.observe().map { rows -> rows.map {
        BarcodeHistoryItem(it.id, BarcodeResult(it.content, it.format, it.type), it.scannedAt)
    } }.flowOn(Dispatchers.Default)
    override suspend fun save(item: BarcodeHistoryItem) = dao.insert(BarcodeHistoryEntity(
        item.id, item.result.content, item.result.format, item.result.type, item.scannedAt,
    ))
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun clear() = dao.clear()
}
