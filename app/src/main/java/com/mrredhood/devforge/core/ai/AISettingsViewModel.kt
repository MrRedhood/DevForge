package com.mrredhood.devforge.core.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class AISettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AISettingsRepository(application)

    var provider by mutableStateOf(repository.selectedProvider())
        private set
    var apiKey by mutableStateOf("")
        private set
    var customBaseUrl by mutableStateOf(repository.customBaseUrl(provider).orEmpty())
        private set
    var customModelId by mutableStateOf(repository.selectedModelId(provider).orEmpty())
        private set
    var status by mutableStateOf(statusFor(provider))
        private set

    fun selectProvider(value: AIProvider) {
        provider = value
        apiKey = ""
        customBaseUrl = repository.customBaseUrl(value).orEmpty()
        customModelId = repository.selectedModelId(value).orEmpty()
        repository.setSelectedProvider(value)
        status = statusFor(value)
    }

    fun updateApiKey(value: String) { apiKey = value }
    fun updateCustomBaseUrl(value: String) { customBaseUrl = value }
    fun updateCustomModelId(value: String) { customModelId = value }

    fun save() {
        val value = apiKey.trim()
        if (value.isBlank()) {
            status = "Enter an API key first."
            return
        }
        runCatching {
            if (provider == AIProvider.OPENAI_COMPATIBLE) {
                AIProviderRegistry.validateCustomBaseUrl(customBaseUrl.trim())
                if (customModelId.isNotBlank()) {
                    require(customModelId.trim().length <= 180) { "Custom model ID is too long." }
                    require(Regex("^[A-Za-z0-9_.:/-]+$").matches(customModelId.trim())) { "Custom model ID contains unsupported characters." }
                }
                repository.setCustomBaseUrl(provider, customBaseUrl.trim())
                if (customModelId.isNotBlank()) repository.setSelectedModelId(provider, customModelId.trim())
                else repository.clearSelectedModelId(provider)
            }
            repository.saveApiKey(provider, value)
        }.onSuccess {
            apiKey = ""
            status = if (provider == AIProvider.OPENAI_COMPATIBLE) {
                "Saved securely. The custom endpoint will be queried for models; the saved model ID is used as fallback."
            } else {
                "Saved securely. Open Chat and tap the model dropdown to load models."
            }
        }.onFailure { error -> status = friendlyCredentialError(error, "Unable to save the provider settings.") }
    }

    private fun statusFor(value: AIProvider): String = when {
        repository.isApiKeyLocked(value) -> "API key is stored with biometric protection. Unlock protected credentials before using or changing it."
        value == AIProvider.OPENAI_COMPATIBLE && repository.hasApiKey(value) -> {
            if (repository.customBaseUrl(value).isNullOrBlank()) "API key is configured, but the custom base URL is missing."
            else "API key and custom endpoint are configured."
        }
        repository.hasApiKey(value) -> "API key is configured."
        else -> "No API key saved for this provider."
    }

    fun remove() {
        runCatching { repository.removeApiKey(provider) }
            .onSuccess {
                apiKey = ""
                status = "API key removed."
            }
            .onFailure { error -> status = friendlyCredentialError(error, "Unable to remove the API key.") }
    }

    private fun friendlyCredentialError(error: Throwable, fallback: String): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("KeyGenParameterSpec", ignoreCase = true) ||
                message.contains("AndroidKeyStore", ignoreCase = true) ||
                message.contains("keystore", ignoreCase = true) ->
                "Android Keystore could not initialize. No credential was stored."
            message.contains("biometric authentication", ignoreCase = true) ->
                "Unlock protected credentials before changing this key."
            else -> fallback
        }
    }

}
