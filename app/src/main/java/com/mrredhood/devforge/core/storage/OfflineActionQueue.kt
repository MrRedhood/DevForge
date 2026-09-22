package com.mrredhood.devforge.core.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OfflineQueueItem(
    val id: String,
    val type: String,
    val title: String,
    val payload: String,
    val attempts: Int,
    val createdAt: Long,
)

class OfflineActionQueue(context: Context) {
    private val preferences = context.getSharedPreferences("devforge_offline_queue", Context.MODE_PRIVATE)

    fun list(): List<OfflineQueueItem> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        OfflineQueueItem(
                            id = item.optString("id"),
                            type = item.optString("type"),
                            title = item.optString("title"),
                            payload = item.optString("payload"),
                            attempts = item.optInt("attempts", 0).coerceAtLeast(0),
                            createdAt = item.optLong("createdAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun enqueue(type: String, title: String, payload: String): OfflineQueueItem {
        val item = OfflineQueueItem(
            id = UUID.randomUUID().toString(),
            type = type.take(80),
            title = title.take(240),
            payload = payload.take(MAX_PAYLOAD),
            attempts = 0,
            createdAt = System.currentTimeMillis(),
        )
        val next = list().plus(item).takeLast(MAX_ITEMS)
        save(next)
        return item
    }

    @Synchronized
    fun remove(id: String) {
        save(list().filterNot { it.id == id })
    }

    @Synchronized
    fun markAttempt(id: String) {
        save(list().map { if (it.id == id) it.copy(attempts = it.attempts + 1) else it })
    }

    private fun save(items: List<OfflineQueueItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("type", it.type)
                    .put("title", it.title)
                    .put("payload", it.payload)
                    .put("attempts", it.attempts)
                    .put("createdAt", it.createdAt),
            )
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 50
        private const val MAX_PAYLOAD = 64 * 1024
    }
}
