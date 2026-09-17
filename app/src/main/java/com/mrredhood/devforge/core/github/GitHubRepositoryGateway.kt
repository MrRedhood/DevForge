package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Read-only GitHub REST boundary for account, repository and workflow discovery. */
class GitHubRepositoryGateway(
    private val secretStore: SecretStore,
    private val connection: HttpConnectionFactory = DefaultHttpConnectionFactory,
) {
    fun currentUser(): Result<String> = getJson("/user") {
        it.optString("login").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("GitHub did not return an account login.")
    }

    fun listRepositories(): GitHubRepositoryListResult {
        val repositories = mutableListOf<GitHubRepository>()
        var page = 1
        var truncated = false

        while (page <= MAX_REPOSITORY_PAGES) {
            val result = getJson("/user/repos?per_page=$PAGE_SIZE&page=$page&sort=updated&direction=desc") { json ->
                parseRepositoryArray(json)
            }

            val pageItems = result.getOrElse { return GitHubRepositoryListResult.Failure(safeMessage(it)) }
            repositories += pageItems
            if (pageItems.size < PAGE_SIZE) break
            page++
        }

        if (page > MAX_REPOSITORY_PAGES) truncated = true
        return GitHubRepositoryListResult.Success(repositories, truncated)
    }

    fun getRepository(owner: String, repository: String): GitHubRepositoryResult {
        val path = "/repos/${owner.trim()}/${repository.trim()}"
        val result = getJson(path) { parseRepository(it) }
        return result.fold(
            onSuccess = { GitHubRepositoryResult.Success(it) },
            onFailure = { GitHubRepositoryResult.Failure(safeMessage(it)) },
        )
    }

    fun listWorkflows(owner: String, repository: String): GitHubWorkflowListResult {
        val path = "/repos/${owner.trim()}/${repository.trim()}/actions/workflows?per_page=$PAGE_SIZE&page=1"
        val result = getJson(path) { json ->
            val workflows = json.optJSONArray("workflows") ?: JSONArray()
            buildList(workflows.length()) {
                for (index in 0 until workflows.length()) {
                    val workflow = workflows.optJSONObject(index) ?: continue
                    val path = workflow.optString("path").takeIf(String::isNotBlank) ?: continue
                    add(
                        GitHubWorkflow(
                            id = workflow.optLong("id"),
                            name = workflow.optString("name", path.substringAfterLast('/')),
                            path = path,
                            state = workflow.optString("state", "unknown"),
                        ),
                    )
                }
            }
        }

        return result.fold(
            onSuccess = { GitHubWorkflowListResult.Success(it) },
            onFailure = { GitHubWorkflowListResult.Failure(safeMessage(it)) },
        )
    }

    private fun <T> getJson(path: String, parser: (JSONObject) -> T): Result<T> {
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return Result.failure(IllegalStateException("GitHub is not connected on this device."))

        return runCatching {
            val endpoint = "https://api.github.com$path"
            val http = connection.open(endpoint).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                connectTimeout = 15_000
                readTimeout = 20_000
            }

            val code = http.responseCode
            val body = runCatching {
                (if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream)
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrDefault("")

            if (code !in 200..299) {
                throw IllegalStateException("GitHub request failed (HTTP $code)${if (body.isBlank()) "." else ": ${sanitizeError(body)}"}")
            }

            parser(JSONObject(body))
        }
    }

    private fun parseRepositoryArray(json: JSONObject): List<GitHubRepository> {
        val repositories = json.optJSONArray("repositories") ?: JSONArray()
        return buildList(repositories.length()) {
            for (index in 0 until repositories.length()) {
                repositories.optJSONObject(index)?.let { add(parseRepository(it)) }
            }
        }
    }

    private fun parseRepository(json: JSONObject): GitHubRepository {
        val owner = json.optJSONObject("owner")?.optString("login").orEmpty()
        val name = json.optString("name")
        return GitHubRepository(
            id = json.optLong("id"),
            owner = owner,
            name = name,
            fullName = json.optString("full_name", "$owner/$name"),
            isPrivate = json.optBoolean("private", false),
            defaultBranch = json.optString("default_branch", "main"),
        )
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank)?.take(280) ?: "GitHub request failed."

    private fun sanitizeError(body: String): String = body
        .replace(Regex("(?i)(token|authorization|access[_-]?token)\\s*[:=]\\s*[^,}\\s]+"), "$1=[redacted]")
        .take(280)

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_REPOSITORY_PAGES = 3
        private const val API_VERSION = "2026-03-10"
    }
}

private val DefaultHttpConnectionFactory: HttpConnectionFactory = HttpConnectionFactory { url ->
    URL(url).openConnection() as HttpURLConnection
}
