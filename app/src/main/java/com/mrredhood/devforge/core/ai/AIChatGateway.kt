package com.mrredhood.devforge.core.ai

import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class AIChatGateway {
    suspend fun send(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
    ): String {
        return when (model.provider) {
            AIProvider.GEMINI -> sendGemini(model.id, apiKey, history, userInstruction)
            AIProvider.OPENROUTER -> sendOpenAiCompatible("https://openrouter.ai/api/v1/chat/completions", apiKey, model.id, history, userInstruction, openRouter = true)
            AIProvider.OPENAI -> sendOpenAiCompatible("https://api.openai.com/v1/chat/completions", apiKey, model.id, history, userInstruction, openRouter = false)
        }
    }

    fun stream(
        model: AIModelInfo,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
    ): Flow<String> = flow {
        when (model.provider) {
            AIProvider.GEMINI -> streamGemini(model.id, apiKey, history, userInstruction).collect { emit(it) }
            AIProvider.OPENROUTER -> streamOpenAiCompatible(
                "https://openrouter.ai/api/v1/chat/completions", apiKey, model.id, history, userInstruction, true,
            ).collect { emit(it) }
            AIProvider.OPENAI -> streamOpenAiCompatible(
                "https://api.openai.com/v1/chat/completions", apiKey, model.id, history, userInstruction, false,
            ).collect { emit(it) }
        }
    }

    private fun streamGemini(
        modelId: String,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
    ): Flow<String> = flow {
        val contents = buildContents(history, userInstruction)
        val connection = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?alt=sse",
        ).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.setRequestProperty("Content-Type", "application/json")
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use { it.write(JSONObject().put("contents", contents).toString().toByteArray(Charsets.UTF_8)) }
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
                    val line = reader.readLine() ?: break
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
                        require(output.length <= MAX_STREAM_CHARS) { "AI response exceeded the streaming response limit." }
                        emit(chunk)
                    }
                }
            }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun streamOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        modelId: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        openRouter: Boolean,
    ): Flow<String> = flow {
        val messages = JSONArray()
        history.forEach { (role, content) -> messages.put(JSONObject().put("role", role).put("content", content)) }
        messages.put(JSONObject().put("role", "user").put("content", userInstruction))
        val body = JSONObject().put("model", modelId).put("messages", messages).put("stream", true)
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
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
                val message = runCatching { JSONObject(detail).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                error(message.ifBlank { "AI streaming request failed (HTTP $status)." })
            }
            stream?.bufferedReader()?.use { reader ->
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]" || data.isBlank()) continue
                    val chunk = runCatching {
                        JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optString("content").orEmpty()
                    }.getOrDefault("")
                    if (chunk.isNotBlank()) {
                        total += chunk.length
                        require(total <= MAX_STREAM_CHARS) { "AI response exceeded the streaming response limit." }
                        emit(chunk)
                    }
                }
            }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun buildContents(history: List<Pair<String, String>>, userInstruction: String): JSONArray {
        val contents = JSONArray()
        history.filter { it.first == "user" || it.first == "assistant" }.forEach { (role, content) ->
            contents.put(JSONObject().put("role", if (role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", content))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userInstruction))))
        return contents
    }

    private fun sendGemini(
        modelId: String,
        apiKey: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
    ): String {
        val contents = JSONArray()
        history.filter { it.first == "user" || it.first == "assistant" }.forEach { (role, content) ->
            contents.put(
                JSONObject()
                    .put("role", if (role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", content))),
            )
        }
        contents.put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userInstruction))),
        )
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

    private fun sendOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        modelId: String,
        history: List<Pair<String, String>>,
        userInstruction: String,
        openRouter: Boolean,
    ): String {
        val messages = JSONArray()
        history.forEach { (role, content) -> messages.put(JSONObject().put("role", role).put("content", content)) }
        messages.put(JSONObject().put("role", "user").put("content", userInstruction))
        val body = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", false)
        val headers = mutableMapOf("Authorization" to "Bearer $apiKey", "Content-Type" to "application/json")
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

    private fun request(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.use { it.readBounded(MAX_RESPONSE_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
            error(detail ?: "AI request failed (HTTP $status).")
        }
        return JSONObject(response)
    }

    private companion object {
        const val MAX_STREAM_CHARS = 512 * 1024
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_ERROR_BYTES = 16 * 1024
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
