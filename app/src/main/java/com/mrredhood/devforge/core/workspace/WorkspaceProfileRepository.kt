package com.mrredhood.devforge.core.workspace

import android.content.Context
import org.json.JSONObject

data class WorkspaceProfile(
    val displayName: String,
    val preferredBuildTarget: String,
    val validationWorkflow: String,
    val defaultPreviewPath: String,
    val notes: String,
)

class WorkspaceProfileRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "devforge_workspace_profiles",
        Context.MODE_PRIVATE,
    )

    fun load(workspaceId: String): WorkspaceProfile {
        val json = runCatching {
            JSONObject(prefs.getString(key(workspaceId), "{}").orEmpty())
        }.getOrDefault(JSONObject())
        return WorkspaceProfile(
            displayName = json.optString("displayName", ""),
            preferredBuildTarget = json.optString("preferredBuildTarget", "DebugApk"),
            validationWorkflow = json.optString("validationWorkflow", ".github/workflows/android.yml"),
            defaultPreviewPath = json.optString("defaultPreviewPath", "index.html"),
            notes = json.optString("notes", ""),
        )
    }

    fun save(workspaceId: String, profile: WorkspaceProfile) {
        val json = JSONObject()
            .put("displayName", profile.displayName.take(120))
            .put("preferredBuildTarget", profile.preferredBuildTarget.take(40))
            .put("validationWorkflow", profile.validationWorkflow.take(180))
            .put("defaultPreviewPath", profile.defaultPreviewPath.take(180))
            .put("notes", profile.notes.take(2000))
        prefs.edit().putString(key(workspaceId), json.toString()).apply()
    }

    fun clear(workspaceId: String) {
        prefs.edit().remove(key(workspaceId)).apply()
    }

    private fun key(workspaceId: String) = "profile::$workspaceId"
}
