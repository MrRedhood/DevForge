package com.mrredhood.devforge.core.ai

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

interface ProviderAttachmentAdapter {
    val provider: AIProvider
    suspend fun prepare(apiKey: String, attachments: List<ChatAttachment>): List<ProviderPreparedAttachment>
}

sealed interface ProviderPreparedAttachment {
    data class FileUri(
        val name: String,
        val mimeType: String,
        val uri: String,
    ) : ProviderPreparedAttachment

    data class OpenAiContentPart(
        val name: String,
        val mimeType: String,
        val base64Data: String,
    ) : ProviderPreparedAttachment
}

class UnsupportedProviderAttachmentsException(provider: AIProvider) :
    IllegalStateException(
        "Binary attachments are not supported for " + provider.displayName + ". " +
            "Choose a compatible model/provider or attach the file as text when possible.",
    )

class GeminiProviderAttachmentAdapter(private val resolver: ContentResolver) : ProviderAttachmentAdapter {
    override val provider: AIProvider = AIProvider.GEMINI

    override suspend fun prepare(apiKey: String, attachments: List<ChatAttachment>): List<ProviderPreparedAttachment> {
        val binary = attachments.filterNot(::isTextLikeAttachment)
        if (binary.isEmpty()) return emptyList()
        return binary.map { upload(it, apiKey) }
    }

    private suspend fun upload(attachment: ChatAttachment, apiKey: String): ProviderPreparedAttachment.FileUri {
        require(attachment.sizeBytes in 1L..MAX_UPLOAD_BYTES) { "Attachment exceeds the provider upload safety limit." }
        val start = URL("https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + Uri.encode(apiKey))
            .openConnection() as HttpURLConnection
        start.requestMethod = "POST"
        start.instanceFollowRedirects = false
        start.doOutput = true
        start.connectTimeout = CONNECT_TIMEOUT_MS
        start.readTimeout = READ_TIMEOUT_MS
        start.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
        start.setRequestProperty("X-Goog-Upload-Command", "start")
        start.setRequestProperty("X-Goog-Upload-Header-Content-Length", attachment.sizeBytes.toString())
        start.setRequestProperty("X-Goog-Upload-Header-Content-Type", attachment.mimeType)
        start.setRequestProperty("Content-Type", "application/json")
        val uploadUrl = try {
            start.outputStream.use { it.write(JSONObject().put("file", JSONObject().put("display_name", attachment.name.take(512))).toString().toByteArray(Charsets.UTF_8)) }
            currentCoroutineContext().ensureActive()
            val code = start.responseCode
            val detail = (if (code in 200..299) start.inputStream else start.errorStream)?.use { it.readBounded(MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            require(code in 200..299) { parseProviderError(detail).ifBlank { "Gemini attachment upload initialization failed (HTTP $code)." } }
            start.getHeaderField("x-goog-upload-url") ?: start.getHeaderField("X-Goog-Upload-URL")
                ?: error("Gemini did not return a resumable upload URL.")
        } finally { start.disconnect() }

        val upload = URL(uploadUrl).openConnection() as HttpURLConnection
        upload.requestMethod = "POST"
        upload.instanceFollowRedirects = false
        upload.doOutput = true
        upload.connectTimeout = CONNECT_TIMEOUT_MS
        upload.readTimeout = READ_TIMEOUT_MS
        upload.setRequestProperty("X-Goog-Upload-Offset", "0")
        upload.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
        upload.setRequestProperty("Content-Length", attachment.sizeBytes.toString())
        upload.setRequestProperty("Content-Type", attachment.mimeType)
        return try {
            val input = resolver.openInputStream(attachment.uri) ?: throw IOException("Unable to open attachment.")
            input.use { source -> upload.outputStream.use { sink -> copyExactlyBounded(source, sink, attachment.sizeBytes) } }
            currentCoroutineContext().ensureActive()
            val code = upload.responseCode
            val detail = (if (code in 200..299) upload.inputStream else upload.errorStream)?.use { it.readBounded(MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            require(code in 200..299) { parseProviderError(detail).ifBlank { "Gemini attachment upload failed (HTTP $code)." } }
            val file = JSONObject(detail).optJSONObject("file") ?: error("Gemini returned an invalid attachment response.")
            val remoteUri = file.optString("uri").trim()
            require(remoteUri.startsWith("https://", true)) { "Gemini returned an invalid attachment URI." }
            ProviderPreparedAttachment.FileUri(attachment.name, file.optString("mimeType").ifBlank { attachment.mimeType }, remoteUri)
        } finally { upload.disconnect() }
    }

    private suspend fun copyExactlyBounded(source: java.io.InputStream, target: java.io.OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (total < expectedBytes) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), expectedBytes - total).toInt())
            require(read >= 0) { "Attachment ended before its declared size." }
            if (read == 0) continue
            target.write(buffer, 0, read)
            total += read
        }
        require(total == expectedBytes && source.read() == -1) { "Attachment size changed while uploading." }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 120_000
        const val MAX_ERROR_BYTES = 16 * 1024
        const val MAX_UPLOAD_BYTES = 50L * 1024L * 1024L
    }
}

class OpenRouterProviderAttachmentAdapter(private val resolver: ContentResolver) : ProviderAttachmentAdapter {
    override val provider: AIProvider = AIProvider.OPENROUTER

