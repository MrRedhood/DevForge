package com.mrredhood.devforge.core.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TerminalSession(
    val id: String,
    val name: String,
    val workingDirectory: String = "",
)

class TerminalSessionRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_terminal_sessions", Context.MODE_PRIVATE)

    fun list(workspaceId: String): List<TerminalSession> = runCatching {
        val array = JSONArray(prefs.getString(key(workspaceId), "[]"))
        buildList {
            for (i in 0 until minOf(array.length(), MAX_SESSIONS)) {
                val value = array.optJSONObject(i) ?: continue
                add(TerminalSession(value.optString("id"), value.optString("name").take(MAX_NAME), value.optString("workingDirectory").take(MAX_PATH)))
            }
        }
    }.getOrDefault(emptyList())

    fun ensureDefault(workspaceId: String): List<TerminalSession> {
        val existing = list(workspaceId)
        if (existing.isNotEmpty()) return existing
        val session = TerminalSession(UUID.randomUUID().toString(), "Terminal 1")
        save(workspaceId, listOf(session))
        return list(workspaceId)
    }

    fun add(workspaceId: String): TerminalSession {
        val sessions = list(workspaceId)
        val session = TerminalSession(UUID.randomUUID().toString(), "Terminal " + (sessions.size + 1))
        save(workspaceId, (sessions + session).take(MAX_SESSIONS))
        return session
    }

    fun delete(workspaceId: String, id: String): List<TerminalSession> {
        val updated = list(workspaceId).filterNot { it.id == id }
        save(workspaceId, updated)
        return list(workspaceId)
    }

    private fun save(workspaceId: String, sessions: List<TerminalSession>) {
        val array = JSONArray()
        sessions.take(MAX_SESSIONS).forEach { session ->
            array.put(JSONObject().put("id", session.id).put("name", session.name).put("workingDirectory", session.workingDirectory))
        }
        prefs.edit().putString(key(workspaceId), array.toString()).apply()
    }

    private fun key(workspaceId: String) = "sessions::$workspaceId"

    private companion object {
        const val MAX_SESSIONS = 8
        const val MAX_NAME = 80
        const val MAX_PATH = 500
    }
}
