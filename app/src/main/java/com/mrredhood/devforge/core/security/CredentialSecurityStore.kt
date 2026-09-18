package com.mrredhood.devforge.core.security

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * Security-aware credential facade. Ordinary credentials remain usable without biometric protection;
 * when protection is enabled, reads/writes are delegated to the biometric-backed store.
 */
class CredentialSecurityStore(context: Context) : SecretStore {
    private val appContext = context.applicationContext
    private val ordinary = AndroidSecretStore(appContext)
    private val biometric = BiometricProtectedSecretStore(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val isProtectionEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)

    val isUnlocked: Boolean
        get() = !isProtectionEnabled || biometric.isUnlocked()

    fun canAuthenticate(): Boolean = biometric.canAuthenticate()

    fun unlock(activity: FragmentActivity, onResult: (Boolean, String?) -> Unit) {
        if (!isProtectionEnabled) {
            onResult(true, null)
            return
        }
        biometric.authenticate(activity, onResult)
    }

    fun lock() {
        biometric.lock()
    }

    fun contains(key: String): Boolean =
        if (isProtectionEnabled) biometric.contains(key) else ordinary.contains(key)

    fun enableProtection(keys: Collection<String>) {
        require(isProtectionEnabled.not()) { "Biometric protection is already enabled." }
        require(biometric.isUnlocked()) { "Biometric authentication is required before enabling protection." }

        val migrated = mutableListOf<String>()
        try {
            keys.distinct().forEach { key ->
                if (ordinary.contains(key)) {
                    val value = ordinary.get(key) ?: return@forEach
                    biometric.put(key, value)
                    migrated += key
                }
            }
            preferences.edit().putBoolean(KEY_ENABLED, true).apply()
            migrated.forEach(ordinary::remove)
        } catch (error: Throwable) {
            migrated.forEach { key ->
                runCatching { biometric.remove(key) }
            }
            throw error
        }
    }

    fun disableProtection(keys: Collection<String>) {
        require(isProtectionEnabled) { "Biometric protection is not enabled." }
        require(biometric.isUnlocked()) { "Biometric authentication is required before disabling protection." }

        val copied = mutableListOf<String>()
        try {
            keys.distinct().forEach { key ->
                if (biometric.contains(key)) {
                    val value = biometric.get(key) ?: return@forEach
                    ordinary.put(key, value)
                    copied += key
                }
            }
            preferences.edit().putBoolean(KEY_ENABLED, false).apply()
            copied.forEach { key -> runCatching { biometric.remove(key) } }
            biometric.lock()
        } catch (error: Throwable) {
            throw error
        }
    }

    override fun put(key: String, value: String) {
        if (isProtectionEnabled) biometric.put(key, value) else ordinary.put(key, value)
    }

    override fun get(key: String): String? =
        if (isProtectionEnabled) biometric.get(key) else ordinary.get(key)

    override fun remove(key: String) {
        if (isProtectionEnabled) biometric.remove(key) else ordinary.remove(key)
    }

    companion object {
        private const val PREFERENCES = "devforge_credential_security"
        private const val KEY_ENABLED = "biometric_protection_enabled"
    }
}
