package com.example.gscan.core.database.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "ocr_search")
data class OcrSearchEntity(
    val pageId: String,
    val documentId: String,
    val text: String,
)
