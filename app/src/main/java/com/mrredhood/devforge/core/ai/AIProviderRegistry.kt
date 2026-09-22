package com.mrredhood.devforge.core.ai

import java.net.URI

enum class AIWireProtocol { GEMINI, ANTHROPIC_MESSAGES, OPENAI_CHAT }

data class AIProviderSpec(
    val wireProtocol: AIWireProtocol,
    val defaultBaseUrl: String,
    val supportsModelCatalog: Boolean = true,
)

object AIProviderRegistry {
    fun spec(provider: AIProvider): AIProviderSpec = when (provider) {
        AIProvider.GEMINI -> AIProviderSpec(AIWireProtocol.GEMINI, "https://generativelanguage.googleapis.com/v1beta")
        AIProvider.OPENAI -> AIProviderSpec(AIWireProtocol.OPENAI_CHAT, "https://api.openai.com/v1")
        AIProvider.ANTHROPIC -> AIProviderSpec(AIWireProtocol.ANTHROPIC_MESSAGES, "https://api.anthropic.com/v1")
        AIProvider.XAI_GROK -> AIProviderSpec(AIWireProtocol.OPENAI_CHAT, "https://api.x.ai/v1")
        AIProvider.DEEPINFRA -> AIProviderSpec(AIWireProtocol.OPENAI_CHAT, "https://api.deepinfra.com/v1/openai")
        AIProvider.OPENROUTER -> AIProviderSpec(AIWireProtocol.OPENAI_CHAT, "https://openrouter.ai/api/v1")
        AIProvider.GROQ -> AIProviderSpec(AIWireProtocol.OPENAI_CHAT, "https://api.groq.com/openai/v1")
        AIProvider.OPENAI_COMPATIBLE -> AIProviderSpec(AIWireProtocol.OPENAI_CHAT, "", supportsModelCatalog = true)
    }

    fun baseUrl(provider: AIProvider, customBaseUrl: String?): String {
        val configured = customBaseUrl?.trim().orEmpty()
        return if (provider == AIProvider.OPENAI_COMPATIBLE) validateCustomBaseUrl(configured) else spec(provider).defaultBaseUrl
    }

    fun chatEndpoint(provider: AIProvider, customBaseUrl: String?, modelId: String? = null): String = when (spec(provider).wireProtocol) {
        AIWireProtocol.GEMINI -> {
            require(!modelId.isNullOrBlank()) { "Gemini model ID is required." }
            baseUrl(provider, customBaseUrl) + "/models/" + modelId + ":generateContent"
        }
        AIWireProtocol.ANTHROPIC_MESSAGES -> baseUrl(provider, customBaseUrl) + "/messages"
        AIWireProtocol.OPENAI_CHAT -> baseUrl(provider, customBaseUrl) + "/chat/completions"
    }

    fun streamingEndpoint(provider: AIProvider, customBaseUrl: String?, modelId: String? = null): String = when (spec(provider).wireProtocol) {
        AIWireProtocol.GEMINI -> {
            require(!modelId.isNullOrBlank()) { "Gemini model ID is required." }
            baseUrl(provider, customBaseUrl) + "/models/" + modelId + ":streamGenerateContent?alt=sse"
        }
        AIWireProtocol.ANTHROPIC_MESSAGES, AIWireProtocol.OPENAI_CHAT -> chatEndpoint(provider, customBaseUrl, modelId)
    }

    fun modelsEndpoint(provider: AIProvider, customBaseUrl: String?): String = baseUrl(provider, customBaseUrl) + "/models"

    fun validateCustomBaseUrl(value: String): String {
        require(value.length in 1..500) { "Custom provider base URL is required and must be at most 500 characters." }
        val normalized = value.removeSuffix("/")
        val uri = runCatching { URI(normalized) }.getOrElse { throw IllegalArgumentException("Custom provider base URL is invalid.") }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Custom provider base URL must not contain credentials, query parameters, or fragments."
        }
        require(!uri.host.isNullOrBlank()) { "Custom provider base URL must contain a host." }
        when (uri.scheme?.lowercase()) {
            "https" -> Unit
            "http" -> {
                val host = uri.host.lowercase()
                require(host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1") {
                    "Insecure HTTP is only allowed for localhost custom providers."
                }
            }
            else -> throw IllegalArgumentException("Custom provider base URL must use HTTPS.")
        }
        require(uri.path.orEmpty().split('/').none { it == ".." }) { "Custom provider base URL contains an unsafe path." }
        return normalized
    }
}
