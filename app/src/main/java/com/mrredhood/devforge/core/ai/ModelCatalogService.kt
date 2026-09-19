package com.mrredhood.devforge.core.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class ModelCatalogService {
    suspend fun load(provider: AIProvider, apiKey: String, customBaseUrl: String? = null): ModelCatalogResult {
        return try {
            when (provider) {
                AIProvider.GEMINI -> loadGemini(apiKey)
                AIProvider.ANTHROPIC -> loadAnthropic(apiKey)
                AIProvider.XAI_GROK -> loadXai(apiKey)
                AIProvider.DEEPINFRA -> loadOpenAiStyle(AIProvider.DEEPINFRA, apiKey, customBaseUrl)
                AIProvider.OPENROUTER -> loadOpenRouter(apiKey)
                AIProvider.GROQ -> loadOpenAiStyle(AIProvider.GROQ, apiKey, customBaseUrl)
                AIProvider.OPENAI -> loadOpenAiStyle(AIProvider.OPENAI, apiKey, customBaseUrl)
                AIProvider.OPENAI_COMPATIBLE -> loadOpenAiStyle(AIProvider.OPENAI_COMPATIBLE, apiKey, customBaseUrl)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ModelCatalogResult(emptyList(), provider, warning = error.message ?: "Unable to load models.")
        }
    }

    fun resolveMissingContext(model: AIModelInfo): AIModelInfo = model

    private suspend fun loadGemini(apiKey: String): ModelCatalogResult {
        val json = request(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
            mapOf("x-goog-api-key" to apiKey, "Accept" to "application/json"),
        )
        val array = json.optJSONArray("models") ?: return ModelCatalogResult(emptyList(), AIProvider.GEMINI, warning = "Google returned no models.")
        val models = buildList {
            for (index in 0 until array.length()) {
                val model = array.optJSONObject(index) ?: continue
                val id = model.optString("baseModelId").ifBlank { model.optString("name").removePrefix("models/") }
                if (!isSafeModelId(id)) continue
                val methods = jsonArrayStrings(model.optJSONArray("supportedGenerationMethods"))
                val modalities = inferModalities(id, model.optString("description"))
                add(AIModelInfo(
                    provider = AIProvider.GEMINI,
                    id = id,
                    displayName = model.optString("displayName", id),
                    description = model.optString("description"),
                    priceClass = ModelPriceClass.UNKNOWN,
                    contextLimit = model.optLongOrNull("inputTokenLimit"),
                    outputTokenLimit = model.optLongOrNull("outputTokenLimit"),
                    inputModalities = modalities.first,
                    outputModalities = modalities.second,
                    supportsTools = methods.any { it.equals("generateContent", true) },
                    metadataSource = "Google Gemini API",
                ))
            }
        }
        return ModelCatalogResult(models.sortedBy { it.displayName.lowercase(Locale.US) }, AIProvider.GEMINI)
    }

    private suspend fun loadAnthropic(apiKey: String): ModelCatalogResult {
        val json = request(
            AIProviderRegistry.modelsEndpoint(AIProvider.ANTHROPIC, null),
            mapOf("x-api-key" to apiKey, "anthropic-version" to ANTHROPIC_VERSION, "Accept" to "application/json"),
        )
        val array = json.optJSONArray("data") ?: return ModelCatalogResult(emptyList(), AIProvider.ANTHROPIC, warning = "Anthropic returned no models.")
        val models = buildList {
            for (index in 0 until array.length()) {
                val model = array.optJSONObject(index) ?: continue
                val id = model.optString("id")
                if (!isSafeModelId(id)) continue
                add(AIModelInfo(
                    provider = AIProvider.ANTHROPIC,
                    id = id,
                    displayName = model.optString("display_name", id),
                    description = "Discovered from the Anthropic Models API.",
                    priceClass = ModelPriceClass.UNKNOWN,
                    contextLimit = model.optLongOrNull("context_window"),
                    outputTokenLimit = model.optLongOrNull("max_output_tokens"),
                    inputModalities = setOf("text"),
                    outputModalities = setOf("text"),
                    supportsTools = id.startsWith("claude-", true),
                    metadataSource = "Anthropic Models API",
                ))
            }
        }
        return ModelCatalogResult(models.sortedBy { it.displayName.lowercase(Locale.US) }, AIProvider.ANTHROPIC)
    }

    private suspend fun loadXai(apiKey: String): ModelCatalogResult =
        loadOpenAiStyle(AIProvider.XAI_GROK, apiKey, null)

    private suspend fun loadOpenRouter(apiKey: String): ModelCatalogResult {
        val json = request(
            AIProviderRegistry.modelsEndpoint(AIProvider.OPENROUTER, null),
            mapOf("Authorization" to "Bearer $apiKey", "Accept" to "application/json", "X-Title" to "DevForge"),
        )
        val array = json.optJSONArray("data") ?: return ModelCatalogResult(emptyList(), AIProvider.OPENROUTER, warning = "OpenRouter returned no models.")
        val models = buildOpenAiStyleModels(AIProvider.OPENROUTER, array)
        return ModelCatalogResult(models.sortedBy { it.displayName.lowercase(Locale.US) }, AIProvider.OPENROUTER)
    }

    private suspend fun loadOpenAiStyle(provider: AIProvider, apiKey: String, customBaseUrl: String?): ModelCatalogResult {
        val endpoint = AIProviderRegistry.modelsEndpoint(provider, customBaseUrl)
        val headers = mutableMapOf(
            "Authorization" to "Bearer ${apiKey.trim()}",
            "Accept" to "application/json",
        )
        if (provider == AIProvider.OPENROUTER) {
            headers["X-Title"] = "DevForge"
            headers["HTTP-Referer"] = "https://github.com/MrRedhood/DevForge"
            headers["User-Agent"] = "DevForge/0.1.0"
        }
        val json = request(endpoint, headers)
        val array = json.optJSONArray("data") ?: return ModelCatalogResult(emptyList(), provider, warning = provider.displayName + " returned no models.")
        val models = buildOpenAiStyleModels(provider, array)
        return ModelCatalogResult(models.sortedBy { it.displayName.lowercase(Locale.US) }, provider)
    }

    private fun buildOpenAiStyleModels(provider: AIProvider, array: JSONArray): List<AIModelInfo> = buildList {
        for (index in 0 until array.length()) {
            val model = array.optJSONObject(index) ?: continue
            val id = model.optString("id")
            if (!isSafeModelId(id)) continue
            val architecture = model.optJSONObject("architecture")
            val input = jsonArrayStrings(architecture?.optJSONArray("input_modalities"))
            val output = jsonArrayStrings(architecture?.optJSONArray("output_modalities"))
            val pricing = model.optJSONObject("pricing")
            val inputPrice = pricing?.optDoubleOrNull("prompt")?.let { it * 1_000_000.0 }
            val outputPrice = pricing?.optDoubleOrNull("completion")?.let { it * 1_000_000.0 }
            val priceClass = when {
                id.endsWith(":free", true) || (inputPrice == 0.0 && outputPrice == 0.0) -> ModelPriceClass.FREE
                inputPrice != null || outputPrice != null -> ModelPriceClass.PAID
                else -> ModelPriceClass.UNKNOWN
            }
            val inferred = inferModalities(id, model.optString("name"))
            add(AIModelInfo(
                provider = provider,
                id = id,
                displayName = model.optString("name", id),
                description = model.optString("description"),
                priceClass = priceClass,
                inputPricePerMillion = inputPrice,
                outputPricePerMillion = outputPrice,
                contextLimit = model.optLongOrNull("context_length") ?: model.optLongOrNull("context_window"),
                outputTokenLimit = model.optLongOrNull("max_output_tokens") ?: model.optLongOrNull("max_completion_tokens"),
                inputModalities = input.ifEmpty { inferred.first },
                outputModalities = output.ifEmpty { inferred.second },
                supportsTools = jsonArrayStrings(model.optJSONArray("supported_parameters")).any { it == "tools" || it == "tool_choice" },
                metadataSource = provider.displayName + " Models API",
            ))
        }
    }

    private suspend fun request(url: String, headers: Map<String, String>): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBounded(MAX_RESPONSE_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                error(message.ifBlank { "Model catalog request failed (HTTP $status)." })
            }
            JSONObject(body)
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun inferModalities(id: String, description: String): Pair<Set<String>, Set<String>> {
        val source = "$id $description".lowercase(Locale.US)
        val input = mutableSetOf("text")
        val output = mutableSetOf("text")
        if (source.contains("image") || source.contains("vision") || source.contains("multimodal")) input += "image"
        if (source.contains("video")) input += "video"
        if (source.contains("audio") || source.contains("voice") || source.contains("live")) input += "audio"
        if (source.contains("tts") || source.contains("speech") || source.contains("voice")) output += "audio"
        if (source.contains("image") || source.contains("imagen") || source.contains("image-generation")) output += "image"
        if (source.contains("video")) output += "video"
        if (source.contains("embedding")) {
            input.clear(); input += "text"
            output.clear(); output += "embedding"
        }
        return input to output
    }

    private fun isSafeModelId(value: String): Boolean =
        value.isNotBlank() && value.length <= 180 && SAFE_MODEL_ID.matches(value) && !value.contains("..")

    private fun jsonArrayStrings(array: JSONArray?): Set<String> = buildSet {
        if (array == null) return@buildSet
        for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        val SAFE_MODEL_ID = Regex("^[A-Za-z0-9_.:/-]+$")
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toLong().takeIf { it > 0L }
        is String -> value.trim().toLongOrNull()?.takeIf { it > 0L }
        else -> null
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }
}

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (total <= maxBytes) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes + 1 - total))
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
        if (total > maxBytes) error("Model catalog response exceeded the DevForge response limit.")
    }
    return output.toByteArray()
}
