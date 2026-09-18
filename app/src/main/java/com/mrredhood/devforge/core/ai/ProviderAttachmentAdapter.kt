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

    suspend fun prepare(
        apiKey: String,
        attachments: List<ChatAttachment>,
    ): List<ProviderPreparedAttachment>
}

sealed interface ProviderPreparedAttachment {
    data class FileUri(
        val name: String,
        val mimeType: String,
        val uri: String,
    ) : ProviderPreparedAttachment
}

class UnsupportedProviderAttachmentsException(provider: AIProvider) :
    IllegalStateException(
        "Binary attachments are not yet supported for " +
            provider.displayName +
            ". Remove the binary attachment or use a provider with binary attachment support.",
    )

class GeminiProviderAttachmentAdapter(
    private val resolver: ContentResolver,
) : ProviderAttachmentAdapter {
    override val provider: AIProvider = AIProvider.GEMINI

    override suspend fun prepare(
        apiKey: String,
        attachments: List<ChatAttachment>,
    ): List<ProviderPreparedAttachment> {
        val binary = attachments.filterNot(::isTextLikeAttachment)
        if (binary.isEmpty()) return emptyList()
        return binary.map { upload(it, apiKey) }
    }

    private suspend fun upload(
        attachment: ChatAttachment,
        apiKey: String,
    ): ProviderPreparedAttachment.FileUri {
        require(attachment.sizeBytes > 0L) { "Attachment size is invalid." }
        require(attachment.sizeBytes <= MAX_UPLOAD_BYTES) {
            "Attachment exceeds the provider upload safety limit."
        }

        val startConnection = URL(
            "https://generativelanguage.googleapis.com/upload/v1beta/files?key=" +
                Uri.encode(apiKey),
        ).openConnection() as HttpURLConnection
        startConnection.requestMethod = "POST"
        startConnection.instanceFollowRedirects = false
        startConnection.doOutput = true
        startConnection.connectTimeout = CONNECT_TIMEOUT_MS
        startConnection.readTimeout = READ_TIMEOUT_MS
        startConnection.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
        startConnection.setRequestProperty("X-Goog-Upload-Command", "start")
        startConnection.setRequestProperty(
            "X-Goog-Upload-Header-Content-Length",
            attachment.sizeBytes.toString(),
        )
        startConnection.setRequestProperty("X-Goog-Upload-Header-Content-Type", attachment.mimeType)
        startConnection.setRequestProperty("Content-Type", "application/json")

        val uploadUrl = try {
            startConnection.outputStream.use { output ->
                output.write(
                    JSONObject()
                        .put(
                            "file",
                            JSONObject().put(
                                "display_name",
                                attachment.name.take(MAX_DISPLAY_NAME_CHARS),
                            ),
                        )
                        .toString()
                        .toByteArray(Charsets.UTF_8),
                )
            }
            currentCoroutineContext().ensureActive()
            val status = startConnection.responseCode
            val detail = (
                if (status in 200..299) startConnection.inputStream
                else startConnection.errorStream
            )?.use { it.readBounded(MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            require(status in 200..299) {
                parseProviderError(detail).ifBlank {
                    "Gemini attachment upload initialization failed (HTTP $status)."
                }
            }
            startConnection.getHeaderField("x-goog-upload-url")
                ?: startConnection.getHeaderField("X-Goog-Upload-URL")
                ?: error("Gemini did not return a resumable upload URL.")
        } finally {
            startConnection.disconnect()
        }

        val uploadConnection = URL(uploadUrl).openConnection() as HttpURLConnection
        uploadConnection.requestMethod = "POST"
        uploadConnection.instanceFollowRedirects = false
        uploadConnection.doOutput = true
        uploadConnection.connectTimeout = CONNECT_TIMEOUT_MS
        uploadConnection.readTimeout = READ_TIMEOUT_MS
        uploadConnection.setRequestProperty("X-Goog-Upload-Offset", "0")
        uploadConnection.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
        uploadConnection.setRequestProperty("Content-Length", attachment.sizeBytes.toString())
        uploadConnection.setRequestProperty("Content-Type", attachment.mimeType)

        return try {
            val input = resolver.openInputStream(attachment.uri)
                ?: throw IOException("Unable to open attachment.")
            input.use { source ->
                uploadConnection.outputStream.use { output ->
                    copyExactlyBounded(source, output, attachment.sizeBytes)
                }
            }
            currentCoroutineContext().ensureActive()

            val status = uploadConnection.responseCode
            val detail = (
                if (status in 200..299) uploadConnection.inputStream
                else uploadConnection.errorStream
            )?.use { it.readBounded(MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8).orEmpty()

            require(status in 200..299) {
                parseProviderError(detail).ifBlank {
                    "Gemini attachment upload failed (HTTP $status)."
                }
            }

            val file = JSONObject(detail).optJSONObject("file")
                ?: error("Gemini returned an invalid attachment response.")
            val remoteUri = file.optString("uri").trim()
            require(remoteUri.startsWith("https://", ignoreCase = true)) {
                "Gemini returned an invalid attachment URI."
            }
            val remoteMime = file.optString("mimeType").trim().ifBlank { attachment.mimeType }

            require(remoteUri.isNotBlank()) { "Gemini returned no attachment URI." }

            ProviderPreparedAttachment.FileUri(
                name = attachment.name,
                mimeType = remoteMime,
                uri = remoteUri,
            )
        } finally {
            uploadConnection.disconnect()
        }
    }

    private suspend fun copyExactlyBounded(
        source: java.io.InputStream,
        target: java.io.OutputStream,
        expectedBytes: Long,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L

        while (total < expectedBytes) {
            currentCoroutineContext().ensureActive()
            val maxRead = minOf(buffer.size.toLong(), expectedBytes - total).toInt()
            val read = source.read(buffer, 0, maxRead)
            require(read >= 0) { "Attachment ended before its declared size." }
            if (read == 0) continue
            total += read
            target.write(buffer, 0, read)
        }

        require(total == expectedBytes) { "Attachment size changed while uploading." }
        require(source.read() == -1) { "Attachment grew while uploading." }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 120_000
        const val MAX_ERROR_BYTES = 16 * 1024
        const val MAX_DISPLAY_NAME_CHARS = 512
        const val MAX_UPLOAD_BYTES = 50L * 1024L * 1024L
        const val BUFFER_BYTES = 64 * 1024
    }
}

class UnsupportedProviderAttachmentAdapter(
    override val provider: AIProvider,
) : ProviderAttachmentAdapter {
    override suspend fun prepare(
        apiKey: String,
        attachments: List<ChatAttachment>,
    ): List<ProviderPreparedAttachment> {
        if (attachments.any(::isBinaryAttachment)) {
            throw UnsupportedProviderAttachmentsException(provider)
        }
        return emptyList()
    }
}

fun isTextLikeAttachment(attachment: ChatAttachment): Boolean =
    isTextLikeAttachment(attachment.name, attachment.mimeType)

fun isTextLikeAttachment(name: String, mimeType: String): Boolean =
    mimeType.startsWith("text/") ||
        mimeType == "application/json" ||
        mimeType == "application/xml" ||
        mimeType == "application/rtf" ||
        mimeType == "text/csv" ||
        name.endsWith(".kt", true) ||
        name.endsWith(".java", true) ||
        name.endsWith(".js", true) ||
        name.endsWith(".ts", true) ||
        name.endsWith(".py", true) ||
        name.endsWith(".md", true)

fun isBinaryAttachment(attachment: ChatAttachment): Boolean = !isTextLikeAttachment(attachment)

private fun parseProviderError(body: String): String =
    runCatching {
        JSONObject(body).optString("message").ifBlank {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }
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
        if (total > maxBytes) {
            throw IOException("Provider error response exceeded the safety limit.")
        }
    }
    return output.toByteArray()
}
