package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.build.BuildConfiguration
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

sealed interface GitHubDispatchResult {
    data class Started(val runId: Long, val htmlUrl: String?) : GitHubDispatchResult
    data class Failure(val message: String) : GitHubDispatchResult
}

sealed interface GitHubCancelResult {
    data object Accepted : GitHubCancelResult
    data object Conflict : GitHubCancelResult
    data class Failure(val message: String) : GitHubCancelResult
}

class GitHubActionsGateway(
    private val secretStore: com.mrredhood.devforge.core.security.SecretStore,
    private val connection: HttpConnectionFactory = DefaultHttpConnectionFactory,
) {
    fun validateCredential(): GitHubCredentialValidation {
        val result = requestBody("/user", "GET", 32_000)
        return result.fold(
            onSuccess = { body ->
                val login = runCatching { JSONObject(body).optString("login").takeIf(String::isNotBlank) }.getOrNull()
                if (login == null) GitHubCredentialValidation.Invalid("GitHub returned an authenticated response without an account login.")
                else GitHubCredentialValidation.Valid(login)
            },
            onFailure = { GitHubCredentialValidation.Invalid(safeMessage(it)) },
        )
    }

    fun cancelRun(owner: String, repository: String, runId: Long): GitHubCancelResult {
        val normalizedOwner = owner.trim()
        val normalizedRepository = repository.trim()
        if (!OWNER_OR_REPOSITORY.matches(normalizedOwner) || !OWNER_OR_REPOSITORY.matches(normalizedRepository)) {
            return GitHubCancelResult.Failure("The selected GitHub repository identifier is invalid.")
        }
        if (runId <= 0L) return GitHubCancelResult.Failure("The workflow run identifier is invalid.")
        val token = runCatching { secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) }.getOrNull()
            ?: return GitHubCancelResult.Failure("GitHub credential is unavailable. Unlock protected credentials and try again.")
        val endpoint = "https://api.github.com/repos/" + normalizedOwner + "/" + normalizedRepository + "/actions/runs/" + runId + "/cancel"
        return runCatching {
            val http = connection.open(endpoint).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer " + token)
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            val code = http.responseCode
            val body = runCatching {
                (if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream)
                    .bufferedReader().use { it.readText() }
            }.getOrDefault("")
            http.disconnect()
            when (code) {
                202 -> GitHubCancelResult.Accepted
                409 -> GitHubCancelResult.Conflict
                401, 403 -> GitHubCancelResult.Failure("GitHub rejected the cancellation request (HTTP " + code + "). Verify the credential has Actions write permission.")
                else -> GitHubCancelResult.Failure("GitHub rejected the cancellation request (HTTP " + code + ")" + if (body.isBlank()) "." else ": " + sanitizeError(body))
            }
        }.getOrElse { error ->
            GitHubCancelResult.Failure("Unable to reach GitHub: " + (error.message ?: "network error"))
        }
    }

    fun dispatch(owner: String, repository: String, configuration: BuildConfiguration): GitHubDispatchResult {
        val normalizedOwner = owner.trim()
        val normalizedRepository = repository.trim()
        val workflowId = configuration.workflowFile.trim().substringAfterLast('/')
        val branch = configuration.branch.trim()
        if (!OWNER_OR_REPOSITORY.matches(normalizedOwner) || !OWNER_OR_REPOSITORY.matches(normalizedRepository)) return GitHubDispatchResult.Failure("The selected GitHub repository identifier is invalid.")
        if (!WORKFLOW_NAME.matches(workflowId)) return GitHubDispatchResult.Failure("The selected GitHub workflow file is invalid.")
        if (branch.isBlank()) return GitHubDispatchResult.Failure("A Git reference is required before dispatch.")
        if (workflowId != TARGET_CONTRACT_WORKFLOW) return GitHubDispatchResult.Failure("The selected workflow does not expose DevForge's fixed build-target contract.")
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) ?: return GitHubDispatchResult.Failure("GitHub is not connected on this device.")
        val endpoint = "https://api.github.com/repos/$normalizedOwner/$normalizedRepository/actions/workflows/$workflowId/dispatches"
        val dispatchStartedAt = Instant.now()
        val inputs = JSONObject()
        inputs.put("target", configuration.target.workflowInput)
        val payloadJson = JSONObject()
        payloadJson.put("ref", branch)
        payloadJson.put("inputs", inputs)
        val payload = payloadJson.toString()

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
                runCatching { http.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            } else {
                runCatching { (http.errorStream ?: http.inputStream).use { it.readBounded(MAX_DIRECT_RESPONSE_BYTES).toString(Charsets.UTF_8) } }.getOrDefault("")
            }
            http.disconnect()
            if (code !in 200..299) {
                val suffix = if (body.isBlank()) "." else ": ${sanitizeError(body)}"
                GitHubDispatchResult.Failure("GitHub rejected the workflow dispatch (HTTP $code)$suffix")
            } else {
                val responseJson = body.takeIf(String::isNotBlank)?.let { runCatching { JSONObject(it) }.getOrNull() }
                val responseRunId = responseJson?.optLong("workflow_run_id")?.takeIf { it > 0L }
                val responseHtmlUrl = responseJson?.optString("html_url")?.takeIf(String::isNotBlank)
                val run = if (responseRunId != null) {
                    GitHubRunReference(responseRunId, responseHtmlUrl)
                } else {
                    resolveDispatchedRun(normalizedOwner, normalizedRepository, workflowId, branch, dispatchStartedAt, token)
                }
                if (run == null) GitHubDispatchResult.Failure("GitHub accepted the workflow dispatch, but the new workflow run could not be located yet. Refresh Build Center and try again if no run appears.")
                else GitHubDispatchResult.Started(run.runId, run.htmlUrl)
            }
        }.getOrElse { error -> GitHubDispatchResult.Failure("Unable to reach GitHub: ${error.message ?: "network error"}") }
    }

    private fun resolveDispatchedRun(owner: String, repository: String, workflowId: String, branch: String, dispatchedAt: Instant, token: String): GitHubRunReference? {
        repeat(DISPATCH_RUN_LOOKUP_ATTEMPTS) { attempt ->
            val runs = fetchWorkflowDispatchRuns(owner, repository, workflowId, token)
            val candidate = runs.asSequence()
                .filter { it.branch == branch }
                .filter { reference -> reference.createdAt?.let { runCatching { Instant.parse(it).isAfter(dispatchedAt.minusSeconds(3)) }.getOrDefault(false) } == true }
                .sortedByDescending { it.id }
                .firstOrNull()
            if (candidate != null) return GitHubRunReference(candidate.id, candidate.htmlUrl)
            if (attempt + 1 < DISPATCH_RUN_LOOKUP_ATTEMPTS) Thread.sleep(DISPATCH_RUN_LOOKUP_DELAY_MS)
        }
        return null
    }

    private fun fetchWorkflowDispatchRuns(owner: String, repository: String, workflowId: String, token: String): List<WorkflowRunReference> {
        val endpoint = "https://api.github.com/repos/$owner/$repository/actions/workflows/$workflowId/runs?event=workflow_dispatch&per_page=20"
        val http = connection.open(endpoint).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        val code = http.responseCode
        val stream = if (code in 200..299) http.inputStream else (http.errorStream ?: http.inputStream)
        val body = stream.use { it.readBounded(DISPATCH_RUN_LOOKUP_MAX_BYTES).toString(Charsets.UTF_8) }
        http.disconnect()
        if (code !in 200..299) {
            val suffix = if (body.isBlank()) "." else ": ${sanitizeError(body)}"
            throw IllegalStateException("GitHub run lookup failed (HTTP $code)$suffix")
        }
        return parseWorkflowRuns(body)
    }

    private fun parseWorkflowRuns(body: String): List<WorkflowRunReference> {
        val json = JSONObject(body)
        val source = json.optJSONArray("workflow_runs") ?: JSONArray()
        val result = mutableListOf<WorkflowRunReference>()
        for (index in 0 until source.length()) {
            val run = source.optJSONObject(index) ?: continue
            val id = run.optLong("id")
            val branch = run.optString("head_branch")
            if (id <= 0L || branch.isBlank()) continue
            result += WorkflowRunReference(
                id = id,
                branch = branch,
                createdAt = run.optString("created_at").takeIf(String::isNotBlank),
                htmlUrl = run.optString("html_url").takeIf(String::isNotBlank),
            )
        }
        return result
    }

    fun getRun(owner: String, repository: String, runId: Long): GitHubRunResult {
        val normalizedOwner = validateRepositoryPart(owner) ?: return GitHubRunResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateRepositoryPart(repository) ?: return GitHubRunResult.Failure("The GitHub repository is invalid.")
                id = json.optLong("id"), runNumber = json.optLong("run_number"), name = json.optString("name", "GitHub Actions run"),
                status = json.optString("status", "unknown"), conclusion = json.optString("conclusion").takeIf(String::isNotBlank),
                htmlUrl = json.optString("html_url").takeIf(String::isNotBlank), branch = json.optString("head_branch", "unknown"),
                event = json.optString("event", "unknown"), createdAt = json.optString("created_at").takeIf(String::isNotBlank),
                updatedAt = json.optString("updated_at").takeIf(String::isNotBlank),
            )
        }.fold(onSuccess = { GitHubRunResult.Success(it) }, onFailure = { GitHubRunResult.Failure(safeMessage(it)) })

    fun listArtifacts(owner: String, repository: String, runId: Long): GitHubArtifactsResult =
        getJson("/repos/${normalizedOwner}/${normalizedRepository}/actions/runs/$runId/artifacts?per_page=100") { json ->
            val source = json.optJSONArray("artifacts") ?: JSONArray()
            val result = mutableListOf<GitHubArtifact>()
            for (index in 0 until source.length()) {
                val artifact = source.optJSONObject(index) ?: continue
                result += GitHubArtifact(
                    id = artifact.optLong("id"), name = artifact.optString("name", "Unnamed artifact"), sizeBytes = artifact.optLong("size_in_bytes"),
                    expired = artifact.optBoolean("expired", false), archiveDownloadUrl = artifact.optString("archive_download_url").takeIf(String::isNotBlank),
                    createdAt = artifact.optString("created_at").takeIf(String::isNotBlank), expiresAt = artifact.optString("expires_at").takeIf(String::isNotBlank),
                )
            }
            result
        }.fold(onSuccess = { GitHubArtifactsResult.Success(it) }, onFailure = { GitHubArtifactsResult.Failure(safeMessage(it)) })

    fun fetchLogs(owner: String, repository: String, runId: Long, maxJobs: Int = 4, maxBytes: Int = 220_000): GitHubLogsResult {
        val jobs = getJson("/repos/${owner.trim()}/${repository.trim()}/actions/runs/$runId/jobs?per_page=100") { json ->
            val source = json.optJSONArray("jobs") ?: JSONArray()
            val result = mutableListOf<JobDescriptor>()
            for (index in 0 until source.length()) {
                val job = source.optJSONObject(index) ?: continue
                result += JobDescriptor(
                    id = job.optLong("id"), name = job.optString("name", "job"), status = job.optString("status", "unknown"),
                    conclusion = job.optString("conclusion").takeIf(String::isNotBlank), htmlUrl = job.optString("html_url").takeIf(String::isNotBlank),
                )
            }
            result
        }.getOrElse { return GitHubLogsResult.Failure(safeMessage(it)) }
        val selected = jobs.filter { it.status != "queued" }.sortedByDescending { it.id }.take(maxJobs)
        var usedBytes = 0
        var truncated = false
        val result = mutableListOf<GitHubJobLog>()
        for (job in selected) {
            if (usedBytes >= maxBytes) { truncated = true; break }
            val limit = maxBytes - usedBytes
            val text = getText("/actions/jobs/${job.id}/logs", limit).getOrElse { error -> return GitHubLogsResult.Failure("Unable to read logs for ${job.name}: ${safeMessage(error)}") }
            usedBytes += text.toByteArray(Charsets.UTF_8).size
            if (text.toByteArray(Charsets.UTF_8).size >= limit) truncated = true
            result += GitHubJobLog(jobId = job.id, jobName = job.name, status = job.status, conclusion = job.conclusion, htmlUrl = job.htmlUrl, text = text)
        }
        return GitHubLogsResult.Success(result, truncated)
    }

    private fun <T> getJson(path: String, parser: (JSONObject) -> T): Result<T> {
        val body = requestBody(path, "GET").getOrElse { return Result.failure(it) }
        return runCatching { parser(JSONObject(body)) }
    }

    private fun getText(path: String, maxBytes: Int): Result<String> = requestBody(path, "GET", maxBytes)

    private fun validatedPath(path: String): String {
        require(path.length <= MAX_PATH_LENGTH) { "GitHub API path is too long." }
        require(!path.contains("..")) { "GitHub API path contains invalid traversal markers." }
        return path
    }

    private fun requestBody(path: String, method: String, maxBytes: Int = 320_000): Result<String> {
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) ?: return Result.failure(IllegalStateException("GitHub is not connected on this device."))
        return runCatching {
            val endpoint = "https://api.github.com" + validatedPath(path)
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
                val suffix = if (body.isBlank()) "." else ": ${sanitizeError(body)}"
                throw IllegalStateException("GitHub request failed (HTTP $code)$suffix")
            }
            body
        }
    }

    private fun safeMessage(error: Throwable): String = error.message?.takeIf(String::isNotBlank)?.take(280) ?: "GitHub request failed."
    private fun sanitizeError(body: String): String = body.replace(Regex("(?i)(token|authorization|access[_-]?token)\\s*[:=]\\s*[^,}\\s]+"), "$1=[redacted]").take(280)

    private data class GitHubRunReference(val runId: Long, val htmlUrl: String?)
    private data class WorkflowRunReference(val id: Long, val branch: String, val createdAt: String?, val htmlUrl: String?)
    private data class JobDescriptor(val id: Long, val name: String, val status: String, val conclusion: String?, val htmlUrl: String?)

    private fun validateRepositoryPart(value: String): String? = value.trim().takeIf { it.isNotBlank() && it.length <= 100 && OWNER_OR_REPOSITORY.matches(it) }

        private const val MAX_DIRECT_RESPONSE_BYTES = 320_000
        private const val MAX_PATH_LENGTH = 500
    companion object {
        const val API_VERSION = "2026-03-10"
        const val TARGET_CONTRACT_WORKFLOW = "android.yml"
        private const val DISPATCH_RUN_LOOKUP_ATTEMPTS = 6
        private const val DISPATCH_RUN_LOOKUP_DELAY_MS = 1_500L
        private const val DISPATCH_RUN_LOOKUP_MAX_BYTES = 180_000
        private val OWNER_OR_REPOSITORY = Regex("^[A-Za-z0-9_.-]+$")
        private val WORKFLOW_NAME = Regex("^[A-Za-z0-9_.-]+$")
        fun forBuildStore(secretStore: com.mrredhood.devforge.core.security.SecretStore): GitHubActionsGateway = GitHubActionsGateway(secretStore)
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

fun interface HttpConnectionFactory { fun open(url: String): HttpURLConnection }

private val DefaultHttpConnectionFactory = HttpConnectionFactory { url -> URL(url).openConnection() as HttpURLConnection }
    }

