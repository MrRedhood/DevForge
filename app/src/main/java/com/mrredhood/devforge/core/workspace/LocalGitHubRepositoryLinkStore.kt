package com.mrredhood.devforge.core.workspace

import android.content.Context
import org.json.JSONObject

data class LocalGitHubRepositoryLink(
    val workspaceId: String,
    val owner: String,
    val repository: String,
    val branch: String,
)

class LocalGitHubRepositoryLinkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_local_github_links", Context.MODE_PRIVATE)

    fun save(link: LocalGitHubRepositoryLink) {
        prefs.edit().putString(
            KEY_PREFIX + link.workspaceId,
            JSONObject()
                .put("owner", link.owner)
                .put("repository", link.repository)
                .put("branch", link.branch)
                .toString(),
        ).apply()
    }

    fun get(workspaceId: String): LocalGitHubRepositoryLink? = runCatching {
        val json = JSONObject(prefs.getString(KEY_PREFIX + workspaceId, null) ?: return null)
        LocalGitHubRepositoryLink(
            workspaceId = workspaceId,
            owner = json.getString("owner"),
            repository = json.getString("repository"),
            branch = json.optString("branch", "main").ifBlank { "main" },
        )
    }.getOrNull()

    fun remove(workspaceId: String) {
        prefs.edit().remove(KEY_PREFIX + workspaceId).apply()
    }

    fun removeByRepository(owner: String, repository: String): Set<String> {
        val ids = prefs.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
            runCatching {
                val json = JSONObject(value)
                if (
                    json.optString("owner").equals(owner.trim(), ignoreCase = true) &&
                    json.optString("repository").equals(repository.trim(), ignoreCase = true)
                ) key.removePrefix(KEY_PREFIX) else null
            }.getOrNull()
        }.toSet()
        if (ids.isNotEmpty()) prefs.edit().apply { ids.forEach { remove(KEY_PREFIX + it) } }.apply()
        return ids
    }

    fun renameRepository(owner: String, oldRepository: String, newRepository: String) {
        prefs.all.entries.forEach { (key, value) ->
            if (!key.startsWith(KEY_PREFIX) || value !is String) return@forEach
            runCatching {
                val json = JSONObject(value)
                if (
                    json.optString("owner").equals(owner.trim(), ignoreCase = true) &&
                    json.optString("repository").equals(oldRepository.trim(), ignoreCase = true)
                ) {
                    prefs.edit().putString(key, json.put("repository", newRepository.trim()).toString()).apply()
                }
            }
        }
    }

    companion object {
        private const val KEY_PREFIX = "workspace::"
    }
}
