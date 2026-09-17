package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.build.BuildConfiguration
import com.mrredhood.devforge.core.security.SecretStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface GitHubDispatchResult {
    data class Started(
        val runId: Long,
        val htmlUrl: String?,
    ) : GitHubDispatchResult

    data class Failure(val message: String) : GitHubDispatchResult
}

/**
 * Small REST boundary for GitHub Actions. Network calls stay outside UI and ViewModel code.
 * The credential is read only at request time and is never returned by this API.
 */
class GitHubActionsGateway(
    private val secretStore: SecretStore,
    private val connection: HttpConnectionFactory = DefaultHttpConnectionFactory,
) {
    fun dispatch(
        owner: String,
        repository: String,
        configuration: BuildConfiguration,
    ): GitHubDispatchResult {
        val normalizedOwner = owner.trim()
        val normalizedRepository = repository.trim()
        val workflowId = configuration.workflowFile.trim().substringAfterLast('/')
        val branch = configuration.branch.trim()

        if (!OWNER_OR_REPOSITORY.matches(normalizedOwner) || !OWNER_OR_REPOSITORY.matches(normalizedRepository)) {
            return GitHubDispatchResult.Failure("The selected GitHub repository identifier is invalid.")
        }
        if (!WORKFLOW_NAME.matches(workflowId)) {
            return GitHubDispatchResult.Failure("The selected GitHub workflow file is invalid.")
        }
        if (branch.isBlank()) {
            return GitHubDispatchResult.Failure("A Git reference is required before dispatch.")
        }

        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return GitHubDispatchResult.Failure("GitHub is not connected on this device.")

        val endpoint = "https://api.github.com/repos/$normalizedOwner/$normalizedRepository/actions/workflows/$workflowId/dispatches"
        val payload = JSONObject().apply {
            put("ref", branch)
        }.toString()

        return runCatching {
            val http = connection.open(endpoint).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
            }

            http.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = http.responseCode
            val body = if (code in 200..299) {
                http.inputStream.bufferedReader().use { it.readText() }
            } else {
                runCatching {
                    (http.errorStream ?: http.inputStream).bufferedReader().use { it.readText() }
                }.getOrDefault("")
            }
            http.disconnect()

            if (code in 200..299) {
                val json = body.takeIf(String::isNotBlank)?.let(::JSONObject)
                val runId = json?.optLong("workflow_run_id")?.takeIf { it > 0L }
                if (runId == null) {
                    return@runCatching GitHubDispatchResult.Failure(
                        "GitHub accepted the workflow dispatch but did not return a workflow run ID.",
                    )
                }
                GitHubDispatchResult.Started(
                    runId = runId,
                    htmlUrl = json.optString("html_url").takeIf(String::isNotBlank),
                )
            } else {
                GitHubDispatchResult.Failure(
                    "GitHub rejected the workflow dispatch (HTTP $code)${if (body.isBlank()) "." else ": ${sanitizeError(body)}"}",
                )
            }
        }.getOrElse { error ->
            GitHubDispatchResult.Failure("Unable to reach GitHub: ${error.message ?: "network error"}")
        }
    }

    private fun sanitizeError(body: String): String = body
        .replace(Regex("(?i)(token|authorization|access[_-]?token)\\s*[:=]\\s*[^,}\\s]+"), "$1=[redacted]")
        .take(280)

    companion object {
        const val API_VERSION = "2026-03-10"
        private val OWNER_OR_REPOSITORY = Regex("^[A-Za-z0-9_.-]+$")
        private val WORKFLOW_NAME = Regex("^[A-Za-z0-9_.-]+$")

        fun forBuildStore(secretStore: SecretStore): GitHubActionsGateway = GitHubActionsGateway(secretStore)
    }
}

fun interface HttpConnectionFactory {
    fun open(url: String): HttpURLConnection
}

private val DefaultHttpConnectionFactory = HttpConnectionFactory { url ->
    URL(url).openConnection() as HttpURLConnection
}
