package com.example.gscan

import android.app.Application
import android.util.Log
import com.example.gscan.core.storage.DocumentStorageReconciler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GScanApplication : Application() {
    @Inject
    lateinit var storageReconciler: DocumentStorageReconciler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { storageReconciler.reconcile() }
                .onFailure { error -> Log.e(TAG, "Document storage reconciliation failed", error) }
        }
    }

    private companion object {
        const val TAG = "GScanApplication"
    }
}
