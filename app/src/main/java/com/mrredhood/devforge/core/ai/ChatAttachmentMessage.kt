package com.mrredhood.devforge.core.ai

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessageAttachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
)

private const val CHAT_ATTACHMENT_OPEN = "<devforge_attachments>"
private const val CHAT_ATTACHMENT_CLOSE = "</devforge_attachments>"

fun encodeChatMessageAttachments(attachments: List<ChatAttachment>): String {
    if (attachments.isEmpty()) return ""
    val array = JSONArray()
    attachments.forEach { attachment ->
        array.put(
            JSONObject().apply {
                put("uri", attachment.uri.toString())
                put("name", attachment.name.take(500))
                put("mimeType", attachment.mimeType.take(200))
                put("sizeBytes", attachment.sizeBytes.coerceAtLeast(0L))
            },
        )
    }
    return CHAT_ATTACHMENT_OPEN + array + CHAT_ATTACHMENT_CLOSE
}

fun parseChatMessageAttachments(content: String): List<ChatMessageAttachment> {
    val payload = content.substringAfter(CHAT_ATTACHMENT_OPEN, "")
        .substringBefore(CHAT_ATTACHMENT_CLOSE, "")
        .trim()
    if (payload.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(payload)
        buildList {
            for (index in 0 until minOf(array.length(), 16)) {
                val item = array.optJSONObject(index) ?: continue
                val uri = runCatching { Uri.parse(item.optString("uri")) }.getOrNull() ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    ChatMessageAttachment(
                        uri = uri,
                        name = name.take(500),
                        mimeType = item.optString("mimeType").ifBlank { "application/octet-stream" }.take(200),
                        sizeBytes = item.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

fun stripChatMessageAttachmentMetadata(content: String): String =
    content.substringBefore(CHAT_ATTACHMENT_OPEN).trimEnd()
