package com.mrredhood.devforge.core.workspace

import android.content.Context
import android.net.Uri
import org.json.JSONObject

class WorkspaceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun current(): Workspace? {
        val raw = preferences.getString(KEY_CURRENT, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Workspace(
                id = json.getString("id"),
                name = json.getString("name"),
                treeUri = Uri.parse(json.getString("treeUri")),
                lastOpenedAt = json.optLong("lastOpenedAt", 0L),
            )
        }.getOrNull()
    }

    fun save(workspace: Workspace) {
        val json = JSONObject()
            .put("id", workspace.id)
            .put("name", workspace.name)
            .put("treeUri", workspace.treeUri.toString())
            .put("lastOpenedAt", workspace.lastOpenedAt)
        preferences.edit().putString(KEY_CURRENT, json.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_CURRENT).apply()
    }

    private companion object {
        const val PREFERENCES = "devforge_workspace"
        const val KEY_CURRENT = "current_workspace"
    }
}
