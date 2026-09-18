package com.mrredhood.devforge.core.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
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
    ): String {
        validateRequest(model, apiKey, userInstruction)
        return when (model.provider) {
            AIProvider.GEMINI -> sendGemini(
                model.id,
                apiKey,
                history,
                userInstruction,
                attachments,
            )
            AIProvider.OPENROUTER -> sendOpenAiCompatible(
                "https://openrouter.ai/api/v1/chat/completions",
                apiKey,
                model.id,
                history,
                userInstruction,
                openRouter = true,
                attachments = attachments,
            )
            AIProvider.OPENAI -> sendOpenAiCompatible(
                "https://api.openai.com/v1/chat/completions",
                apiKey,
                model.id,
                history,
                userInstruction,
                openRouter = false,
                attachments = attachments,
            )
        }
    }

    fun stream(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment> = emptyList(),
    ): Flow<String> = flow {
        validateRequest(model, apiKey, userInstruction)
        when (model.provider) {
            AIProvider.GEMINI -> streamGemini(
                model.id,
                apiKey,
                history,
                userInstruction,
                attachments,
            ).collect { emit(it) }

            AIProvider.OPENROUTER -> streamOpenAiCompatible(
                "https://openrouter.ai/api/v1/chat/completions",
                apiKey,
                model.id,
                history,
                userInstruction,
                openRouter = true,
                attachments = attachments,
            ).collect { emit(it) }

            AIProvider.OPENAI -> streamOpenAiCompatible(
                "https://api.openai.com/v1/chat/completions",
                apiKey,
                model.id,
                history,
                userInstruction,
                openRouter = false,
                attachments = attachments,
            ).collect { emit(it) }
        }
    }

    private suspend fun streamGemini(
        modelId: String,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment>,
    ): Flow<String> = flow {
        val prepared = prepareAttachments(AIProvider.GEMINI, apiKey, attachments)
        val contents = buildContents(history, userInstruction, prepared)
        val connection = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?alt=sse",
        ).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.setRequestProperty("Content-Type", "application/json")
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use {
                it.write(
                    JSONObject()
                        .put("contents", contents)
                        .toString()
                        .toByteArray(Charsets.UTF_8),
                )
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            if (status !in 200..299) {
                val detail = stream?.use { it.readBounded(MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
                val message = runCatching { JSONObject(detail).optString("message") }.getOrNull().orEmpty()
                error(message.ifBlank { "AI streaming request failed (HTTP $status)." })
            }
            stream?.bufferedReader()?.use { reader ->
                val output = StringBuilder()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readBoundedLine(MAX_SSE_LINE_CHARS) ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank()) continue
                    val chunk = runCatching {
                        JSONObject(data)
                            .optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")
                            .orEmpty()
                    }.getOrDefault("")
                    if (chunk.isNotBlank()) {
                        output.append(chunk)
                        require(output.length <= MAX_STREAM_CHARS) {
                            "AI response exceeded the streaming response limit."
                        }
                        emit(chunk)
                    }
                }
            }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private suspend fun streamOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        modelId: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        openRouter: Boolean,
        attachments: List<ChatAttachment>,
    ): Flow<String> = flow {
        val prepared = prepareAttachments(
            if (openRouter) AIProvider.OPENROUTER else AIProvider.OPENAI,
            apiKey,
            attachments,
        )
        val messages = JSONArray()
        history.forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", buildOpenAiUserContent(userInstruction, prepared)),
        )
        val body = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", true)
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        if (openRouter) {
            connection.setRequestProperty("X-Title", "DevForge")
            connection.setRequestProperty("HTTP-Referer", "https://github.com/MrRedhood/DevForge")
        }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            if (status !in 200..299) {
                val detail = stream?.use { it.readBounded(MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
                val message = runCatching {
                    JSONObject(detail).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(message.ifBlank { "AI streaming request failed (HTTP $status)." })
            }
            stream?.bufferedReader()?.use { reader ->
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readBoundedLine(MAX_SSE_LINE_CHARS) ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]" || data.isBlank()) continue
                    val chunk = runCatching {
                        JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optString("content").orEmpty()
                    }.getOrDefault("")
                    if (chunk.isNotBlank()) {
                        total += chunk.length
                        require(total <= MAX_STREAM_CHARS) {
                            "AI response exceeded the streaming response limit."
                        }
                        emit(chunk)
                    }
                }
            }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun validateRequest(
        model: AIModelInfo,
        apiKey: String,
        userInstruction: String,
    ) {
        require(apiKey.isNotBlank() && apiKey.length <= MAX_API_KEY_CHARS) {
            "The AI credential is invalid or too large."
        }
        require(userInstruction.length <= MAX_INSTRUCTION_CHARS) {
            "The AI instruction exceeds the supported request limit."
        }
        val modelPattern = when (model.provider) {
            AIProvider.GEMINI -> GEMINI_MODEL_ID
            AIProvider.OPENROUTER, AIProvider.OPENAI -> SAFE_MODEL_ID
        }
        require(
            model.id.length <= MAX_MODEL_ID_CHARS &&
                modelPattern.matches(model.id) &&
                !model.id.contains("..") &&
                !model.id.contains('\\') &&
                !model.id.contains('?') &&
                !model.id.contains('#'),
        ) {
            "The selected AI model identifier is invalid."
        }
    }

    private suspend fun buildContents(
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ProviderPreparedAttachment>,
    ): JSONArray {
        val contents = JSONArray()
        history.filter { it.first == "user" || it.first == "assistant" }.forEach { (role, content) ->
            contents.put(
                JSONObject()
                    .put("role", if (role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", content))),
            )
        }

        val parts = JSONArray().put(JSONObject().put("text", userInstruction))
        attachments.forEach { attachment ->
            when (attachment) {
                is ProviderPreparedAttachment.FileUri -> {
                    parts.put(
                        JSONObject().put(
                            "file_data",
                            JSONObject()
                                .put("mime_type", attachment.mimeType)
                                .put("file_uri", attachment.uri),
                        ),
                    )
                }
            }
        }
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        return contents
    }

    private suspend fun sendGemini(
        modelId: String,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        attachments: List<ChatAttachment>,
    ): String {
        val prepared = prepareAttachments(AIProvider.GEMINI, apiKey, attachments)
        val contents = buildContents(history, userInstruction, prepared)
        val body = JSONObject().put("contents", contents)
        val json = request(
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent",
            body,
            mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json"),
        )
        val candidates = json.optJSONArray("candidates") ?: error("Gemini returned no candidates.")
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: error("Gemini returned no text content.")
        return buildString {
            for (index in 0 until parts.length()) {
                val text = parts.optJSONObject(index)?.optString("text").orEmpty()
                if (text.isNotBlank()) append(text)
            }
        }.ifBlank { "The model returned an empty response." }
    }

    private suspend fun sendOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        modelId: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        openRouter: Boolean,
        attachments: List<ChatAttachment>,
    ): String {
        val prepared = prepareAttachments(
            if (openRouter) AIProvider.OPENROUTER else AIProvider.OPENAI,
            apiKey,
            attachments,
        )
        val messages = JSONArray()
        history.forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", buildOpenAiUserContent(userInstruction, prepared)),
        )
        val body = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", false)
        val headers = mutableMapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
        )
        if (openRouter) {
            headers["X-Title"] = "DevForge"
            headers["HTTP-Referer"] = "https://github.com/MrRedhood/DevForge"
        }
        val json = request(endpoint, body, headers)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: error("Provider returned no choices.")
        return choice.optJSONObject("message")?.optString("content").orEmpty().ifBlank {
            "The model returned an empty response."
        }
    }

    private suspend fun prepareAttachments(
        provider: AIProvider,
        apiKey: String,
        attachments: List<ChatAttachment>,
    ): List<ProviderPreparedAttachment> {
        val binary = attachments.filter(::isBinaryAttachment)
        if (binary.isEmpty()) return emptyList()
        val adapter = attachmentAdapters[provider]
            ?: throw UnsupportedProviderAttachmentsException(provider)
        return adapter.prepare(apiKey, binary)
    }

    private fun buildOpenAiUserContent(
        userInstruction: String,
        attachments: List<ProviderPreparedAttachment>,
    ): Any {
        require(attachments.isEmpty()) {
            "The selected OpenAI-compatible attachment adapter returned an unsupported payload."
        }
        return userInstruction
    }

    private fun request(
        url: String,
        body: JSONObject,
        headers: Map<String, String>,
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readBounded(MAX_RESPONSE_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull()
                error(detail ?: "AI request failed (HTTP $status).")
            }
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_STREAM_CHARS = 512 * 1024
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_ERROR_BYTES = 16 * 1024
        private const val MAX_API_KEY_CHARS = 4_096
        private const val MAX_MODEL_ID_CHARS = 180
        private const val MAX_INSTRUCTION_CHARS = 1_000_000
        private const val MAX_SSE_LINE_CHARS = 128 * 1024
        private val GEMINI_MODEL_ID = Regex("^[A-Za-z0-9_.-]+$")
        private val SAFE_MODEL_ID = Regex("^[A-Za-z0-9_.:/-]+$")
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
            if (output.length > maxChars) {
                error("AI streaming event line exceeded the DevForge line limit.")
            }
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
