package com.example.gscan.core.storage

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

@Singleton
class DocumentOperationLock @Inject constructor() {
    val mutex = Mutex()
}
