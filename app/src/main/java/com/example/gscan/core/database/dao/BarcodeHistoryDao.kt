package com.example.gscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gscan.core.database.model.BarcodeHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BarcodeHistoryDao {
    @Query("SELECT * FROM barcode_history ORDER BY scannedAt DESC, id DESC")
    fun observe(): Flow<List<BarcodeHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: BarcodeHistoryEntity)
    @Query("DELETE FROM barcode_history WHERE id = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM barcode_history")
    suspend fun clear()
}
