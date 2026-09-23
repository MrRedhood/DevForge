package com.mrredhood.devforge.core.extension

import android.content.Context
import java.net.URI

class DevForgeMarketplaceConfig(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBaseUrl(): String = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()

    fun setBaseUrl(value: String): Result<String> = runCatching {
        val normalized = value.trim().removeSuffix("/")
        require(normalized.isBlank() || URI(normalized).scheme == "https") {
            "Marketplace server URL must use HTTPS."
        }
        if (normalized.isNotBlank()) {
            require(!URI(normalized).host.isNullOrBlank()) { "Marketplace server URL is invalid." }
        }
        preferences.edit().putString(KEY_BASE_URL, normalized).apply()
        normalized
    }

    fun clear() {
        preferences.edit().remove(KEY_BASE_URL).apply()
    }

    fun isConfigured(): Boolean = getBaseUrl().isNotBlank()

    fun resolve(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("https://", ignoreCase = true)) return pathOrUrl
        val base = getBaseUrl().trimEnd('/')
        require(base.isNotBlank()) { "DevForge Marketplace server is not configured." }
        return base + "/" + pathOrUrl.trimStart('/')
    }

    fun isSameHost(url: String): Boolean {
        val baseUri = runCatching { URI(getBaseUrl()) }.getOrNull() ?: return false
        val targetUri = runCatching { URI(url) }.getOrNull() ?: return false
        return !baseUri.host.isNullOrBlank() &&
            baseUri.host.equals(targetUri.host, ignoreCase = true)
    }

    companion object {
        private const val PREFS = "devforge_marketplace"
        private const val KEY_BASE_URL = "base_url"
        const val DEFAULT_BASE_URL = ""
    }
}