    override suspend fun prepare(apiKey: String, attachments: List<ChatAttachment>): List<ProviderPreparedAttachment> =
        attachments.filter(::isBinaryAttachment).map { attachment ->
            require(attachment.sizeBytes in 1L..MAX_REQUEST_FILE_BYTES) {
                "OpenRouter attachment '" + attachment.name + "' exceeds the 10 MiB inline safety limit."
            }
            ProviderPreparedAttachment.OpenAiContentPart(
                name = attachment.name,
                mimeType = attachment.mimeType,
                base64Data = readBase64(attachment),
            )
        }

    private suspend fun readBase64(attachment: ChatAttachment): String {
        val input = resolver.openInputStream(attachment.uri) ?: throw IOException("Unable to open " + attachment.name + ".")
        val bytes = input.use { source ->
            val output = java.io.ByteArrayOutputStream(attachment.sizeBytes.coerceAtMost(MAX_REQUEST_FILE_BYTES).toInt())
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (total <= MAX_REQUEST_FILE_BYTES) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                require(total <= MAX_REQUEST_FILE_BYTES) { "OpenRouter attachment exceeds the 10 MiB inline safety limit." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private companion object { const val MAX_REQUEST_FILE_BYTES = 10L * 1024L * 1024L }
}

fun isTextLikeAttachment(attachment: ChatAttachment): Boolean = isTextLikeAttachment(attachment.name, attachment.mimeType)

fun isTextLikeAttachment(name: String, mimeType: String): Boolean =
    mimeType.startsWith("text/") ||
        mimeType == "application/json" || mimeType == "application/xml" || mimeType == "application/rtf" || mimeType == "text/csv" ||
        name.endsWith(".kt", true) || name.endsWith(".java", true) || name.endsWith(".js", true) || name.endsWith(".ts", true) ||
        name.endsWith(".py", true) || name.endsWith(".md", true) || name.endsWith(".json", true) || name.endsWith(".xml", true)

fun isBinaryAttachment(attachment: ChatAttachment): Boolean = !isTextLikeAttachment(attachment)

private fun parseProviderError(body: String): String = runCatching {
    val root = JSONObject(body)
    root.optString("message").ifBlank { root.optJSONObject("error")?.optString("message").orEmpty() }
}.getOrDefault("")

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (total <= maxBytes) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes + 1 - total))
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
        if (total > maxBytes) throw IOException("Provider response exceeded the safety limit.")
    }
    return output.toByteArray()
}