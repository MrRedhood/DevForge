package com.mrredhood.devforge.core.security

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.content.Context

/**
 * Optional high-security secret store. Access requires a recent successful strong biometric
 * authentication; ciphertext remains in app-private preferences and the AES key stays in Keystore.
 */
class BiometricProtectedSecretStore(context: Context) : SecretStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    @Volatile private var unlockedUntilEpochMs: Long = 0L

    fun canAuthenticate(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun isUnlocked(): Boolean = System.currentTimeMillis() < unlockedUntilEpochMs

    fun lock() {
        unlockedUntilEpochMs = 0L
    }

    fun authenticate(
        activity: FragmentActivity,
        onResult: (Boolean, String?) -> Unit,
    ) {
        if (!canAuthenticate()) {
            onResult(false, "Strong biometric authentication is unavailable on this device.")
            return
        }
        val cipher = runCatching { newCipher(Cipher.ENCRYPT_MODE) }.getOrElse {
            onResult(false, "Unable to prepare biometric secret protection.")
            return
        }
        val prompt = BiometricPrompt(
            activity,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockedUntilEpochMs = System.currentTimeMillis() + UNLOCK_WINDOW_MS
                    onResult(true, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false, errString.toString())
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock protected secrets")
            .setSubtitle("Use biometric authentication to access high-security credentials")
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    override fun put(key: String, value: String) {
        requireUnlocked()
        val cipher = newCipher(Cipher.ENCRYPT_MODE)
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(cipher.iv.size + ciphertext.size)
        cipher.iv.copyInto(combined)
        ciphertext.copyInto(combined, destinationOffset = cipher.iv.size)
        preferences.edit().putString(key, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    override fun get(key: String): String? {
        requireUnlocked()
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > GCM_IV_BYTES)
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val cipher = newCipher(Cipher.DECRYPT_MODE, iv)
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    override fun remove(key: String) {
        requireUnlocked()
        preferences.edit().remove(key).apply()
    }

    fun contains(key: String): Boolean = preferences.contains(key)

    private fun requireUnlocked() {
        check(isUnlocked()) { "Biometric authentication is required to access protected secrets." }
    }

    private fun newCipher(mode: Int, iv: ByteArray? = null): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (iv == null) {
            cipher.init(mode, getOrCreateKey())
        } else {
            cipher.init(mode, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(300)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "devforge_biometric_secret_key"
        const val PREFERENCES_NAME = "devforge_biometric_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val UNLOCK_WINDOW_MS = 5L * 60L * 1000L
    }
}
