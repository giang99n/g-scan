package com.example.gscan.feature.security.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.gscan.feature.security.domain.model.AppLockSettings
import com.example.gscan.feature.security.domain.model.AppLockTimeout
import com.example.gscan.feature.security.domain.model.PinVerificationResult
import com.example.gscan.feature.security.domain.repository.AppLockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class KeystoreAppLockRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AppLockRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val secureRandom = SecureRandom()
    private val _settings = MutableStateFlow(preferences.readSettings())

    override val settings: StateFlow<AppLockSettings> = _settings.asStateFlow()

    override suspend fun enable(pin: String) = mutex.withLock {
        requireValidPin(pin)
        val encryptedVerifier = withContext(Dispatchers.Default) { createEncryptedVerifier(pin) }
        check(
            preferences.edit()
                .putString(KEY_PIN_VERIFIER, encryptedVerifier)
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .remove(KEY_BLOCKED_UNTIL)
                .commitOffMain(),
        ) { "Không thể lưu cấu hình khóa ứng dụng." }
        publishSettings()
    }

    override suspend fun verifyPin(pin: String): PinVerificationResult = mutex.withLock {
        if (!settings.value.enabled) return@withLock PinVerificationResult.Success
        if (!isValidPin(pin)) return@withLock registerFailedAttempt()

        val now = System.currentTimeMillis()
        val blockedUntil = preferences.getLong(KEY_BLOCKED_UNTIL, 0L)
        if (blockedUntil > now) {
            return@withLock PinVerificationResult.TemporarilyLocked(
                ceil((blockedUntil - now) / 1_000.0).toInt().coerceAtLeast(1),
            )
        }
        if (blockedUntil != 0L) {
            if (!preferences.edit().putInt(KEY_FAILED_ATTEMPTS, 0).remove(KEY_BLOCKED_UNTIL).commitOffMain()) {
                return@withLock PinVerificationResult.Unavailable
            }
        }

        val encryptedVerifier = preferences.getString(KEY_PIN_VERIFIER, null)
            ?: return@withLock PinVerificationResult.Unavailable
        val matches = runCatching {
            withContext(Dispatchers.Default) { verifyEncryptedPin(pin, encryptedVerifier) }
        }.getOrElse { return@withLock PinVerificationResult.Unavailable }

        if (matches) {
            if (preferences.edit().putInt(KEY_FAILED_ATTEMPTS, 0).remove(KEY_BLOCKED_UNTIL).commitOffMain()) {
                PinVerificationResult.Success
            } else {
                PinVerificationResult.Unavailable
            }
        } else {
            registerFailedAttempt()
        }
    }

    override suspend fun changePin(currentPin: String, newPin: String): PinVerificationResult {
        requireValidPin(newPin)
        val verification = verifyPin(currentPin)
        if (verification == PinVerificationResult.Success) enable(newPin)
        return verification
    }

    override suspend fun disable(pin: String): PinVerificationResult {
        val verification = verifyPin(pin)
        if (verification != PinVerificationResult.Success) return verification
        mutex.withLock {
            check(
                preferences.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                    .remove(KEY_PIN_VERIFIER)
                    .remove(KEY_FAILED_ATTEMPTS)
                    .remove(KEY_BLOCKED_UNTIL)
                    .commitOffMain(),
            ) { "Không thể tắt khóa ứng dụng." }
            publishSettings()
            AppLockBiometricCrypto.deleteKey()
        }
        return verification
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) = mutex.withLock {
        if (!settings.value.enabled) return@withLock
        check(preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).commitOffMain())
        if (!enabled) AppLockBiometricCrypto.deleteKey()
        publishSettings()
    }

    override suspend fun setLockTimeout(timeout: AppLockTimeout) = mutex.withLock {
        if (!settings.value.enabled) return@withLock
        check(preferences.edit().putString(KEY_TIMEOUT, timeout.name).commitOffMain())
        publishSettings()
    }

    private suspend fun registerFailedAttempt(): PinVerificationResult {
        val failedAttempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        return if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            val saved = preferences.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_BLOCKED_UNTIL, System.currentTimeMillis() + RETRY_DELAY_MILLIS)
                .commitOffMain()
            if (saved) PinVerificationResult.TemporarilyLocked((RETRY_DELAY_MILLIS / 1_000L).toInt())
            else PinVerificationResult.Unavailable
        } else {
            val saved = preferences.edit().putInt(KEY_FAILED_ATTEMPTS, failedAttempts).commitOffMain()
            if (saved) PinVerificationResult.Invalid(MAX_FAILED_ATTEMPTS - failedAttempts)
            else PinVerificationResult.Unavailable
        }
    }

    private fun createEncryptedVerifier(pin: String): String {
        val salt = ByteArray(SALT_SIZE_BYTES).also(secureRandom::nextBytes)
        val algorithm = preferredPbkdf2Algorithm()
        val verifier = derivePin(pin, salt, algorithm.name)
        val payload = ByteBuffer.allocate(1 + salt.size + verifier.size)
            .put(algorithm.version)
            .put(salt)
            .put(verifier)
            .array()
        return encrypt(payload)
    }

    private fun verifyEncryptedPin(pin: String, encryptedVerifier: String): Boolean {
        val payload = decrypt(encryptedVerifier)
        if (payload.size != 1 + SALT_SIZE_BYTES + VERIFIER_SIZE_BYTES) {
            return false
        }
        val algorithm = when (payload[0]) {
            PBKDF2_SHA256_VERSION -> PBKDF2_SHA256_ALGORITHM
            PBKDF2_SHA1_VERSION -> PBKDF2_SHA1_ALGORITHM
            else -> return false
        }
        val salt = payload.copyOfRange(1, 1 + SALT_SIZE_BYTES)
        val expected = payload.copyOfRange(1 + SALT_SIZE_BYTES, payload.size)
        return MessageDigest.isEqual(expected, derivePin(pin, salt, algorithm))
    }

    private fun derivePin(pin: String, salt: ByteArray, algorithm: String): ByteArray {
        val characters = pin.toCharArray()
        return try {
            val spec = PBEKeySpec(characters, salt, PBKDF2_ITERATIONS, VERIFIER_SIZE_BYTES * 8)
            try {
                SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } finally {
            characters.fill('\u0000')
        }
    }

    private fun preferredPbkdf2Algorithm(): Pbkdf2Algorithm =
        if (runCatching { SecretKeyFactory.getInstance(PBKDF2_SHA256_ALGORITHM) }.isSuccess) {
            Pbkdf2Algorithm(PBKDF2_SHA256_VERSION, PBKDF2_SHA256_ALGORITHM)
        } else {
            Pbkdf2Algorithm(PBKDF2_SHA1_VERSION, PBKDF2_SHA1_ALGORITHM)
        }

    private fun encrypt(payload: ByteArray): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload)
        val result = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): ByteArray {
        val encoded = Base64.decode(value, Base64.NO_WRAP)
        require(encoded.isNotEmpty())
        val ivSize = encoded[0].toInt() and 0xFF
        require(ivSize in 12..16 && encoded.size > 1 + ivSize)
        val iv = encoded.copyOfRange(1, 1 + ivSize)
        val ciphertext = encoded.copyOfRange(1 + ivSize, encoded.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun publishSettings() {
        _settings.value = preferences.readSettings()
    }

    private suspend fun SharedPreferences.Editor.commitOffMain(): Boolean =
        withContext(Dispatchers.IO) { commit() }

    private fun SharedPreferences.readSettings(): AppLockSettings {
        val timeout = getString(KEY_TIMEOUT, null)
            ?.let { value -> AppLockTimeout.entries.firstOrNull { it.name == value } }
            ?: AppLockTimeout.ONE_MINUTE
        return AppLockSettings(
            enabled = getBoolean(KEY_ENABLED, false),
            biometricEnabled = getBoolean(KEY_BIOMETRIC_ENABLED, false),
            lockTimeout = timeout,
        )
    }

    private fun requireValidPin(pin: String) {
        require(isValidPin(pin)) { "PIN phải gồm đúng 6 chữ số." }
    }

    private fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    private companion object {
        const val PREFERENCES_NAME = "app_lock"
        const val KEY_ENABLED = "enabled"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_TIMEOUT = "timeout"
        const val KEY_PIN_VERIFIER = "pin_verifier"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_BLOCKED_UNTIL = "blocked_until"
        const val KEY_ALIAS = "gscan_app_lock_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val PBKDF2_SHA256_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PBKDF2_SHA1_ALGORITHM = "PBKDF2WithHmacSHA1"
        const val PBKDF2_ITERATIONS = 120_000
        const val SALT_SIZE_BYTES = 16
        const val VERIFIER_SIZE_BYTES = 32
        const val PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 5
        const val RETRY_DELAY_MILLIS = 30_000L
        const val PBKDF2_SHA256_VERSION: Byte = 1
        const val PBKDF2_SHA1_VERSION: Byte = 2
    }
}

private data class Pbkdf2Algorithm(val version: Byte, val name: String)
