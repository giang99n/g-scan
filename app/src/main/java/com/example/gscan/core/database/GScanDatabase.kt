package com.example.gscan.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.database.model.DocumentEntity
import com.example.gscan.core.database.model.OcrResultEntity
import com.example.gscan.core.database.model.OcrSearchEntity
import com.example.gscan.core.database.model.PageEntity

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        OcrResultEntity::class,
        OcrSearchEntity::class,
        com.example.gscan.core.database.model.SignatureTemplateEntity::class,
        com.example.gscan.core.database.model.BarcodeHistoryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class GScanDatabase : RoomDatabase() {
    abstract fun barcodeHistoryDao(): com.example.gscan.core.database.dao.BarcodeHistoryDao
    abstract fun documentDao(): DocumentDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `deletedAtEpochMillis` INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `barcode_history` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, `format` TEXT NOT NULL, `type` TEXT NOT NULL, `scannedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pages` ADD COLUMN `signatureInk` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `signature_templates` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `ink` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_results` (
                        `pageId` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `script` TEXT NOT NULL,
                        `engineVersion` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorCode` TEXT,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`pageId`),
                        FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ocr_results_documentId` " +
                        "ON `ocr_results` (`documentId`)",
                )
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `ocr_search` " +
                        "USING FTS4(`pageId` TEXT NOT NULL, `documentId` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, tokenize=unicode61)",
                )
            }
        }
    }
}
