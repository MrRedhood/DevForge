package com.mrredhood.devforge.core.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AIChatGateway(
    private val attachmentAdapters: Map<AIProvider, ProviderAttachmentAdapter> = emptyMap(),
) {
    suspend fun send(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment> = emptyList(),
        customBaseUrl: String? = null,
    ): String {
        validateRequest(model, apiKey, userInstruction, customBaseUrl)
        return when (AIProviderRegistry.spec(model.provider).wireProtocol) {
            AIWireProtocol.GEMINI -> sendGemini(model, apiKey, history, userInstruction, attachments, customBaseUrl)
            AIWireProtocol.ANTHROPIC_MESSAGES -> sendAnthropic(model, apiKey, history, userInstruction, attachments, customBaseUrl, stream = false)
            AIWireProtocol.OPENAI_CHAT -> sendOpenAiCompatible(model.provider, apiKey, model.id, history, userInstruction, attachments, customBaseUrl, stream = false)
        }
    }

    fun stream(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment> = emptyList(),
        customBaseUrl: String? = null,
    ): Flow<String> = flow {
        validateRequest(model, apiKey, userInstruction, customBaseUrl)
        when (AIProviderRegistry.spec(model.provider).wireProtocol) {
            AIWireProtocol.GEMINI -> streamGemini(model, apiKey, history, userInstruction, attachments, customBaseUrl).collect(::emit)
            AIWireProtocol.ANTHROPIC_MESSAGES -> streamAnthropic(model, apiKey, history, userInstruction, attachments, customBaseUrl).collect(::emit)
            AIWireProtocol.OPENAI_CHAT -> streamOpenAiCompatible(model.provider, apiKey, model.id, history, userInstruction, attachments, customBaseUrl).collect(::emit)
        }
    }

    private suspend fun sendGemini(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment>,
        customBaseUrl: String?,
    ): String {
        val prepared = prepareAttachments(AIProvider.GEMINI, apiKey, attachments)
        val contents = buildGeminiContents(history, userInstruction, prepared)
        val json = request(
            AIProviderRegistry.chatEndpoint(AIProvider.GEMINI, customBaseUrl, model.id),
            JSONObject().put("contents", contents),
            mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json"),
        )
        val candidates = json.optJSONArray("candidates") ?: error("Google returned no candidates.")
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: error("Google returned no text content.")
        return buildString {
            for (index in 0 until parts.length()) {
                val text = parts.optJSONObject(index)?.optString("text").orEmpty()
                if (text.isNotBlank()) append(text)
            }
        }.ifBlank { "The model returned an empty response." }
    }

    private suspend fun streamGemini(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment>,
        customBaseUrl: String?,
    ): Flow<String> = flow {
        val prepared = prepareAttachments(AIProvider.GEMINI, apiKey, attachments)
        val contents = buildGeminiContents(history, userInstruction, prepared)
        val connection = URL(AIProviderRegistry.streamingEndpoint(AIProvider.GEMINI, customBaseUrl, model.id)).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.setRequestProperty("Content-Type", "application/json")
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use { it.write(JSONObject().put("contents", contents).toString().toByteArray(Charsets.UTF_8)) }
            emitSseResponse(connection, ::parseGeminiChunk)
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private suspend fun sendAnthropic(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment>,
        customBaseUrl: String?,
        stream: Boolean,
    ): String {
        // Anthropic currently has no provider attachment adapter in DevForge.
        // Explicitly validate binary attachments so they cannot be silently dropped.
        prepareAttachments(AIProvider.ANTHROPIC, apiKey, attachments)
        val messages = buildAnthropicMessages(history, userInstruction)
        val body = JSONObject()
            .put("model", model.id)
            .put("max_tokens", maxAnthropicOutputTokens(model))
            .put("messages", messages)
            .put("stream", stream)
        val json = request(
            AIProviderRegistry.chatEndpoint(AIProvider.ANTHROPIC, customBaseUrl),
            body,
            anthropicHeaders(apiKey),
        )
        return json.optJSONArray("content")?.let { content ->
            buildString {
                for (index in 0 until content.length()) {
                    val block = content.optJSONObject(index) ?: continue
                    if (block.optString("type") == "text") append(block.optString("text"))
                }
            }
        }?.ifBlank { "The model returned an empty response." }
            ?: error("Anthropic returned no text content.")
    }

    private fun streamAnthropic(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        customBaseUrl: String?,
    ): Flow<String> = flow {
        val body = JSONObject()
            .put("model", model.id)
            .put("max_tokens", maxAnthropicOutputTokens(model))
            .put("messages", buildAnthropicMessages(history, userInstruction))
            .put("stream", true)
        val connection = URL(AIProviderRegistry.chatEndpoint(AIProvider.ANTHROPIC, customBaseUrl)).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        anthropicHeaders(apiKey).forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            emitSseResponse(connection) { data ->
                runCatching {
                    val json = JSONObject(data)
                    if (json.optString("type") != "content_block_delta") return@runCatching ""
                    val delta = json.optJSONObject("delta") ?: return@runCatching ""
                    if (delta.optString("type") != "text_delta") return@runCatching ""
                    delta.optString("text")
                }.getOrDefault("")
            }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun streamOpenAiCompatible(
        provider: AIProvider,
        apiKey: String,
        modelId: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment>,
        customBaseUrl: String?,
    ): Flow<String> = flow {
        val prepared = prepareAttachments(provider, apiKey, attachments)
        val messages = JSONArray()
        history.forEach { (role, content) -> messages.put(JSONObject().put("role", role).put("content", content)) }
        messages.put(JSONObject().put("role", "user").put("content", buildOpenAiUserContent(userInstruction, prepared)))
        val body = JSONObject().put("model", modelId).put("messages", messages).put("stream", true)
        val headers = openAiHeaders(provider, apiKey)
        val connection = URL(AIProviderRegistry.chatEndpoint(provider, customBaseUrl)).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            emitSseResponse(connection) { data ->
                runCatching {
                    JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                }.getOrDefault("")
            }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private suspend fun sendOpenAiCompatible(
        provider: AIProvider,
        apiKey: String,
        modelId: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment>,
        customBaseUrl: String?,
        stream: Boolean,
    ): String {
        val prepared = prepareAttachments(provider, apiKey, attachments)
        val messages = JSONArray()
        history.forEach { (role, content) -> messages.put(JSONObject().put("role", role).put("content", content)) }
        messages.put(JSONObject().put("role", "user").put("content", buildOpenAiUserContent(userInstruction, prepared)))
        val body = JSONObject().put("model", modelId).put("messages", messages).put("stream", stream)
        val json = request(AIProviderRegistry.chatEndpoint(provider, customBaseUrl), body, openAiHeaders(provider, apiKey))
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: error(provider.displayName + " returned no choices.")
        return extractOpenAiContent(choice).ifBlank { "The model returned an empty response." }
    }

    private fun extractOpenAiContent(choice: JSONObject): String = when (val content = choice.optJSONObject("message")?.opt("content")) {
        is String -> content
        is JSONArray -> buildString {
            for (index in 0 until content.length()) {
                val part = content.optJSONObject(index) ?: continue
                append(part.optString("text"))
            }
        }
        else -> ""
    }

    private fun buildGeminiContents(history: List<Pair<String, String>>, userInstruction: String, attachments: List<ProviderPreparedAttachment>): JSONArray {
        val contents = JSONArray()
        history.filter { it.first == "user" || it.first == "assistant" }.forEach { (role, content) ->
            contents.put(JSONObject().put("role", if (role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", content))))
        }
        val parts = JSONArray().put(JSONObject().put("text", userInstruction))
        attachments.forEach { attachment ->
            when (attachment) {
                is ProviderPreparedAttachment.FileUri -> parts.put(JSONObject().put("file_data", JSONObject().put("mime_type", attachment.mimeType).put("file_uri", attachment.uri)))
            }
        }
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        return contents
    }

    private fun buildAnthropicMessages(history: List<Pair<String, String>>, userInstruction: String): JSONArray {
        val messages = JSONArray()
        history.filter { it.first == "user" || it.first == "assistant" }.forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userInstruction))
        return messages
    }

    private fun anthropicHeaders(apiKey: String): Map<String, String> = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to ANTHROPIC_VERSION,
        "Content-Type" to "application/json",
        "Accept" to "application/json",
    )

    private fun openAiHeaders(provider: AIProvider, apiKey: String): Map<String, String> = buildMap {
        put("Authorization", "Bearer $apiKey")
        put("Content-Type", "application/json")
        if (provider == AIProvider.OPENROUTER) {
            put("X-Title", "DevForge")
            put("HTTP-Referer", "https://github.com/MrRedhood/DevForge")
        }
    }

    private fun maxAnthropicOutputTokens(model: AIModelInfo): Int =
        (model.outputTokenLimit ?: 8_192L).coerceIn(1L, 32_000L).toInt()

    private fun validateRequest(model: AIModelInfo, apiKey: String, userInstruction: String, customBaseUrl: String?) {
        require(apiKey.isNotBlank() && apiKey.length <= MAX_API_KEY_CHARS) { "The AI credential is invalid or too large." }
        require(userInstruction.length <= MAX_INSTRUCTION_CHARS) { "The AI instruction exceeds the supported request limit." }
        require(model.id.length <= MAX_MODEL_ID_CHARS && SAFE_MODEL_ID.matches(model.id) && !model.id.contains("..") && !model.id.contains('\\') && !model.id.contains('?') && !model.id.contains('#')) {
            "The selected AI model identifier is invalid."
        }
        if (model.provider == AIProvider.OPENAI_COMPATIBLE) AIProviderRegistry.validateCustomBaseUrl(customBaseUrl.orEmpty())
    }

    private suspend fun prepareAttachments(provider: AIProvider, apiKey: String, attachments: List<ChatAttachment>): List<ProviderPreparedAttachment> {
        val binary = attachments.filter(::isBinaryAttachment)
        if (binary.isEmpty()) return emptyList()
        val adapter = attachmentAdapters[provider] ?: throw UnsupportedProviderAttachmentsException(provider)
        return adapter.prepare(apiKey, binary)
    }

    private fun buildOpenAiUserContent(userInstruction: String, attachments: List<ProviderPreparedAttachment>): Any {
        require(attachments.isEmpty()) { "The selected OpenAI-compatible attachment adapter returned an unsupported payload." }
        return userInstruction
    }

    private suspend fun FlowCollector<String>.emitSseResponse(connection: HttpURLConnection, parser: (String) -> String) {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (status !in 200..299) {
            val detail = stream?.use { it.readBounded(MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            val message = parseErrorMessage(detail)
            error(message.ifBlank { "AI streaming request failed (HTTP $status)." })
        }
        stream?.bufferedReader()?.use { reader ->
            var total = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readBoundedLine(MAX_SSE_LINE_CHARS) ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isBlank() || data == "[DONE]") continue
                val chunk = parser(data)
                if (chunk.isNotBlank()) {
                    total += chunk.length
                    require(total <= MAX_STREAM_CHARS) { "AI response exceeded the streaming response limit." }
                    emit(chunk)
                }
            }
        }
    }

    private fun parseGeminiChunk(data: String): String = runCatching {
        JSONObject(data).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
    }.getOrDefault("")

    private fun parseErrorMessage(body: String): String = runCatching {
        val root = JSONObject(body)
        root.optString("message").ifBlank { root.optJSONObject("error")?.optString("message").orEmpty() }
    }.getOrDefault("")

    private suspend fun request(url: String, body: JSONObject, headers: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return@withContext try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readBounded(MAX_RESPONSE_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            if (status !in 200..299) error(parseErrorMessage(response).ifBlank { "AI request failed (HTTP $status)." })
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MAX_STREAM_CHARS = 512 * 1024
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_ERROR_BYTES = 16 * 1024
        const val MAX_API_KEY_CHARS = 4_096
        const val MAX_MODEL_ID_CHARS = 180
        const val MAX_INSTRUCTION_CHARS = 1_000_000
        const val MAX_SSE_LINE_CHARS = 128 * 1024
        val SAFE_MODEL_ID = Regex("^[A-Za-z0-9_.:/-]+$")
    }
}

private fun java.io.BufferedReader.readBoundedLine(maxChars: Int): String? {
    require(maxChars > 0)
    val output = StringBuilder(minOf(maxChars, 4 * 1024))
    val buffer = CharArray(minOf(4 * 1024, maxChars + 1))
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, maxChars + 1 - output.length))
        if (count < 0) return if (output.isEmpty()) null else output.toString()
        for (index in 0 until count) {
            val char = buffer[index]
            if (char == '\n') return output.toString().removeSuffix("\r")
            output.append(char)
            if (output.length > maxChars) error("AI streaming event line exceeded the DevForge line limit.")
        }
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
        if (total > maxBytes) error("AI response exceeded the DevForge response limit.")
    }
    return output.toByteArray()
}
