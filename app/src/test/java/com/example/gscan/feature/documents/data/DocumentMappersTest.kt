package com.example.gscan.feature.documents.data

import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.feature.documents.domain.model.DocumentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentMappersTest {
    @Test
    fun `entity is mapped to domain model`() {
        val entity = DocumentEntity(
            id = "document-1",
            title = "Hóa đơn",
            pageCount = 2,
            thumbnailUri = "file://thumbnail.jpg",
            status = "READY",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
        )

        val result = entity.toDomain()

        assertEquals("document-1", result.id)
        assertEquals(2, result.pageCount)
        assertEquals(DocumentStatus.READY, result.status)
    }

    @Test
    fun `unknown database status becomes failed instead of crashing`() {
        val entity = DocumentEntity(
            id = "document-2",
            title = "Tài liệu cũ",
            pageCount = 1,
            thumbnailUri = null,
            status = "UNKNOWN_FROM_OLD_VERSION",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
        )

        assertEquals(DocumentStatus.FAILED, entity.toDomain().status)
    }
}
