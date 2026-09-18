package com.mrredhood.devforge.core.ai

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import org.json.JSONObject

class ModelCatalogService {
    fun load(provider: AIProvider, apiKey: String): ModelCatalogResult {
        return runCatching {
            when (provider) {
                AIProvider.GEMINI -> loadGemini(apiKey)
                AIProvider.OPENROUTER -> loadOpenRouter(apiKey)
                AIProvider.OPENAI -> loadOpenAi(apiKey)
            }
        }.getOrElse { error ->
            ModelCatalogResult(emptyList(), provider, warning = error.message ?: "Unable to load models.")
        }
    }

    fun resolveMissingContext(model: AIModelInfo): AIModelInfo {
        if (model.contextLimit != null) return model
        val context = resolveContextLimitFromWeb(model.id) ?: return model
        return model.copy(contextLimit = context, metadataSource = "${model.metadataSource} + web search")
    }

    private fun loadGemini(apiKey: String): ModelCatalogResult {
        val json = request(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
            headers = mapOf("x-goog-api-key" to apiKey, "Accept" to "application/json"),
        )
        val array = json.optJSONArray("models") ?: return ModelCatalogResult(emptyList(), AIProvider.GEMINI, warning = "Gemini returned no models.")
        val models = buildList {
            for (index in 0 until array.length()) {
                val model = array.optJSONObject(index) ?: continue
                val id = model.optString("baseModelId").ifBlank { model.optString("name").removePrefix("models/") }
                if (id.isBlank()) continue
                if (!isSafeModelId(id)) continue
                val methods = jsonArrayStrings(model.optJSONArray("supportedGenerationMethods"))
                val inputLimit = model.optLongOrNull("inputTokenLimit")
                val outputLimit = model.optLongOrNull("outputTokenLimit")
                val modalities = inferModalities(id, model.optString("description"))
                add(
                    AIModelInfo(
                        provider = AIProvider.GEMINI,
                        id = id,
                        displayName = model.optString("displayName", id),
                        description = model.optString("description"),
                        priceClass = if (id.contains("embedding", true)) ModelPriceClass.PAID else ModelPriceClass.UNKNOWN,
                        contextLimit = inputLimit,
                        outputTokenLimit = outputLimit,
                        inputModalities = modalities.first,
                        outputModalities = modalities.second,
                        supportsTools = methods.any { it.contains("generate", true) },
                        metadataSource = "Gemini API",
                    ),
                )
            }
        }
        return ModelCatalogResult(models.sortedBy { it.displayName.lowercase(Locale.US) }, AIProvider.GEMINI)
    }

