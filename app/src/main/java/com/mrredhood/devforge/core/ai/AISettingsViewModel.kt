package com.mrredhood.devforge.core.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var isTestingConnection by mutableStateOf(false)
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
            val normalizedBaseUrl = if (provider == AIProvider.OPENAI_COMPATIBLE) {
                AIProviderRegistry.validateCustomBaseUrl(customBaseUrl.trim())
            } else null
            val normalizedModelId = if (provider == AIProvider.OPENAI_COMPATIBLE && customModelId.isNotBlank()) {
                customModelId.trim().also { model ->
                    require(model.length <= 180) { "Custom model ID is too long." }
                    require(Regex("^[A-Za-z0-9_.:/-]+$").matches(model)) { "Custom model ID contains unsupported characters." }
                }
            } else null
            repository.saveApiKey(provider, value)
            if (provider == AIProvider.OPENAI_COMPATIBLE) {
                repository.setCustomBaseUrl(provider, requireNotNull(normalizedBaseUrl))
                if (normalizedModelId != null) repository.setSelectedModelId(provider, normalizedModelId)
                else repository.clearSelectedModelId(provider)
            }
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

    fun testConnection() {
        if (isTestingConnection) return
        if (repository.isApiKeyLocked(provider)) {
            status = "Unlock protected credentials before testing this provider."
            return
        }
        val key = repository.getApiKey(provider)
        if (key.isNullOrBlank()) {
            status = "Enter and save an API key before testing the connection."
            return
        }
        isTestingConnection = true
        status = "Testing " + provider.displayName + " connection…"
        viewModelScope.launch(Dispatchers.IO) {
            val result = ModelCatalogService().load(provider, key, repository.customBaseUrl(provider))
            withContext(Dispatchers.Main.immediate) {
                isTestingConnection = false
                if (result.models.isNotEmpty()) {
                    status = "Connection successful. Discovered " + result.models.size + " model(s)."
                } else {
                    status = result.warning ?: "Provider responded without any models."
                }
            }
        }
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
