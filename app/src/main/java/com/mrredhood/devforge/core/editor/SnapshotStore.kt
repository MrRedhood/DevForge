package com.mrredhood.devforge.core.editor

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(snapshot: ContentSnapshot) {
        val key = key(snapshot.uri)
        val existing = list(snapshot.uri).toMutableList()
        existing.removeAll { it.contentHash == snapshot.contentHash }
        existing.add(0, snapshot)
        val trimmed = existing.take(MAX_PER_FILE)
        val array = JSONArray().apply {
            trimmed.forEach { value ->
                put(
                    JSONObject()
                        .put("id", value.id)
                        .put("uri", value.uri.toString())
                        .put("name", value.name)
                        .put("content", value.content)
                        .put("contentHash", value.contentHash)
                        .put("createdAt", value.createdAt)
                        .put("reason", value.reason.name),
                )
            }
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    fun create(
        uri: Uri,
        name: String,
        content: String,
        reason: SnapshotReason,
    ): ContentSnapshot = ContentSnapshot(
        id = UUID.randomUUID().toString(),
        uri = uri,
        name = name,
        content = content,
        contentHash = ContentHasher.sha256(content),
        createdAt = System.currentTimeMillis(),
        reason = reason,
    )

    fun list(uri: Uri): List<ContentSnapshot> = runCatching {
        val json = JSONArray(preferences.getString(key(uri), "[]"))
        buildList {
            for (index in 0 until json.length()) {
                val value = json.getJSONObject(index)
                add(
                    ContentSnapshot(
                        id = value.getString("id"),
                        uri = Uri.parse(value.getString("uri")),
                        name = value.getString("name"),
                        content = value.getString("content"),
                        contentHash = value.getString("contentHash"),
                        createdAt = value.getLong("createdAt"),
                        reason = runCatching { SnapshotReason.valueOf(value.getString("reason")) }
                            .getOrDefault(SnapshotReason.MANUAL),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun latest(uri: Uri): ContentSnapshot? = list(uri).firstOrNull()

    fun clear(uri: Uri) {
        preferences.edit().remove(key(uri)).apply()
    }

    private fun key(uri: Uri) = SNAPSHOT_PREFIX + uri.toString()

    private companion object {
        const val PREFERENCES = "devforge_editor_snapshots"
        const val SNAPSHOT_PREFIX = "snapshot::"
        const val MAX_PER_FILE = 12
    }
}
