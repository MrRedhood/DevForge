package com.mrredhood.devforge.core.editor

import android.content.ContentResolver
import android.net.Uri
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class EditorRepository(
    private val resolver: ContentResolver,
    private val preferences: android.content.SharedPreferences,
) {
    fun read(uri: Uri): Result<String> = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            input.readBytesLimited(MAX_EDITOR_BYTES).toString(StandardCharsets.UTF_8)
        } ?: error("Unable to open file")
    }

    fun write(uri: Uri, content: String): Result<Unit> = runCatching {
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        } ?: error("Unable to write file")
    }

    fun saveRecoveryDraft(draft: RecoveryDraft) {
        val json = JSONObject()
            .put("uri", draft.uri.toString())
            .put("name", draft.name)
            .put("content", draft.content)
            .put("savedAt", draft.savedAt)
        preferences.edit().putString(RECOVERY_PREFIX + draft.uri, json.toString()).apply()
    }

    fun recoveryDraft(uri: Uri): RecoveryDraft? = runCatching {
        val json = JSONObject(preferences.getString(RECOVERY_PREFIX + uri, null) ?: return null)
        RecoveryDraft(
            uri = Uri.parse(json.getString("uri")),
            name = json.getString("name"),
            content = json.getString("content"),
            savedAt = json.optLong("savedAt", 0L),
        )
    }.getOrNull()

    fun clearRecoveryDraft(uri: Uri) {
        preferences.edit().remove(RECOVERY_PREFIX + uri).apply()
    }

    private companion object {
        const val RECOVERY_PREFIX = "recovery::"
        const val MAX_EDITOR_BYTES = 8 * 1024 * 1024
    }
}

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > maxBytes) error("File is larger than the supported editor limit of ${maxBytes / (1024 * 1024)} MB")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
