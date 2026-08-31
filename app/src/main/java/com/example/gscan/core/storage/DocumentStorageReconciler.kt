package com.example.gscan.core.storage

import com.example.gscan.core.database.GScanDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.withLock

@Singleton
class DocumentStorageReconciler @Inject constructor(
    private val database: GScanDatabase,
    private val storage: DocumentFileStorage,
    private val operationLock: DocumentOperationLock,
) {
    suspend fun reconcile() {
        operationLock.mutex.withLock {
            val persistedDocumentIds = database.documentDao().getAllIds().toSet()
            val persistedPageUris = database.documentDao().getAllPageSourceUris().toSet()
            storage.reconcile(persistedDocumentIds, persistedPageUris)
        }
    }
}
