package com.mrredhood.devforge.core.ai

import android.content.Context
import com.mrredhood.devforge.core.security.AndroidSecretStore

class AISettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val secrets = AndroidSecretStore(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun selectedProvider(): AIProvider = AIProvider.entries.firstOrNull {
        it.id == preferences.getString(KEY_PROVIDER, null)
    } ?: AIProvider.GEMINI

    fun setSelectedProvider(provider: AIProvider) {
        preferences.edit().putString(KEY_PROVIDER, provider.id).apply()
    }

    fun selectedModelId(provider: AIProvider): String? =
        preferences.getString("$KEY_MODEL_PREFIX${provider.id}", null)

    fun setSelectedModelId(provider: AIProvider, modelId: String) {
        preferences.edit().putString("$KEY_MODEL_PREFIX${provider.id}", modelId).apply()
    }

    fun getApiKey(provider: AIProvider): String? = secrets.get(secretKey(provider))?.trim()?.ifBlank { null }

    fun hasApiKey(provider: AIProvider): Boolean = !getApiKey(provider).isNullOrBlank()

    fun saveApiKey(provider: AIProvider, apiKey: String) {
        secrets.put(secretKey(provider), apiKey.trim())
        setSelectedProvider(provider)
    }

    fun removeApiKey(provider: AIProvider) {
        secrets.remove(secretKey(provider))
    }

    companion object {
        private const val PREFERENCES = "devforge_ai_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_MODEL_PREFIX = "model."

        private fun secretKey(provider: AIProvider): String = "devforge.ai.${provider.id}.api_key"
    }
}
