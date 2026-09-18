package com.mrredhood.devforge.core.workspace

import android.content.Context
import com.mrredhood.devforge.core.security.SecretRedactor
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WorkspaceKnowledgeNote(val id: String, val title: String, val content: String, val source: String?, val updatedAtEpochMs: Long)

class WorkspaceKnowledgeRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_workspace_knowledge", Context.MODE_PRIVATE)

    fun list(workspaceId: String, limit: Int = MAX_NOTES): List<WorkspaceKnowledgeNote> = runCatching {
        val a = JSONArray(prefs.getString("knowledge::$workspaceId", "[]"))
        buildList {
            for (i in 0 until minOf(a.length(), MAX_NOTES)) {
                val v = a.optJSONObject(i) ?: continue
                add(WorkspaceKnowledgeNote(v.optString("id"), v.optString("title").take(MAX_TITLE), v.optString("content").take(MAX_CONTENT), v.optString("source").take(MAX_SOURCE).ifBlank { null }, v.optLong("updatedAtEpochMs", 0L)))
            }
        }.take(limit.coerceIn(1, MAX_NOTES))
    }.getOrDefault(emptyList())

    fun remember(workspaceId: String, title: String, content: String, source: String? = null): Result<Unit> = runCatching {
        require(workspaceId.isNotBlank())
        val cleanTitle = title.trim().take(MAX_TITLE)
        val cleanContent = content.trim().take(MAX_CONTENT)
        require(cleanTitle.isNotBlank()) { "Knowledge title is required." }
        require(cleanContent.isNotBlank()) { "Knowledge content is required." }
        require(!SecretRedactor.containsLikelySecret(cleanContent)) { "Likely secret material is not allowed in workspace knowledge." }
        val notes = list(workspaceId, MAX_NOTES).toMutableList()
        notes.removeAll { it.title.equals(cleanTitle, true) }
        notes.add(0, WorkspaceKnowledgeNote(UUID.randomUUID().toString(), cleanTitle, cleanContent, source?.take(MAX_SOURCE), System.currentTimeMillis()))
        val a = JSONArray()
        notes.take(MAX_NOTES).forEach { n -> a.put(JSONObject().put("id", n.id).put("title", n.title).put("content", n.content).put("source", n.source).put("updatedAtEpochMs", n.updatedAtEpochMs)) }
        require(a.toString().toByteArray(Charsets.UTF_8).size <= MAX_TOTAL_BYTES) { "Workspace knowledge storage limit reached." }
        prefs.edit().putString("knowledge::$workspaceId", a.toString()).apply()
    }

    fun remove(workspaceId: String, id: String) {
        val a = JSONArray()
        list(workspaceId, MAX_NOTES).filterNot { it.id == id }.forEach { n -> a.put(JSONObject().put("id", n.id).put("title", n.title).put("content", n.content).put("source", n.source).put("updatedAtEpochMs", n.updatedAtEpochMs)) }
        prefs.edit().putString("knowledge::$workspaceId", a.toString()).apply()
    }

    fun clear(workspaceId: String) = prefs.edit().remove("knowledge::$workspaceId").apply()

    private companion object {
        const val MAX_NOTES = 64
        const val MAX_TITLE = 160
        const val MAX_CONTENT = 4_000
        const val MAX_SOURCE = 300
        const val MAX_TOTAL_BYTES = 256 * 1024
    }
}
