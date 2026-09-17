package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.build.BuildConfiguration
import org.json.JSONArray
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
    private val secretStore: com.mrredhood.devforge.core.security.SecretStore,
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

    fun getRun(owner: String, repository: String, runId: Long): GitHubRunResult =
        getJson("/repos/${owner.trim()}/${repository.trim()}/actions/runs/$runId") { json ->
            GitHubRunSnapshot(
                id = json.optLong("id"),
                runNumber = json.optLong("run_number"),
                name = json.optString("name", "GitHub Actions run"),
                status = json.optString("status", "unknown"),
                conclusion = json.optString("conclusion").takeIf(String::isNotBlank),
                htmlUrl = json.optString("html_url").takeIf(String::isNotBlank),
                branch = json.optString("head_branch", "unknown"),
                event = json.optString("event", "unknown"),
                createdAt = json.optString("created_at").takeIf(String::isNotBlank),
                updatedAt = json.optString("updated_at").takeIf(String::isNotBlank),
            )
        }.fold(
            onSuccess = { GitHubRunResult.Success(it) },
            onFailure = { GitHubRunResult.Failure(safeMessage(it)) },
        )

    fun listArtifacts(owner: String, repository: String, runId: Long): GitHubArtifactsResult =
        getJson("/repos/${owner.trim()}/${repository.trim()}/actions/runs/$runId/artifacts?per_page=100") { json ->
            val source = json.optJSONArray("artifacts") ?: JSONArray()
            buildList(source.length()) {
                for (index in 0 until source.length()) {
                    val artifact = source.optJSONObject(index) ?: continue
                    add(
                        GitHubArtifact(
                            id = artifact.optLong("id"),
                            name = artifact.optString("name", "Unnamed artifact"),
                            sizeBytes = artifact.optLong("size_in_bytes"),
                            expired = artifact.optBoolean("expired", false),
                            archiveDownloadUrl = artifact.optString("archive_download_url").takeIf(String::isNotBlank),
                            createdAt = artifact.optString("created_at").takeIf(String::isNotBlank),
                            expiresAt = artifact.optString("expires_at").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }.fold(
            onSuccess = { GitHubArtifactsResult.Success(it) },
            onFailure = { GitHubArtifactsResult.Failure(safeMessage(it)) },
        )

    fun fetchLogs(
        owner: String,
        repository: String,
        runId: Long,
        maxJobs: Int = 4,
        maxBytes: Int = 220_000,
    ): GitHubLogsResult {
        val jobs = getJson("/repos/${owner.trim()}/${repository.trim()}/actions/runs/$runId/jobs?per_page=100") { json ->
            val source = json.optJSONArray("jobs") ?: JSONArray()
            buildList(source.length()) {
                for (index in 0 until source.length()) {
                    val job = source.optJSONObject(index) ?: continue
                    add(
                        JobDescriptor(
                            id = job.optLong("id"),
                            name = job.optString("name", "job"),
                            status = job.optString("status", "unknown"),
                            conclusion = job.optString("conclusion").takeIf(String::isNotBlank),
                            htmlUrl = job.optString("html_url").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }.getOrElse { return GitHubLogsResult.Failure(safeMessage(it)) }

        val selected = jobs
            .filter { it.status != "queued" }
            .sortedByDescending { it.id }
            .take(maxJobs)

        var usedBytes = 0
        var truncated = false
        val result = mutableListOf<GitHubJobLog>()

        for (job in selected) {
            if (usedBytes >= maxBytes) {
                truncated = true
                break
            }

            val limit = maxBytes - usedBytes
            val textResult = getText("/actions/jobs/${job.id}/logs", limit)
            val text = textResult.getOrElse { error ->
                return GitHubLogsResult.Failure("Unable to read logs for ${job.name}: ${safeMessage(error)}")
            }
            usedBytes += text.toByteArray(Charsets.UTF_8).size
            if (text.toByteArray(Charsets.UTF_8).size >= limit) truncated = true
            result += GitHubJobLog(
                jobId = job.id,
                jobName = job.name,
                status = job.status,
                conclusion = job.conclusion,
                htmlUrl = job.htmlUrl,
                text = text,
            )
        }

        return GitHubLogsResult.Success(result, truncated)
    }

    private fun <T> getJson(path: String, parser: (JSONObject) -> T): Result<T> {
        val body = requestBody(path, "GET").getOrElse { return Result.failure(it) }
        return runCatching { parser(JSONObject(body)) }
    }

    private fun getText(path: String, maxBytes: Int): Result<String> =
        requestBody(path, "GET", maxBytes)

    private fun requestBody(path: String, method: String, maxBytes: Int = 320_000): Result<String> {
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return Result.failure(IllegalStateException("GitHub is not connected on this device."))

        return runCatching {
            val endpoint = "https://api.github.com$path"
            val http = connection.open(endpoint).apply {
                requestMethod = method
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            val code = http.responseCode
            val stream = if (code in 200..299) http.inputStream else (http.errorStream ?: http.inputStream)
            val bytes = stream.use { it.readBounded(maxBytes) }
            val body = bytes.toString(Charsets.UTF_8)
            http.disconnect()

            if (code !in 200..299) {
                throw IllegalStateException(
                    "GitHub request failed (HTTP $code)${if (body.isBlank()) "." else ": ${sanitizeError(body)}"}",
                )
            }
            body
        }
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank)?.take(280) ?: "GitHub request failed."

    private fun sanitizeError(body: String): String = body
        .replace(Regex("(?i)(token|authorization|access[_-]?token)\\s*[:=]\\s*[^,}\\s]+"), "$1=[redacted]")
        .take(280)

    private data class JobDescriptor(
        val id: Long,
        val name: String,
        val status: String,
        val conclusion: String?,
        val htmlUrl: String?,
    )

    companion object {
        const val API_VERSION = "2026-03-10"
        private val OWNER_OR_REPOSITORY = Regex("^[A-Za-z0-9_.-]+$")
        private val WORKFLOW_NAME = Regex("^[A-Za-z0-9_.-]+$")

        fun forBuildStore(secretStore: com.mrredhood.devforge.core.security.SecretStore): GitHubActionsGateway =
            GitHubActionsGateway(secretStore)
    }
}

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 32_768))
    val buffer = ByteArray(8_192)
    var total = 0
    while (total < maxBytes) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes - total))
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}

fun interface HttpConnectionFactory {
    fun open(url: String): HttpURLConnection
}

private val DefaultHttpConnectionFactory = HttpConnectionFactory { url ->
    URL(url).openConnection() as HttpURLConnection
}
