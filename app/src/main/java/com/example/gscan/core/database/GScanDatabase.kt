package com.example.gscan.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.gscan.core.database.dao.DocumentDao
import com.example.gscan.core.database.model.DocumentEntity

@Database(
    entities = [DocumentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GScanDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
