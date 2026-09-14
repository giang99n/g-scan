package com.example.gscan.feature.security.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object AppLockBiometricCrypto {
    fun prepareAuthentication(createKeyIfMissing: Boolean): BiometricCryptoPreparation = runCatching {
        val key = getKey() ?: if (createKeyIfMissing) createKey() else {
            return BiometricCryptoPreparation.Unavailable(
                "Sinh trắc học cần được bật lại bằng PIN.",
                disableBiometric = true,
            )
        }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        BiometricCryptoPreparation.Ready(BiometricPrompt.CryptoObject(cipher))
    }.getOrElse {
        deleteKey()
        BiometricCryptoPreparation.Unavailable(
            "Sinh trắc học đã thay đổi. Hãy mở khóa bằng PIN và bật lại.",
            disableBiometric = true,
        )
    }

    fun finishAuthentication(cryptoObject: BiometricPrompt.CryptoObject?): Boolean = runCatching {
        cryptoObject?.cipher?.doFinal(AUTHENTICATION_CHALLENGE) != null
    }.getOrDefault(false)

    fun deleteKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getKey(): SecretKey? =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.getKey(KEY_ALIAS, null) as? SecretKey

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    private const val KEY_ALIAS = "gscan_app_lock_biometric_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private val AUTHENTICATION_CHALLENGE = "gscan-app-lock".encodeToByteArray()
}

sealed interface BiometricCryptoPreparation {
    data class Ready(val cryptoObject: BiometricPrompt.CryptoObject) : BiometricCryptoPreparation

    data class Unavailable(
        val message: String,
        val disableBiometric: Boolean,
    ) : BiometricCryptoPreparation
}
