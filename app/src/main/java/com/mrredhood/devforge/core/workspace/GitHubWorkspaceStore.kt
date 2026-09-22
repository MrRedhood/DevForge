package com.mrredhood.devforge.core.workspace

import android.content.Context
import org.json.JSONObject

data class GitHubWorkspaceRemote(
    val workspaceId: String,
    val owner: String,
    val repository: String,
    val branch: String,
)

object GitHubWorkspaceUris {
    fun root(workspaceId: String) =
        android.net.Uri.Builder().scheme("devforge").authority("github").appendPath(workspaceId).build()

    fun path(workspaceId: String, remotePath: String) =
        android.net.Uri.Builder()
            .scheme("devforge")
            .authority("github")
            .appendPath(workspaceId)
            .appendQueryParameter("path", remotePath.trim('/'))
            .build()

    fun remotePath(uri: android.net.Uri): String =
        uri.getQueryParameter("path").orEmpty().trim('/')

    fun isRemote(uri: android.net.Uri): Boolean =
        uri.scheme == "devforge" && uri.authority == "github"
}

class GitHubWorkspaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_github_workspaces", Context.MODE_PRIVATE)

    fun save(remote: GitHubWorkspaceRemote) {
        prefs.edit()
            .putString(
                KEY_PREFIX + remote.workspaceId,
                JSONObject()
                    .put("owner", remote.owner)
                    .put("repository", remote.repository)
                    .put("branch", remote.branch)
                    .toString(),
            )
            .apply()
    }

    fun get(workspaceId: String): GitHubWorkspaceRemote? = runCatching {
        val json = JSONObject(prefs.getString(KEY_PREFIX + workspaceId, null) ?: return null)
        GitHubWorkspaceRemote(
            workspaceId = workspaceId,
            owner = json.getString("owner"),
            repository = json.getString("repository"),
            branch = json.optString("branch", "main").ifBlank { "main" },
        )
    }.getOrNull()

    fun remove(workspaceId: String) {
        prefs.edit().remove(KEY_PREFIX + workspaceId).apply()
    }

    fun renameRepository(owner: String, oldRepository: String, newRepository: String): Set<String> {
        val ids = prefs.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
            runCatching {
                val json = JSONObject(value)
                if (
                    json.optString("owner").equals(owner.trim(), ignoreCase = true) &&
                    json.optString("repository").equals(oldRepository.trim(), ignoreCase = true)
                ) {
                    val id = key.removePrefix(KEY_PREFIX)
                    save(
                        GitHubWorkspaceRemote(
                            workspaceId = id,
                            owner = json.optString("owner"),
                            repository = newRepository.trim(),
                            branch = json.optString("branch", "main").ifBlank { "main" },
                        ),
                    )
                    id
                } else null
            }.getOrNull()
        }.toSet()
        return ids
    }

    fun removeByRepository(owner: String, repository: String): Set<String> {
        val matchingIds = prefs.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
            runCatching {
                val json = JSONObject(value)
                val storedOwner = json.optString("owner")
                val storedRepository = json.optString("repository")
                if (
                    storedOwner.equals(owner.trim(), ignoreCase = true) &&
                    storedRepository.equals(repository.trim(), ignoreCase = true)
                ) {
                    key.removePrefix(KEY_PREFIX)
                } else {
                    null
                }
            }.getOrNull()
        }.toSet()
        if (matchingIds.isNotEmpty()) {
            prefs.edit().apply {
                matchingIds.forEach { remove(KEY_PREFIX + it) }
            }.apply()
        }
        return matchingIds
    }

    companion object {
        private const val KEY_PREFIX = "workspace::"
    }
}
