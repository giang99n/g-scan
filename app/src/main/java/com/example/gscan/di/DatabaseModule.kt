package com.example.gscan.di

import android.content.Context
import androidx.room.Room
import com.example.gscan.core.database.GScanDatabase
import com.example.gscan.core.database.dao.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GScanDatabase =
        Room.databaseBuilder(context, GScanDatabase::class.java, "gscan.db")
            .addMigrations(GScanDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideDocumentDao(database: GScanDatabase): DocumentDao = database.documentDao()
}
