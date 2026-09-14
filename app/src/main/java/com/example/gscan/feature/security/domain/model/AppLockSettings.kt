package com.example.gscan.feature.security.domain.model

data class AppLockSettings(
    val enabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeout: AppLockTimeout = AppLockTimeout.ONE_MINUTE,
)

enum class AppLockTimeout(val durationMillis: Long) {
    IMMEDIATELY(0L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    FIFTEEN_MINUTES(15 * 60_000L),
}

sealed interface PinVerificationResult {
    data object Success : PinVerificationResult
    data class Invalid(val attemptsBeforeDelay: Int) : PinVerificationResult
    data class TemporarilyLocked(val remainingSeconds: Int) : PinVerificationResult
    data object Unavailable : PinVerificationResult
}
