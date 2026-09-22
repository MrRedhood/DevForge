package com.mrredhood.devforge.core.ai

enum class AIProvider(
    val id: String,
    val displayName: String,
) {
    GEMINI("gemini", "Google Gemini"),
    OPENROUTER("openrouter", "OpenRouter"),
    OPENAI("openai", "OpenAI"),
    ANTHROPIC("anthropic", "Anthropic Claude"),
    XAI_GROK("xai_grok", "xAI Grok"),
    DEEPINFRA("deepinfra", "DeepInfra"),
    GROQ("groq", "Groq"),
    OPENAI_COMPATIBLE("openai_compatible", "OpenAI compatible"),
}

enum class ModelPriceClass { FREE, PAID, UNKNOWN }

enum class ModelFilter(val label: String) {
    ALL("All"),
    FREE("Free"),
    PAID("Paid"),
    VOICE("Voice"),
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    EMBEDDING("Embedding"),
    TOOLS("Tools"),
}

data class AIModelInfo(
    val provider: AIProvider,
    val id: String,
    val displayName: String,
    val description: String = "",
    val priceClass: ModelPriceClass = ModelPriceClass.UNKNOWN,
    val inputPricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val contextLimit: Long? = null,
    val outputTokenLimit: Long? = null,
    val inputModalities: Set<String> = setOf("text"),
    val outputModalities: Set<String> = setOf("text"),
    val supportsTools: Boolean = false,
    val supportsStreaming: Boolean = true,
    /** Provider-declared request parameters, when the catalog exposes them. */
    val supportedParameters: Set<String> = emptySet(),
    val metadataSource: String = "provider",
) {
    val isTextCapable: Boolean get() = "text" in inputModalities || inputModalities.isEmpty()
    val isVoiceCapable: Boolean get() = inputModalities.any { it in setOf("audio", "speech", "voice") } || outputModalities.any { it in setOf("audio", "speech", "voice") }
    val isImageCapable: Boolean get() = inputModalities.contains("image") || outputModalities.contains("image")
    val isVideoCapable: Boolean get() = inputModalities.contains("video") || outputModalities.contains("video")
    val isEmbedding: Boolean get() = id.contains("embedding", ignoreCase = true) || outputModalities.contains("embedding")
}

data class ModelFilters(
    val pricing: ModelPriceClass? = null,
    val capability: ModelFilter = ModelFilter.ALL,
)

data class ModelCatalogResult(
    val models: List<AIModelInfo>,
    val provider: AIProvider,
    val fromCache: Boolean = false,
    val warning: String? = null,
)

data class AIProviderState(
    val provider: AIProvider,
    val hasApiKey: Boolean,
    val selectedModelId: String? = null,
)

data class FileMention(
    val token: String,
    val query: String,
    val uri: String?,
    val name: String,
    val content: String? = null,
)

enum class AgentCommandSafety { READ_ONLY, PROPOSE_ACTION, SIDE_EFFECT }

data class AICommandDefinition(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String,
    val usage: String,
    val safety: AgentCommandSafety = AgentCommandSafety.READ_ONLY,
    val instruction: String,
)

data class AICommandInvocation(
    val command: AICommandDefinition,
    val arguments: String,
    val mentions: List<FileMention> = emptyList(),
) {
    fun toAgentInstruction(): String = buildString {
        append(command.instruction)
        if (arguments.isNotBlank()) append("\nArguments: ").append(arguments.trim())
        if (mentions.isNotEmpty()) {
            append("\nReferenced files:\n")
            mentions.forEach { mention ->
                append("- @").append(mention.name)
                if (!mention.content.isNullOrBlank()) append("\n").append(mention.content)
                append('\n')
            }
        }
    }
}
