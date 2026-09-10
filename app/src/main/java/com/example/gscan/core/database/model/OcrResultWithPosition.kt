package com.example.gscan.core.database.model

import androidx.room.Embedded

data class OcrResultWithPosition(
    @Embedded val result: OcrResultEntity,
    val position: Int,
)
