package com.example.gscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.DocumentWithPages
import com.example.gscan.core.database.model.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAtEpochMillis DESC")
    abstract fun observeAll(): Flow<List<DocumentEntity>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    abstract fun observeWithPages(documentId: String): Flow<DocumentWithPages?>

    @Query("SELECT id FROM documents")
    abstract suspend fun getAllIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM documents WHERE id = :documentId)")
    abstract suspend fun exists(documentId: String): Boolean

    @Query("DELETE FROM documents WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertPages(pages: List<PageEntity>)

    @Transaction
    open suspend fun insertDocumentWithPages(
        document: DocumentEntity,
        pages: List<PageEntity>,
    ) {
        require(pages.isNotEmpty()) { "A scanned document must contain at least one page" }
        insertDocument(document)
        insertPages(pages)
    }
}
