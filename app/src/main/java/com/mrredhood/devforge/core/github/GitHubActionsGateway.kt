package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.build.BuildConfiguration
import com.mrredhood.devforge.core.security.SecretStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface GitHubDispatchResult {
    data class Started(
        val runId: Long?,
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
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return GitHubDispatchResult.Failure("GitHub is not connected on this device.")

        val endpoint = "https://api.github.com/repos/$owner/$repository/actions/workflows/${configuration.workflowFile}/dispatches"
        val payload = JSONObject().apply {
            put("ref", configuration.branch)
        }.toString()

        return runCatching {
            val http = connection.open(endpoint).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            http.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val code = http.responseCode
            if (code in 200..299) {
                val body = http.inputStream.bufferedReader().use { it.readText() }
                val json = body.takeIf(String::isNotBlank)?.let(::JSONObject)
                GitHubDispatchResult.Started(
                    runId = json?.optLong("workflow_run_id")?.takeIf { it > 0 },
                    htmlUrl = json?.optString("html_url")?.takeIf(String::isNotBlank),
                )
            } else {
                val errorBody = runCatching {
                    (http.errorStream ?: http.inputStream).bufferedReader().use { it.readText() }
                }.getOrDefault("")
                GitHubDispatchResult.Failure("GitHub rejected the workflow dispatch (HTTP $code)${if (errorBody.isBlank()) "." else ": ${sanitizeError(errorBody)}"}")
            }
        }.getOrElse { error ->
            GitHubDispatchResult.Failure("Unable to reach GitHub: ${error.message ?: "network error"}")
        }
    }

    private fun sanitizeError(body: String): String = body
        .replace(Regex("(?i)(token|authorization|access[_-]?token)\\s*[:=]\\s*[^,}\\s]+"), "$1=[redacted]")
        .take(280)

    companion object {
        fun forBuildStore(secretStore: SecretStore): GitHubActionsGateway = GitHubActionsGateway(secretStore)
    }
}

fun interface HttpConnectionFactory {
    fun open(url: String): HttpURLConnection
}

private val DefaultHttpConnectionFactory = HttpConnectionFactory { url ->
    URL(url).openConnection() as HttpURLConnection
}
