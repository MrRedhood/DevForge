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
    fun validateCredential(): GitHubCredentialValidation =
        currentUser().fold(
            onSuccess = { GitHubCredentialValidation.Valid(it) },
            onFailure = { GitHubCredentialValidation.Invalid(safeMessage(it)) },
        )

    fun validateCredential(token: String): GitHubCredentialValidation {
        val normalized = normalizeToken(token)
        if (normalized.isBlank()) {
            return GitHubCredentialValidation.Invalid("GitHub access token is empty.")
        }
        return currentUser(normalized).fold(
            onSuccess = { GitHubCredentialValidation.Valid(it) },
            onFailure = { GitHubCredentialValidation.Invalid(safeMessage(it)) },
        )
    }

    fun currentUser(token: String? = null): Result<String> =
        getJson("/user", token) {
            it.optString("login").takeIf(String::isNotBlank)
                ?: throw IllegalStateException("GitHub did not return an account login.")
        }

    fun listRepositories(): GitHubRepositoryListResult {
        val repositories = mutableListOf<GitHubRepository>()
        var page = 1
        var truncated = false

        while (page <= MAX_REPOSITORY_PAGES) {
            val result = getJsonArray(
                "/user/repos?per_page=" + PAGE_SIZE + "&page=" + page + "&sort=updated&direction=desc",
            )
            val pageItems = result.fold(
                onSuccess = { parseRepositoryArray(it) },
                onFailure = { return GitHubRepositoryListResult.Failure(safeMessage(it)) },
            )
            repositories += pageItems
            if (pageItems.size < PAGE_SIZE) break
            page++
        }

        if (page > MAX_REPOSITORY_PAGES) truncated = true
        return GitHubRepositoryListResult.Success(repositories, truncated)
    }

    fun getRepository(owner: String, repository: String): GitHubRepositoryResult {
        val normalizedOwner = validateName(owner)
            ?: return GitHubRepositoryResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateName(repository)
            ?: return GitHubRepositoryResult.Failure("The GitHub repository is invalid.")
        val path = "/repos/" + normalizedOwner + "/" + normalizedRepository
        return getJson(path) { parseRepository(it) }.fold(
            onSuccess = { GitHubRepositoryResult.Success(it) },
            onFailure = { GitHubRepositoryResult.Failure(safeMessage(it)) },
        )
    }

    fun listCommits(
        owner: String,
        repository: String,
        branch: String = "main",
        perPage: Int = 30,
    ): GitHubCommitHistoryResult {
        val normalizedOwner = validateName(owner)
            ?: return GitHubCommitHistoryResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateName(repository)
            ?: return GitHubCommitHistoryResult.Failure("The GitHub repository is invalid.")
        val normalizedBranch = branch.trim().ifBlank { "main" }
        if (normalizedBranch.length > 255 || '\n' in normalizedBranch || '\r' in normalizedBranch) {
            return GitHubCommitHistoryResult.Failure("The Git branch is invalid.")
        }
        val path = "/repos/" + normalizedOwner + "/" + normalizedRepository +
            "/commits?sha=" + java.net.URLEncoder.encode(normalizedBranch, "UTF-8") +
            "&per_page=" + perPage.coerceIn(1, 50)
        return getJsonArray(path).map { json ->
            buildList(minOf(json.length(), 50)) {
                for (index in 0 until minOf(json.length(), 50)) {
                    val item = json.optJSONObject(index) ?: continue
                    val commit = item.optJSONObject("commit") ?: continue
                    val author = commit.optJSONObject("author")
                    add(
                        GitHubCommitHistoryEntry(
                            sha = item.optString("sha").take(64),
                            subject = commit.optString("message").lineSequence().firstOrNull().orEmpty().trim().take(300),
                            author = author?.optString("name").orEmpty().ifBlank {
                                item.optJSONObject("author")?.optString("login").orEmpty()
                            }.take(160),
                            authoredAt = author?.optString("date").takeIf { !it.isNullOrBlank() }?.take(80),
                            url = item.optString("html_url").takeIf(String::isNotBlank)?.take(2000),
                        ),
                    )
                }
            }
        }.fold(
            onSuccess = { GitHubCommitHistoryResult.Success(it) },
            onFailure = { GitHubCommitHistoryResult.Failure(safeMessage(it)) },
        )
    }

    fun listWorkflows(owner: String, repository: String): GitHubWorkflowListResult {
        val normalizedOwner = validateName(owner)
            ?: return GitHubWorkflowListResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateName(repository)
            ?: return GitHubWorkflowListResult.Failure("The GitHub repository is invalid.")
        val path = "/repos/" + normalizedOwner + "/" + normalizedRepository +
            "/actions/workflows?per_page=" + PAGE_SIZE + "&page=1"
        return getJson(path) { json ->
            val workflows = json.optJSONArray("workflows") ?: JSONArray()
            buildList(workflows.length()) {
                for (index in 0 until workflows.length()) {
                    val workflow = workflows.optJSONObject(index) ?: continue
                    val workflowPath = workflow.optString("path")
                        .takeIf(String::isNotBlank) ?: continue
                    add(
                        GitHubWorkflow(
                            id = workflow.optLong("id"),
                            name = workflow.optString("name", workflowPath.substringAfterLast('/')),
                            path = workflowPath,
                            state = workflow.optString("state", "unknown"),
                        ),
                    )
                }
            }
        }.fold(
            onSuccess = { GitHubWorkflowListResult.Success(it) },
            onFailure = { GitHubWorkflowListResult.Failure(safeMessage(it)) },
        )
    }

    private fun <T> getJson(
        path: String,
        tokenOverride: String? = null,
        parser: (JSONObject) -> T,
    ): Result<T> =
        readBody(path, tokenOverride).map { parser(JSONObject(it)) }

    private fun getJsonArray(path: String): Result<JSONArray> =
        readBody(path).map(::JSONArray)

    private fun readBody(path: String, tokenOverride: String? = null): Result<String> {
        val token = tokenOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return Result.failure(
                IllegalStateException("GitHub is not connected on this device."),
            )

        return runCatching {
            val endpoint = "https://api.github.com" + path
            val http = connection.open(endpoint).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer " + token)
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            try {
                val code = http.responseCode
                val body = (if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream)
                    .use { it.readBounded(MAX_RESPONSE_BYTES) }
                    .toString(Charsets.UTF_8)

                if (code !in 200..299) {
                    throw IllegalStateException(
                        "GitHub request failed (HTTP " + code + ")" +
                            if (body.isBlank()) "." else ": " + sanitizeError(body),
                    )
                }
                body
            } finally {
                http.disconnect()
            }
        }
    }

    private fun parseRepositoryArray(json: JSONArray): List<GitHubRepository> =
        buildList(json.length()) {
            for (index in 0 until json.length()) {
                json.optJSONObject(index)?.let { add(parseRepository(it)) }
            }
        }

    private fun parseRepository(json: JSONObject): GitHubRepository {
        val owner = json.optJSONObject("owner")?.optString("login").orEmpty()
        val name = json.optString("name")
        return GitHubRepository(
            id = json.optLong("id"),
            owner = owner,
            name = name,
            fullName = json.optString("full_name", owner + "/" + name),
            isPrivate = json.optBoolean("private", false),
            defaultBranch = json.optString("default_branch", "main"),
        )
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank)?.take(280)
            ?: "GitHub request failed."

    private fun sanitizeError(body: String): String =
        body.replace(
            Regex("(?i)(token|authorization|access[_-]?token)\\s*[:=]\\s*[^,}\\s]+"),
            "$1=[redacted]",
        ).take(280)

    private fun validateName(value: String): String? {
        val normalized = value.trim()
        return normalized.takeIf {
            it.isNotBlank() && it.length <= 100 && SAFE_NAME.matches(it)
        }
    }

    private fun normalizeToken(value: String): String =
        value.trim().replaceFirst(Regex("(?i)^Bearer\\s+"), "").trim().removeSurrounding("\"").trim()

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_REPOSITORY_PAGES = 3
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val API_VERSION = "2026-03-10"
        private val SAFE_NAME = Regex("^[A-Za-z0-9_.-]+$")
    }
}

data class GitHubCommitHistoryEntry(
    val sha: String,
    val subject: String,
    val author: String,
    val authoredAt: String?,
    val url: String?,
)

sealed interface GitHubCommitHistoryResult {
    data class Success(val commits: List<GitHubCommitHistoryEntry>) : GitHubCommitHistoryResult
    data class Failure(val message: String) : GitHubCommitHistoryResult
}

private val DefaultHttpConnectionFactory: HttpConnectionFactory = HttpConnectionFactory { url ->
    URL(url).openConnection() as HttpURLConnection
}

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (total < maxBytes) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes - total))
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
    }
    if (total >= maxBytes) {
        error("GitHub response exceeded the DevForge response limit.")
    }
    return output.toByteArray()
}
