package com.example.gscan.feature.security.domain.usecase

import com.example.gscan.feature.security.domain.model.AppLockTimeout
import com.example.gscan.feature.security.domain.repository.AppLockRepository
import javax.inject.Inject

class EnableAppLockUseCase @Inject constructor(private val repository: AppLockRepository) {
    suspend operator fun invoke(pin: String) = repository.enable(pin)
}

class VerifyAppLockPinUseCase @Inject constructor(private val repository: AppLockRepository) {
    suspend operator fun invoke(pin: String) = repository.verifyPin(pin)
}

class ChangeAppLockPinUseCase @Inject constructor(private val repository: AppLockRepository) {
    suspend operator fun invoke(currentPin: String, newPin: String) = repository.changePin(currentPin, newPin)
}

class DisableAppLockUseCase @Inject constructor(private val repository: AppLockRepository) {
    suspend operator fun invoke(pin: String) = repository.disable(pin)
}

class UpdateAppLockOptionsUseCase @Inject constructor(private val repository: AppLockRepository) {
    suspend fun setBiometricEnabled(enabled: Boolean) = repository.setBiometricEnabled(enabled)
    suspend fun setTimeout(timeout: AppLockTimeout) = repository.setLockTimeout(timeout)
}
