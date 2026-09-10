package com.example.gscan.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class OcrResultEntity(
    @PrimaryKey val pageId: String,
    val documentId: String,
    val text: String,
    val script: String,
    val engineVersion: String,
    val status: String,
    val errorCode: String?,
    val updatedAtEpochMillis: Long,
)
