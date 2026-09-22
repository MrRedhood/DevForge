package com.mrredhood.devforge.core.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TerminalSession(
    val id: String,
    val name: String,
    val workingDirectory: String = "",
    val history: List<String> = emptyList(),
)

class TerminalSessionRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_terminal_sessions", Context.MODE_PRIVATE)

    fun list(workspaceId: String): List<TerminalSession> = runCatching {
        val array = JSONArray(prefs.getString(key(workspaceId), "[]"))
        buildList {
            for (i in 0 until minOf(array.length(), MAX_SESSIONS)) {
                val value = array.optJSONObject(i) ?: continue
                val savedHistory = value.optJSONArray("history") ?: JSONArray()
                val history = buildList {
                    for (index in 0 until minOf(savedHistory.length(), MAX_HISTORY)) {
                        savedHistory.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                add(TerminalSession(
                    value.optString("id"),
                    value.optString("name").take(MAX_NAME),
                    value.optString("workingDirectory").take(MAX_PATH),
                    history,
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun ensureDefault(workspaceId: String): List<TerminalSession> {
        val existing = list(workspaceId)
        if (existing.isNotEmpty()) return existing
        save(workspaceId, listOf(TerminalSession(UUID.randomUUID().toString(), "Terminal 1")))
        return list(workspaceId)
    }

    fun add(workspaceId: String): TerminalSession {
        val sessions = list(workspaceId)
        val session = TerminalSession(UUID.randomUUID().toString(), "Terminal " + (sessions.size + 1))
        save(workspaceId, (sessions + session).take(MAX_SESSIONS))
        return session
    }

    fun updateWorkingDirectory(workspaceId: String, id: String, directory: String): TerminalSession {
        val updated = list(workspaceId).map {
            if (it.id == id) it.copy(workingDirectory = directory.take(MAX_PATH)) else it
        }
        save(workspaceId, updated)
        return updated.firstOrNull { it.id == id }
            ?: throw IllegalStateException("Terminal session no longer exists.")
    }

    fun updateHistory(workspaceId: String, id: String, history: List<String>) {
        val updated = list(workspaceId).map {
            if (it.id == id) it.copy(history = history.takeLast(MAX_HISTORY)) else it
        }
        save(workspaceId, updated)
    }

    fun delete(workspaceId: String, id: String): List<TerminalSession> {
        val updated = list(workspaceId).filterNot { it.id == id }
        save(workspaceId, updated)
        return list(workspaceId)
    }

    fun clear(workspaceId: String) {
        prefs.edit().remove(key(workspaceId)).apply()
    }

    private fun save(workspaceId: String, sessions: List<TerminalSession>) {
        val array = JSONArray()
        sessions.take(MAX_SESSIONS).forEach { session ->
            val history = JSONArray()
            session.history.takeLast(MAX_HISTORY).forEach(history::put)
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("name", session.name)
                    .put("workingDirectory", session.workingDirectory)
                    .put("history", history),
            )
        }
        prefs.edit().putString(key(workspaceId), array.toString()).apply()
    }

    private fun key(workspaceId: String): String = "sessions::$workspaceId"

    companion object {
        const val MAX_HISTORY = 100
        private const val MAX_SESSIONS = 8
        private const val MAX_NAME = 80
        private const val MAX_PATH = 500
    }
}