    private fun loadOpenRouter(apiKey: String): ModelCatalogResult {
        val json = request(
            "https://openrouter.ai/api/v1/models",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Accept" to "application/json",
                "X-Title" to "DevForge",
            ),
        )
        val array = json.optJSONArray("data") ?: return ModelCatalogResult(emptyList(), AIProvider.OPENROUTER, warning = "OpenRouter returned no models.")
        val models = buildList {
            for (index in 0 until array.length()) {
                val model = array.optJSONObject(index) ?: continue
                val id = model.optString("id")
                if (id.isBlank()) continue
                val architecture = model.optJSONObject("architecture")
                val input = jsonArrayStrings(architecture?.optJSONArray("input_modalities"))
                val output = jsonArrayStrings(architecture?.optJSONArray("output_modalities"))
                val pricing = model.optJSONObject("pricing")
                val inputPrice = pricing?.optDoubleOrNull("prompt")?.times(1_000_000.0)
                val outputPrice = pricing?.optDoubleOrNull("completion")?.times(1_000_000.0)
                val priceClass = when {
                    id.endsWith(":free") -> ModelPriceClass.FREE
                    inputPrice == 0.0 && outputPrice == 0.0 -> ModelPriceClass.FREE
                    inputPrice != null || outputPrice != null -> ModelPriceClass.PAID
                    else -> ModelPriceClass.UNKNOWN
                }
                val supported = jsonArrayStrings(model.optJSONArray("supported_parameters"))
                val context = model.optLongOrNull("context_length")
                add(
                    AIModelInfo(
                        provider = AIProvider.OPENROUTER,
                        id = id,
                        displayName = model.optString("name", id),
                        description = model.optString("description"),
                        priceClass = priceClass,
                        inputPricePerMillion = inputPrice,
                        outputPricePerMillion = outputPrice,
                        contextLimit = context,
                        inputModalities = input.ifEmpty { setOf("text") },
                        outputModalities = output.ifEmpty { setOf("text") },
                        supportsTools = "tools" in supported || "tool_choice" in supported,
                        metadataSource = if (context != null) "OpenRouter API" else "OpenRouter API · web fallback available",
                    ),
                )
            }
        }
        return ModelCatalogResult(models.sortedBy { it.displayName.lowercase(Locale.US) }, AIProvider.OPENROUTER)
    }

    private fun loadOpenAi(apiKey: String): ModelCatalogResult {
        val json = request(
            "https://api.openai.com/v1/models",
            headers = mapOf("Authorization" to "Bearer $apiKey", "Accept" to "application/json"),
        )
        val array = json.optJSONArray("data") ?: return ModelCatalogResult(emptyList(), AIProvider.OPENAI, warning = "OpenAI returned no models.")
        val models = buildList {
            for (index in 0 until array.length()) {
                val model = array.optJSONObject(index) ?: continue
                val id = model.optString("id")
                if (id.isBlank()) continue
                val modalities = inferModalities(id, "")
                add(
                    AIModelInfo(
                        provider = AIProvider.OPENAI,
                        id = id,
                        displayName = id,
                        description = "Discovered from the OpenAI Models API.",
                        priceClass = ModelPriceClass.UNKNOWN,
                        contextLimit = null,
                        inputModalities = modalities.first,
                        outputModalities = modalities.second,
                        supportsTools = !id.contains("embedding", true),
                        metadataSource = "OpenAI API · web fallback available",
                    ),
                )
            }
        }
        return ModelCatalogResult(models.sortedBy { it.displayName.lowercase(Locale.US) }, AIProvider.OPENAI)
    }

    private fun request(url: String, headers: Map<String, String>): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.useCaches = true
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { it.readBounded(MAX_RESPONSE_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
        connection.disconnect()
        if (responseCode !in 200..299) error("Model catalog request failed (HTTP $responseCode).")
        return JSONObject(body)
    }

    private fun resolveContextLimitFromWeb(modelId: String): Long? {
        return runCatching {
            val query = URLEncoder.encode("$modelId context window context length tokens", "UTF-8")
            val connection = URL("https://html.duckduckgo.com/html/?q=$query").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "DevForge/0.1 Android")
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            val body = connection.inputStream.use { it.readBounded(MAX_WEB_RESPONSE_BYTES) }.toString(Charsets.UTF_8)
            connection.disconnect()
            val patterns = listOf(
                Regex("(?i)([0-9][0-9,]*(?:\\.[0-9]+)?)[ ]*(k|m)?[ ]*(?:token|tokens)[^<]{0,40}(?:context|context window|context length)"),
                Regex("(?i)(?:context window|context length)[^0-9]{0,40}([0-9][0-9,]*(?:\\.[0-9]+)?)[ ]*(k|m)?[ ]*(?:token|tokens)"),
            )
            patterns.asSequence().mapNotNull { regex ->
                regex.find(body)?.let { match -> parseCount(match.groupValues[1], match.groupValues.getOrNull(2).orEmpty()) }
            }.firstOrNull()
        }.getOrNull()
    }

    private fun parseCount(value: String, suffix: String): Long? {
        val number = value.replace(",", "").toDoubleOrNull() ?: return null
        val factor = when (suffix.lowercase(Locale.US)) {
            "m" -> 1_000_000.0
            "k" -> 1_000.0
            else -> 1.0
        }
        return (number * factor).toLong().takeIf { it > 0L }
    }

    private fun inferModalities(id: String, description: String): Pair<Set<String>, Set<String>> {
        val source = "$id $description".lowercase(Locale.US)
        val input = mutableSetOf("text")
        val output = mutableSetOf("text")
        if (source.contains("image") || source.contains("vision") || source.contains("multimodal")) input += "image"
        if (source.contains("video") || source.contains("veo")) input += "video"
        if (source.contains("audio") || source.contains("voice") || source.contains("live")) input += "audio"
        if (source.contains("tts") || source.contains("speech") || source.contains("voice")) output += "audio"
        if (source.contains("image") || source.contains("imagen")) output += "image"
        if (source.contains("video") || source.contains("veo")) output += "video"
        if (source.contains("embedding")) {
            input.clear(); input += "text"
            output.clear(); output += "embedding"
        }
        return input to output
    }

    private fun isSafeModelId(value: String): Boolean =
        value.length <= 180 && SAFE_MODEL_ID.matches(value) && !value.contains("..")

    private fun jsonArrayStrings(array: org.json.JSONArray?): Set<String> = buildSet {
        if (array == null) return@buildSet
        for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    private companion object {
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_WEB_RESPONSE_BYTES = 512 * 1024
        private val SAFE_MODEL_ID = Regex("^[A-Za-z0-9_.:/-]+$")
    }
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }


private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (total < maxBytes) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes - total))
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
    }
    if (total >= maxBytes) error("Model catalog response exceeded the DevForge response limit.")
    return output.toByteArray()
}