package com.example.gscan.feature.security.domain.repository

import com.example.gscan.feature.security.domain.model.AppLockSettings
import com.example.gscan.feature.security.domain.model.AppLockTimeout
import com.example.gscan.feature.security.domain.model.PinVerificationResult
import kotlinx.coroutines.flow.StateFlow

interface AppLockRepository {
    val settings: StateFlow<AppLockSettings>

    suspend fun enable(pin: String)
    suspend fun verifyPin(pin: String): PinVerificationResult
    suspend fun changePin(currentPin: String, newPin: String): PinVerificationResult
    suspend fun disable(pin: String): PinVerificationResult
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setLockTimeout(timeout: AppLockTimeout)
}
