package com.example.gscan.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.PageEntity

@Database(
    entities = [DocumentEntity::class, PageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class GScanDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pages` (
                        `id` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `sourceUri` TEXT NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `rotationDegrees` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pages_documentId_position` " +
                        "ON `pages` (`documentId`, `position`)",
                )

                // Version 1 chỉ có dữ liệu demo, không có file/page tương ứng.
                db.execSQL(
                    "UPDATE `documents` SET `pageCount` = 0, `thumbnailUri` = NULL, `status` = 'DRAFT'",
                )
            }
        }
    }
}
