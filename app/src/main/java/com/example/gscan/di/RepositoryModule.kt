package com.example.gscan.di

import com.example.gscan.feature.documents.data.OfflineDocumentRepository
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        implementation: OfflineDocumentRepository,
    ): DocumentRepository
}
