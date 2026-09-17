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
    var apiKey by mutableStateOf(repository.getApiKey(provider).orEmpty())
        private set
    var status by mutableStateOf(if (repository.hasApiKey(provider)) "API key is configured." else "No API key saved for this provider.")
        private set

    fun selectProvider(value: AIProvider) {
        provider = value
        apiKey = repository.getApiKey(value).orEmpty()
        repository.setSelectedProvider(value)
        status = if (repository.hasApiKey(value)) "API key is configured." else "No API key saved for this provider."
    }

    fun setApiKey(value: String) { apiKey = value }

    fun save() {
        val value = apiKey.trim()
        if (value.isBlank()) {
            status = "Enter an API key first."
            return
        }
        repository.saveApiKey(provider, value)
        apiKey = value
        status = "Saved securely. Open Chat and tap the model dropdown to load models."
    }

    fun remove() {
        repository.removeApiKey(provider)
        apiKey = ""
        status = "API key removed."
    }
}
