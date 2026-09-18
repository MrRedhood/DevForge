package com.mrredhood.devforge.core.security

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import com.mrredhood.devforge.core.ai.AIProvider

data class CredentialSecurityState(
    val biometricAvailable: Boolean,
    val protectionEnabled: Boolean,
    val unlocked: Boolean,
    val message: String? = null,
)

class CredentialSecurityViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CredentialSecurityStore(application)

    var state by mutableStateOf(currentState())
        private set

    fun unlock(activity: FragmentActivity) {
        store.unlock(activity) { success, message ->
            state = currentState(message = if (success) "Credentials unlocked for a limited window." else message)
        }
    }

    fun enableProtection(activity: FragmentActivity) {
        if (!store.canAuthenticate()) {
            state = currentState(message = "Strong biometric authentication is unavailable.")
            return
        }
        store.unlock(activity) { success, message ->
            if (!success) {
                state = currentState(message = message)
                return@unlock
            }
            runCatching { store.enableProtection(knownCredentialKeys()) }
                .onSuccess {
                    store.lock()
                    state = currentState(message = "Biometric protection is enabled for stored AI and GitHub credentials.")
                }
                .onFailure { error ->
                    state = currentState(message = error.message ?: "Unable to enable biometric protection.")
                }
        }
    }

    fun disableProtection(activity: FragmentActivity) {
        if (!store.isProtectionEnabled) return
        store.unlock(activity) { success, message ->
            if (!success) {
                state = currentState(message = message)
                return@unlock
            }
            runCatching { store.disableProtection(knownCredentialKeys()) }
                .onSuccess { state = currentState(message = "Biometric protection is disabled. Credentials remain Keystore-encrypted.") }
                .onFailure { error -> state = currentState(message = error.message ?: "Unable to disable biometric protection.") }
        }
    }

    fun lock() {
        store.lock()
        state = currentState(message = "Protected credentials are locked.")
    }

    private fun currentState(message: String? = null) = CredentialSecurityState(
        biometricAvailable = store.canAuthenticate(),
        protectionEnabled = store.isProtectionEnabled,
        unlocked = store.isUnlocked,
        message = message,
    )

    private fun knownCredentialKeys(): List<String> = buildList {
        add("github_access_token")
        AIProvider.entries.forEach { provider ->
            add("devforge.ai." + provider.id + ".api_key")
        }
    }
}
