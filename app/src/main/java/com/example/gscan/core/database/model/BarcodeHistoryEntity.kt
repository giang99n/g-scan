package com.example.gscan.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barcode_history")
data class BarcodeHistoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val format: String,
    val type: String,
    val scannedAt: Long,
)
