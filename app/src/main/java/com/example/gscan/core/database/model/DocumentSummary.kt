package com.example.gscan.core.database.model

import androidx.room.Embedded

data class DocumentSummary(
    @Embedded val document: DocumentEntity,
    val thumbnailRotationDegrees: Int,
)
