package com.mrredhood.devforge.core.build

import android.content.Context
import org.json.JSONObject

/**
 * Per-repository controls for remote build outputs and downloadable reports.
 * Settings are local to this device; the actual workflow is updated only when
 * the user explicitly applies the DevForge workflow from the repository picker.
 */
data class BuildOutputSettings(
    val buildArtifact: Boolean = true,
    val publishArtifacts: Boolean = true,
    val lintReport: Boolean = true,
    val unitTestReport: Boolean = true,
    val dependencyReport: Boolean = false,
    val target: BuildTarget = BuildTarget.DebugApk,
)

class GitHubBuildSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "github_build_output_settings",
        Context.MODE_PRIVATE,
    )

    fun get(owner: String, repository: String): BuildOutputSettings {
        val json = runCatching {
            JSONObject(prefs.getString(key(owner, repository), "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        return BuildOutputSettings(
            buildArtifact = json.optBoolean("buildArtifact", true),
            publishArtifacts = json.optBoolean("publishArtifacts", true),
            lintReport = json.optBoolean("lintReport", true),
            unitTestReport = json.optBoolean("unitTestReport", true),
            dependencyReport = json.optBoolean("dependencyReport", false),
            target = runCatching {
                BuildTarget.valueOf(json.optString("target", BuildTarget.DebugApk.name))
            }.getOrDefault(BuildTarget.DebugApk),
        )
    }

    fun remove(owner: String, repository: String) {
        prefs.edit().remove(key(owner, repository)).apply()
    }

    fun set(owner: String, repository: String, settings: BuildOutputSettings) {
        prefs.edit()
            .putString(
                key(owner, repository),
                JSONObject()
                    .put("buildArtifact", settings.buildArtifact)
                    .put("publishArtifacts", settings.publishArtifacts)
                    .put("lintReport", settings.lintReport)
                    .put("unitTestReport", settings.unitTestReport)
                    .put("dependencyReport", settings.dependencyReport)
                    .put("target", settings.target.name)
                    .toString(),
            )
            .apply()
    }

    private fun key(owner: String, repository: String): String =
        owner.trim().lowercase() + "/" + repository.trim().lowercase()
}
