package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.build.BuildConfiguration
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.delay

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
                val login = runCatching {
                    JSONObject(body).optString("login").takeIf(String::isNotBlank)
                }.getOrNull()
                if (login == null) {
                    GitHubCredentialValidation.Invalid(
                        "GitHub returned an authenticated response without an account login.",
                    )
                } else {
                    GitHubCredentialValidation.Valid(login)
                }
            },
            onFailure = { GitHubCredentialValidation.Invalid(safeMessage(it)) },
        )
    }

    fun cancelRun(owner: String, repository: String, runId: Long): GitHubCancelResult {
        val normalizedOwner = validateRepositoryPart(owner)
            ?: return GitHubCancelResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateRepositoryPart(repository)
            ?: return GitHubCancelResult.Failure("The GitHub repository is invalid.")
        if (runId <= 0L) {
            return GitHubCancelResult.Failure("The workflow run identifier is invalid.")
        }
        val token = runCatching { secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) }.getOrNull()
            ?: return GitHubCancelResult.Failure(
                "GitHub credential is unavailable. Unlock protected credentials and try again.",
            )
        val endpoint =
            "https://api.github.com/repos/" + normalizedOwner + "/" + normalizedRepository +
                "/actions/runs/" + runId + "/cancel"

        return runCatching {
            val http = connection.open(endpoint).apply {
                requestMethod = "POST"
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
                    .use { it.readBounded(MAX_DIRECT_RESPONSE_BYTES).toString(Charsets.UTF_8) }
                when (code) {
                    202 -> GitHubCancelResult.Accepted
                    409 -> GitHubCancelResult.Conflict
                    401, 403 -> GitHubCancelResult.Failure(
                        "GitHub rejected the cancellation request (HTTP " + code +
                            "). Verify the credential has Actions write permission.",
                    )
                    else -> GitHubCancelResult.Failure(
                        "GitHub rejected the cancellation request (HTTP " + code + ")" +
                            if (body.isBlank()) "." else ": " + sanitizeError(body),
                    )
                }
            } finally {
                http.disconnect()
            }
        }.getOrElse { error ->
            GitHubCancelResult.Failure(
                "Unable to reach GitHub: " + (error.message ?: "network error"),
            )
        }
    }

    suspend fun dispatch(owner: String, repository: String, configuration: BuildConfiguration): GitHubDispatchResult {
        val normalizedOwner = validateRepositoryPart(owner)
            ?: return GitHubDispatchResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateRepositoryPart(repository)
            ?: return GitHubDispatchResult.Failure("The GitHub repository is invalid.")
        val workflowId = configuration.workflowFile.trim().substringAfterLast('/')
        val branch = configuration.branch.trim()
        if (!WORKFLOW_NAME.matches(workflowId)) {
            return GitHubDispatchResult.Failure("The selected GitHub workflow file is invalid.")
        }
        if (branch.isBlank() || branch.length > MAX_BRANCH_LENGTH || '\n' in branch || '\r' in branch) {
            return GitHubDispatchResult.Failure("A valid Git reference is required before dispatch.")
        }
        if (workflowId != TARGET_CONTRACT_WORKFLOW) {
            return GitHubDispatchResult.Failure(
                "The selected workflow does not expose DevForge's fixed build-target contract.",
            )
        }
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return GitHubDispatchResult.Failure("GitHub is not connected on this device.")
        val endpoint =
            "https://api.github.com/repos/" + normalizedOwner + "/" + normalizedRepository +
                "/actions/workflows/" + workflowId + "/dispatches"
        val dispatchStartedAt = Instant.now()
        val payload = JSONObject()
            .put("ref", branch)
            .put(
                "inputs",
                JSONObject()
                    .put("target", configuration.target.workflowInput)
                    .put("build_artifact", configuration.buildArtifact.toString())
                    .put("publish_artifacts", configuration.publishArtifacts.toString())
                    .put("lint_report", configuration.lintReport.toString())
                    .put("unit_test_report", configuration.unitTestReport.toString())
                    .put("dependency_report", configuration.dependencyReport.toString()),
            )
            .toString()

        return runCatching {
            val http = connection.open(endpoint).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer " + token)
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
            }
            try {
                http.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = http.responseCode
                val stream = if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream
                val body = stream?.use {
                    it.readBounded(MAX_DIRECT_RESPONSE_BYTES).toString(Charsets.UTF_8)
                }.orEmpty()
                if (code !in 200..299) {
                    return@runCatching GitHubDispatchResult.Failure(
                        "GitHub rejected the workflow dispatch (HTTP " + code + ")" +
                            if (body.isBlank()) "." else ": " + sanitizeError(body),
                    )
                }
                val responseJson = body.takeIf(String::isNotBlank)
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                val responseRunId = responseJson?.optLong("workflow_run_id")?.takeIf { it > 0L }
                val responseHtmlUrl = responseJson?.optString("html_url")?.takeIf(String::isNotBlank)
                val run = if (responseRunId != null) {
                    GitHubRunReference(responseRunId, responseHtmlUrl)
                } else {
                    resolveDispatchedRun(
                        normalizedOwner,
                        normalizedRepository,
                        workflowId,
                        branch,
                        dispatchStartedAt,
                        token,
                    )
                }
                if (run == null) {
                    GitHubDispatchResult.Failure(
                        "GitHub accepted the workflow dispatch, but the new workflow run could not be located yet. Refresh Build Center and try again if no run appears.",
                    )
                } else {
                    GitHubDispatchResult.Started(run.runId, run.htmlUrl)
                }
            } finally {
                http.disconnect()
            }
        }.getOrElse { error ->
            GitHubDispatchResult.Failure(
                "Unable to reach GitHub: " + (error.message ?: "network error"),
            )
        }
    }

    private suspend fun resolveDispatchedRun(
        owner: String,
        repository: String,
        workflowId: String,
        branch: String,
        dispatchedAt: Instant,
        token: String,
    ): GitHubRunReference? {
        repeat(DISPATCH_RUN_LOOKUP_ATTEMPTS) { attempt ->
            val runs = fetchWorkflowDispatchRuns(owner, repository, workflowId, token)
            val candidate = runs.asSequence()
                .filter { it.branch == branch }
                .filter { reference ->
                    reference.createdAt?.let {
                        runCatching {
                            Instant.parse(it).isAfter(dispatchedAt.minusSeconds(3))
                        }.getOrDefault(false)
                    } == true
                }
                .sortedByDescending { it.id }
                .firstOrNull()
            if (candidate != null) return GitHubRunReference(candidate.id, candidate.htmlUrl)
            if (attempt + 1 < DISPATCH_RUN_LOOKUP_ATTEMPTS) {
                delay(DISPATCH_RUN_LOOKUP_DELAY_MS)
            }
        }
        return null
    }

    private fun fetchWorkflowDispatchRuns(
        owner: String,
        repository: String,
        workflowId: String,
        token: String,
    ): List<WorkflowRunReference> {
        val endpoint =
            "https://api.github.com/repos/" + owner + "/" + repository +
                "/actions/workflows/" + workflowId + "/runs?event=workflow_dispatch&per_page=20"
        val http = connection.open(endpoint).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        return try {
            val code = http.responseCode
            val stream = if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream
            val body = stream.use {
                it.readBounded(DISPATCH_RUN_LOOKUP_MAX_BYTES).toString(Charsets.UTF_8)
            }
            if (code !in 200..299) {
                val suffix = if (body.isBlank()) "." else ": " + sanitizeError(body)
                throw IllegalStateException("GitHub run lookup failed (HTTP " + code + ")" + suffix)
            }
            parseWorkflowRuns(body)
        } finally {
            http.disconnect()
        }
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
                branch = branch.take(MAX_BRANCH_LENGTH),
                createdAt = run.optString("created_at").takeIf(String::isNotBlank),
                htmlUrl = run.optString("html_url").takeIf(String::isNotBlank),
            )
        }
        return result
    }

    fun listRecentRuns(
        owner: String,
        repository: String,
        workflowFile: String? = null,
        perPage: Int = 20,
    ): GitHubWorkflowRunsResult {
        val normalizedOwner = validateRepositoryPart(owner)
            ?: return GitHubWorkflowRunsResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateRepositoryPart(repository)
            ?: return GitHubWorkflowRunsResult.Failure("The GitHub repository is invalid.")
        val workflowSuffix = workflowFile?.trim()?.takeIf { it.isNotBlank() }
            ?.substringAfterLast('/')
            ?.takeIf { WORKFLOW_NAME.matches(it) }
        val path = buildString {
            append("/repos/").append(normalizedOwner).append("/").append(normalizedRepository).append("/actions/")
            if (workflowSuffix != null) append("workflows/").append(workflowSuffix).append("/")
            append("runs?per_page=").append(perPage.coerceIn(1, 50))
        }
        return requestBody(path, "GET", 220_000).mapCatching { body ->
            val array = JSONObject(body).optJSONArray("workflow_runs") ?: JSONArray()
            buildList {
                for (index in 0 until minOf(array.length(), 50)) {
                    val run = array.optJSONObject(index) ?: continue
                    val id = run.optLong("id")
                    val runNumber = run.optLong("run_number")
                    if (id <= 0L || runNumber <= 0L) continue
                    add(
                        GitHubWorkflowRun(
                            id = id,
                            runNumber = runNumber,
                            name = run.optString("name", "GitHub Actions run").take(MAX_TEXT_FIELD),
                            status = run.optString("status", "unknown").take(MAX_TEXT_FIELD),
                            conclusion = run.optString("conclusion").takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
                            branch = run.optString("head_branch", "unknown").take(MAX_BRANCH_LENGTH),
                            event = run.optString("event", "unknown").take(MAX_TEXT_FIELD),
                            workflowFile = workflowSuffix.orEmpty(),
                            createdAt = run.optString("created_at").takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
                            htmlUrl = run.optString("html_url").takeIf(String::isNotBlank)?.take(MAX_URL_FIELD),
                        ),
                    )
                }
            }
        }.fold(
            onSuccess = { GitHubWorkflowRunsResult.Success(it) },
            onFailure = { GitHubWorkflowRunsResult.Failure(safeMessage(it)) },
        )
    }

    fun getRun(owner: String, repository: String, runId: Long): GitHubRunResult {
        val normalizedOwner = validateRepositoryPart(owner)
            ?: return GitHubRunResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateRepositoryPart(repository)
            ?: return GitHubRunResult.Failure("The GitHub repository is invalid.")
        if (runId <= 0L) return GitHubRunResult.Failure("The workflow run identifier is invalid.")
        val path = "/repos/" + normalizedOwner + "/" + normalizedRepository + "/actions/runs/" + runId
        return getJson(path) { json ->
            GitHubRunSnapshot(
                id = json.optLong("id"),
                runNumber = json.optLong("run_number"),
                name = json.optString("name", "GitHub Actions run").take(MAX_TEXT_FIELD),
                status = json.optString("status", "unknown").take(MAX_TEXT_FIELD),
                conclusion = json.optString("conclusion").takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
                htmlUrl = json.optString("html_url").takeIf(String::isNotBlank)?.take(MAX_URL_FIELD),
                branch = json.optString("head_branch", "unknown").take(MAX_BRANCH_LENGTH),
                event = json.optString("event", "unknown").take(MAX_TEXT_FIELD),
                createdAt = json.optString("created_at").takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
                updatedAt = json.optString("updated_at").takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
            )
        }.fold(
            onSuccess = { GitHubRunResult.Success(it) },
            onFailure = { GitHubRunResult.Failure(safeMessage(it)) },
        )
    }

    fun listArtifacts(owner: String, repository: String, runId: Long): GitHubArtifactsResult {
        val normalizedOwner = validateRepositoryPart(owner)
            ?: return GitHubArtifactsResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateRepositoryPart(repository)
            ?: return GitHubArtifactsResult.Failure("The GitHub repository is invalid.")
        if (runId <= 0L) return GitHubArtifactsResult.Failure("The workflow run identifier is invalid.")
        val path = "/repos/" + normalizedOwner + "/" + normalizedRepository +
            "/actions/runs/" + runId + "/artifacts?per_page=100"
        return getJson(path) { json ->
            val source = json.optJSONArray("artifacts") ?: JSONArray()
            buildList(minOf(source.length(), MAX_ARTIFACTS)) {
                for (index in 0 until minOf(source.length(), MAX_ARTIFACTS)) {
                    val artifact = source.optJSONObject(index) ?: continue
                    add(
                        GitHubArtifact(
                            id = artifact.optLong("id"),
                            name = artifact.optString("name", "Unnamed artifact").take(MAX_TEXT_FIELD),
                            sizeBytes = artifact.optLong("size_in_bytes").coerceAtLeast(0L),
                            expired = artifact.optBoolean("expired", false),
                            archiveDownloadUrl = artifact.optString("archive_download_url")
                                .takeIf(String::isNotBlank)?.take(MAX_URL_FIELD),
                            createdAt = artifact.optString("created_at")
                                .takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
                            expiresAt = artifact.optString("expires_at")
                                .takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
                        ),
                    )
                }
            }
        }.fold(
            onSuccess = { GitHubArtifactsResult.Success(it) },
            onFailure = { GitHubArtifactsResult.Failure(safeMessage(it)) },
        )
    }

    fun fetchLogs(
        owner: String,
        repository: String,
        runId: Long,
        maxJobs: Int = 4,
        maxBytes: Int = 220_000,
    ): GitHubLogsResult {
        val normalizedOwner = validateRepositoryPart(owner)
            ?: return GitHubLogsResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateRepositoryPart(repository)
            ?: return GitHubLogsResult.Failure("The GitHub repository is invalid.")
        if (runId <= 0L) return GitHubLogsResult.Failure("The workflow run identifier is invalid.")
        val safeMaxJobs = maxJobs.coerceIn(1, MAX_LOG_JOBS)
        val safeMaxBytes = maxBytes.coerceIn(4 * 1024, MAX_LOG_BYTES)
        val path = "/repos/" + normalizedOwner + "/" + normalizedRepository +
            "/actions/runs/" + runId + "/jobs?per_page=100"
        val jobs = getJson(path) { json ->
            val source = json.optJSONArray("jobs") ?: JSONArray()
            buildList(minOf(source.length(), MAX_JOBS_PER_RESPONSE)) {
                for (index in 0 until minOf(source.length(), MAX_JOBS_PER_RESPONSE)) {
                    val job = source.optJSONObject(index) ?: continue
                    val id = job.optLong("id")
                    if (id <= 0L) continue
                    add(
                        JobDescriptor(
                            id = id,
                            name = job.optString("name", "job").take(MAX_TEXT_FIELD),
                            status = job.optString("status", "unknown").take(MAX_TEXT_FIELD),
                            conclusion = job.optString("conclusion")
                                .takeIf(String::isNotBlank)?.take(MAX_TEXT_FIELD),
                            htmlUrl = job.optString("html_url")
                                .takeIf(String::isNotBlank)?.take(MAX_URL_FIELD),
                            logsUrl = job.optString("logs_url")
                                .takeIf(String::isNotBlank)?.take(MAX_URL_FIELD),
                        ),
                    )
                }
            }
        }.getOrElse { return GitHubLogsResult.Failure(safeMessage(it)) }

        val selected = jobs
            .filter { it.status != "queued" }
            .sortedByDescending { it.id }
            .take(safeMaxJobs)
        var usedBytes = 0
        var truncated = false
        val result = mutableListOf<GitHubJobLog>()

        for (job in selected) {
            if (usedBytes >= safeMaxBytes) {
                truncated = true
                break
            }
            val limit = safeMaxBytes - usedBytes
            val logSource = job.logsUrl
                ?: "https://api.github.com/repos/" + normalizedOwner + "/" + normalizedRepository + "/actions/jobs/" + job.id + "/logs"
            val text = getTextUrl(logSource, limit).getOrElse { error ->
                return GitHubLogsResult.Failure(
                    "Unable to read logs for " + job.name + ": " + safeMessage(error),
                )
            }
            val bytes = text.toByteArray(Charsets.UTF_8).size
            usedBytes += bytes
            if (bytes >= limit) truncated = true
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
        getTextUrl("https://api.github.com" + validatedPath(path), maxBytes)

    private fun getTextUrl(url: String, maxBytes: Int): Result<String> {
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return Result.failure(IllegalStateException("GitHub is not connected on this device."))
        val uri = runCatching { java.net.URI(url) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("GitHub log URL is invalid."))
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) {
            return Result.failure(IllegalArgumentException("GitHub log URL is outside the secure HTTPS boundary."))
        }
        return readRedirectedBody(
            url = url,
            token = token,
            maxBytes = maxBytes.coerceIn(4 * 1024, MAX_LOG_BYTES),
        )
    }

    suspend fun downloadArtifact(
        owner: String,
        repository: String,
        artifactId: Long,
        output: java.io.OutputStream,
        maxBytes: Long = MAX_ARTIFACT_DOWNLOAD_BYTES,
    ): Result<Long> {
        val normalizedOwner = validateRepositoryPart(owner)
            ?: return Result.failure(IllegalArgumentException("The GitHub owner is invalid."))
        val normalizedRepository = validateRepositoryPart(repository)
            ?: return Result.failure(IllegalArgumentException("The GitHub repository is invalid."))
        if (artifactId <= 0L) return Result.failure(IllegalArgumentException("The artifact identifier is invalid."))
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return Result.failure(IllegalStateException("GitHub is not connected on this device."))
        val url = "https://api.github.com/repos/" + normalizedOwner + "/" + normalizedRepository +
            "/actions/artifacts/" + artifactId + "/zip"
        return withContext(Dispatchers.IO) {
            runCatching {
                streamRedirectedBody(url, token, maxBytes.coerceIn(1L, MAX_ARTIFACT_DOWNLOAD_BYTES), output)
            }
        }
    }

    private fun readRedirectedBody(url: String, token: String, maxBytes: Int): Result<String> =
        runCatching {
            val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            streamRedirectedBody(url, token, maxBytes.toLong(), output)
            output.toString(Charsets.UTF_8.name())
        }

    private fun streamRedirectedBody(
        initialUrl: String,
        token: String,
        maxBytes: Long,
        output: java.io.OutputStream,
    ): Long {
        var url = initialUrl
        var total = 0L
        repeat(MAX_GITHUB_REDIRECTS + 1) { redirectIndex ->
            val uri = java.net.URI(url)
            require(uri.scheme.equals("https", ignoreCase = true)) {
                "GitHub returned an insecure download URL."
            }
            require(uri.userInfo.isNullOrBlank()) {
                "GitHub returned a download URL containing embedded credentials."
            }

            val http = connection.open(url).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                if (redirectIndex == 0) setRequestProperty("Authorization", "Bearer " + token)
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            try {
                val code = http.responseCode
                if (code in 300..399) {
                    if (redirectIndex >= MAX_GITHUB_REDIRECTS) {
                        error("GitHub returned too many redirects.")
                    }
                    val location = http.getHeaderField("Location")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: error("GitHub returned a redirect without a Location header.")
                    val resolved = java.net.URI(url).resolve(location).toString()
                    val resolvedUri = java.net.URI(resolved)
                    require(resolvedUri.scheme.equals("https", ignoreCase = true)) {
                        "GitHub returned an insecure redirect URL."
                    }
                    require(resolvedUri.userInfo.isNullOrBlank()) {
                        "GitHub returned a redirect URL containing embedded credentials."
                    }
                    url = resolved
                    return@repeat
                }
                val stream = if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream
                if (code !in 200..299) {
                    val body = stream?.use { it.readBounded(MAX_DIRECT_RESPONSE_BYTES) }
                        ?.toString(Charsets.UTF_8).orEmpty()
                    error(
                        "GitHub download request failed (HTTP " + code + ")" +
                            if (body.isBlank()) "." else ": " + sanitizeError(body),
                    )
                }
                stream?.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) error("The GitHub download exceeded the DevForge size limit.")
                        output.write(buffer, 0, read)
                    }
                }
                output.flush()
                return total
            } finally {
                http.disconnect()
            }
        }
        error("GitHub download did not complete.")
    }

    private fun requestBody(path: String, method: String, maxBytes: Int = 320_000): Result<String> {
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return Result.failure(IllegalStateException("GitHub is not connected on this device."))
        val boundedBytes = maxBytes.coerceIn(4 * 1024, 2 * 1024 * 1024)

        return runCatching {
            val endpoint = "https://api.github.com" + validatedPath(path)
            val http = connection.open(endpoint).apply {
                requestMethod = method
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer " + token)
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                val code = http.responseCode
                val stream = if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream
                val body = stream.use {
                    it.readBounded(boundedBytes).toString(Charsets.UTF_8)
                }
                if (code !in 200..299) {
                    val suffix = if (body.isBlank()) "." else ": " + sanitizeError(body)
                    throw IllegalStateException("GitHub request failed (HTTP " + code + ")" + suffix)
                }
                body
            } finally {
                http.disconnect()
            }
        }
    }

    private fun validatedPath(path: String): String {
        require(path.length <= MAX_PATH_LENGTH) { "GitHub API path is too long." }
        require(!path.any { it == '\n' || it == '\r' }) { "GitHub API path contains control characters." }
        require(!path.contains("..")) { "GitHub API path contains invalid traversal markers." }
        return path
    }

    private fun validateRepositoryPart(value: String): String? =
        value.trim().takeIf {
            it.isNotBlank() &&
                it.length <= 100 &&
                OWNER_OR_REPOSITORY.matches(it)
        }

    private fun safeMessage(error: Throwable): String =
        error.message
            ?.takeIf(String::isNotBlank)
            ?.let(::sanitizeError)
            ?.take(280)
            ?: "GitHub request failed."

    private fun sanitizeError(body: String): String =
        body.replace(
            Regex("(?i)(token|authorization|access[_-]?token)\\s*[:=]\\s*[^,}\\s]+"),
            "$1=[redacted]",
        ).take(280)

    private data class GitHubRunReference(val runId: Long, val htmlUrl: String?)
    private data class WorkflowRunReference(
        val id: Long,
        val branch: String,
        val createdAt: String?,
        val htmlUrl: String?,
    )
    private data class JobDescriptor(
        val id: Long,
        val name: String,
        val status: String,
        val conclusion: String?,
        val htmlUrl: String?,
        val logsUrl: String?,
    )

    companion object {
        const val API_VERSION = "2026-03-10"
        const val TARGET_CONTRACT_WORKFLOW = "android.yml"
        private const val DISPATCH_RUN_LOOKUP_ATTEMPTS = 6
        private const val DISPATCH_RUN_LOOKUP_DELAY_MS = 1_500L
        private const val DISPATCH_RUN_LOOKUP_MAX_BYTES = 180_000
        private const val MAX_DIRECT_RESPONSE_BYTES = 320_000
        private const val MAX_LOG_JOBS = 8
        private const val MAX_LOG_BYTES = 512 * 1024
        private const val MAX_GITHUB_REDIRECTS = 3
        private const val MAX_ARTIFACT_DOWNLOAD_BYTES = 150L * 1024L * 1024L
        private const val MAX_JOBS_PER_RESPONSE = 100
        private const val MAX_ARTIFACTS = 100
        private const val MAX_PATH_LENGTH = 500
        private const val MAX_BRANCH_LENGTH = 255
        private const val MAX_TEXT_FIELD = 500
        private const val MAX_URL_FIELD = 2_000
        private val OWNER_OR_REPOSITORY = Regex("^[A-Za-z0-9_.-]+$")
        private val WORKFLOW_NAME = Regex("^[A-Za-z0-9_.-]+$")
        fun forBuildStore(
            secretStore: com.mrredhood.devforge.core.security.SecretStore,
        ): GitHubActionsGateway = GitHubActionsGateway(secretStore)
    }
}

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val limit = maxBytes.coerceAtLeast(1)
    val output = java.io.ByteArrayOutputStream(minOf(limit, 32_768))
    val buffer = ByteArray(8_192)
    var total = 0
    while (total <= limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit + 1 - total))
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
        if (total > limit) {
            error("GitHub response exceeded the DevForge response limit.")
        }
    }
    return output.toByteArray()
}

fun interface HttpConnectionFactory {
    fun open(url: String): HttpURLConnection
}

private val DefaultHttpConnectionFactory = HttpConnectionFactory { url ->
    URL(url).openConnection() as HttpURLConnection
}
