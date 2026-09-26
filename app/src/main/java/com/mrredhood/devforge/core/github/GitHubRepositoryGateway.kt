package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/** GitHub REST boundary for account, repository, workflow and controlled repository operations. */
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

    fun listOrganizations(): Result<List<String>> =
        getJsonArray("/user/orgs?per_page=100&page=1").map { json ->
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val login = json.optJSONObject(index)?.optString("login").orEmpty()
                    if (login.isNotBlank() && SAFE_NAME.matches(login)) add(login)
                }
            }
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


    fun deleteRepository(owner: String, repository: String): Result<Unit> {
        val normalizedOwner = validateName(owner)
            ?: return Result.failure(IllegalArgumentException("The GitHub owner is invalid."))
        val normalizedRepository = validateName(repository)
            ?: return Result.failure(IllegalArgumentException("The GitHub repository is invalid."))
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("GitHub is not connected on this device."))

        return runCatching {
            val endpoint = "https://api.github.com/repos/$normalizedOwner/$normalizedRepository"
            val http = connection.open(endpoint).apply {
                requestMethod = "DELETE"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            try {
                val code = http.responseCode
                if (code !in 200..299) {
                    val body = (http.errorStream ?: http.inputStream)
                        .use { it.readBounded(MAX_RESPONSE_BYTES) }
                        .toString(Charsets.UTF_8)
                    throw IllegalStateException(
                        "GitHub repository deletion failed (HTTP $code)" +
                            if (body.isBlank()) "." else ": " + sanitizeError(body),
                    )
                }
            } finally {
                http.disconnect()
            }
        }
    }

    fun listBranches(owner: String, repository: String): GitHubBranchesResult {
        val o = validateName(owner) ?: return GitHubBranchesResult.Failure("The GitHub owner is invalid.")
        val r = validateName(repository) ?: return GitHubBranchesResult.Failure("The GitHub repository is invalid.")
        return getJsonArray("/repos/" + o + "/" + r + "/branches?per_page=100").fold(
            onSuccess = { array ->
                GitHubBranchesResult.Success(
                    buildList(array.length()) {
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                            add(name)
                        }
                    },
                )
            },
            onFailure = { GitHubBranchesResult.Failure(safeMessage(it)) },
        )
    }

    fun listContents(
        owner: String,
        repository: String,
        path: String = "",
        branch: String = "main",
    ): GitHubContentsResult {
        val o = validateName(owner) ?: return GitHubContentsResult.Failure("The GitHub owner is invalid.")
        val r = validateName(repository) ?: return GitHubContentsResult.Failure("The GitHub repository is invalid.")
        val cleanPath = normalizeRepoPathForRead(path)
        val encodedPath = cleanPath.split('/').filter(String::isNotBlank)
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val encodedBranch = URLEncoder.encode(branch.trim().ifBlank { "main" }, "UTF-8").replace("+", "%20")
        val suffix = if (encodedPath.isBlank()) "" else "/" + encodedPath
        return readBody("/repos/" + o + "/" + r + "/contents" + suffix + "?ref=" + encodedBranch).fold(
            onSuccess = { body ->
                runCatching {
                    if (body.trimStart().startsWith("[")) {
                        val array = JSONArray(body)
                        buildList(array.length()) {
                            for (i in 0 until array.length()) {
                                array.optJSONObject(i)?.let { add(parseContentEntry(it)) }
                            }
                        }
                    } else {
                        listOf(parseContentEntry(JSONObject(body)))
                    }
                }.fold(
                    onSuccess = { GitHubContentsResult.Success(it) },
                    onFailure = { GitHubContentsResult.Failure(safeMessage(it)) },
                )
            },
            onFailure = { GitHubContentsResult.Failure(safeMessage(it)) },
        )
    }

    fun readFile(
        owner: String,
        repository: String,
        path: String,
        branch: String = "main",
    ): GitHubFileResult {
        return when (val result = listContents(owner, repository, path, branch)) {
            is GitHubContentsResult.Failure -> GitHubFileResult.Failure(result.message)
            is GitHubContentsResult.Success -> {
                val item = result.entries.singleOrNull()
                    ?: return GitHubFileResult.Failure("GitHub did not return exactly one file for " + path + ".")
                if (item.type != "file") return GitHubFileResult.Failure(path + " is not a file.")
                val decoded = runCatching {
                    Base64.getMimeDecoder().decode(item.contentBase64.orEmpty()).toString(Charsets.UTF_8)
                }.getOrElse {
                    return GitHubFileResult.Failure("Unable to decode GitHub file content.")
                }
                GitHubFileResult.Success(item.path, item.sha, decoded)
            }
        }
    }

    fun readFileBytes(
        owner: String,
        repository: String,
        path: String,
        branch: String = "main",
    ): Result<ByteArray> = runCatching {
        val result = listContents(owner, repository, path, branch)
        val entries = when (result) {
            is GitHubContentsResult.Failure -> error(result.message)
            is GitHubContentsResult.Success -> result.entries
        }
        val item = entries.singleOrNull() ?: error("GitHub did not return exactly one file for $path.")
        require(item.type == "file") { "$path is not a file." }
        Base64.getMimeDecoder().decode(item.contentBase64.orEmpty())
    }

    fun commitChanges(
        owner: String,
        repository: String,
        branch: String,
        message: String,
        changes: List<GitHubTreeChange>,
    ): GitHubCommitResult {
        val o = validateName(owner) ?: return GitHubCommitResult.Failure("The GitHub owner is invalid.")
        val r = validateName(repository) ?: return GitHubCommitResult.Failure("The GitHub repository is invalid.")
        val b = branch.trim().ifBlank { "main" }
        requireSafeBranch(b).getOrElse {
            return GitHubCommitResult.Failure(it.message ?: "The Git branch is invalid.")
        }
        val normalizedMessage = message.trim().take(500).ifBlank {
            return GitHubCommitResult.Failure("Commit message cannot be empty.")
        }
        if (changes.isEmpty()) return GitHubCommitResult.Failure("There are no changes to commit.")
        if (changes.size > 120) return GitHubCommitResult.Failure("Too many remote changes in one commit.")

        return runCatching {
            val refPath = "/repos/" + o + "/" + r + "/git/ref/heads/" + encodePathSegment(b)

            val tree = JSONArray()
            changes.forEach { change ->
                val row = JSONObject()
                    .put("path", normalizeRepoPath(change.path))
                    .put("mode", "100644")
                    .put("type", "blob")
                if (change.delete) row.put("sha", JSONObject.NULL)
                else if (!change.blobSha.isNullOrBlank()) row.put("sha", change.blobSha)
                else row.put("content", change.content.orEmpty())
                tree.put(row)
            }

            var parentCommit = ""
            var treeSha: String? = null
            var lastTreeError: Throwable? = null
            repeat(3) { attempt ->
                parentCommit = getJson(refPath) { it.getJSONObject("object").getString("sha") }.getOrThrow()
                val baseTree = getJson("/repos/" + o + "/" + r + "/git/commits/" + parentCommit) {
                    it.getJSONObject("tree").getString("sha")
                }.getOrThrow()
                try {
                    treeSha = postJson(
                        "/repos/" + o + "/" + r + "/git/trees",
                        JSONObject().put("base_tree", baseTree).put("tree", tree),
                    ).getString("sha")
                    lastTreeError = null
                } catch (error: Throwable) {
                    if (!error.message.orEmpty().contains("HTTP 404") || attempt == 2) throw error
                    lastTreeError = error
                    Thread.sleep(250L * (attempt + 1))
                }
                if (treeSha != null) return@repeat
            }
            val createdTreeSha = treeSha ?: throw (lastTreeError ?: IllegalStateException("Unable to create the GitHub tree."))

            val commitSha = postJson(
                "/repos/" + o + "/" + r + "/git/commits",
                JSONObject()
                    .put("message", normalizedMessage)
                    .put("tree", createdTreeSha)
                    .put("parents", JSONArray().put(parentCommit)),
            ).getString("sha")

            patchJson(
                "/repos/" + o + "/" + r + "/git/refs/heads/" + encodePathSegment(b),
                JSONObject().put("sha", commitSha).put("force", false),
            )

            GitHubCommitResult.Success(commitSha, normalizedMessage, changes.map { normalizeRepoPath(it.path) })
        }.getOrElse { GitHubCommitResult.Failure(safeMessage(it)) }
    }

    private fun parseContentEntry(json: JSONObject): GitHubContentEntry = GitHubContentEntry(
        path = normalizeRepoPath(json.optString("path")),
        name = json.optString("name"),
        type = json.optString("type"),
        sizeBytes = json.optLong("size", 0L).takeIf { it >= 0L },
        sha = json.optString("sha").takeIf(String::isNotBlank),
        contentBase64 = json.optString("content").takeIf(String::isNotBlank),
        htmlUrl = json.optString("html_url").takeIf(String::isNotBlank),
    )

    private fun requireSafeBranch(value: String): Result<Unit> = runCatching {
        require(
            value.length <= 255 &&
                !value.contains('\\') &&
                !value.contains("..") &&
                !value.startsWith("/") &&
                !value.endsWith("/") &&
                !value.contains(' ') &&
                !value.contains('\n') &&
                !value.contains('\r'),
        ) { "The Git branch is invalid." }
    }

    private fun normalizeRepoPathForRead(value: String): String {
        val normalized = value.trim().replace('\\', '/').trim('/')
        require(normalized.length <= 1024 && !normalized.contains('\n') && !normalized.contains('\r')) {
            "The repository path is invalid."
        }
        require(normalized.split('/').none { it == "." || it == ".." }) { "The repository path is invalid." }
        return normalized
    }

    private fun normalizeRepoPath(value: String): String {
        val normalized = normalizeRepoPathForRead(value)
        require(normalized.isNotBlank()) { "The repository path is invalid." }
        return normalized
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%2F", "/")

    private fun postJson(path: String, payload: JSONObject): JSONObject = requestJson("POST", path, payload)
    private fun patchJson(path: String, payload: JSONObject): JSONObject = requestJson("PATCH", path, payload)

    private fun requestJson(method: String, path: String, payload: JSONObject): JSONObject {
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: throw IllegalStateException("GitHub is not connected on this device.")
        val http = connection.open("https://api.github.com" + path).apply {
            requestMethod = method
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        return try {
            http.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = http.responseCode
            val body = (if (code in 200..299) http.inputStream else http.errorStream ?: http.inputStream)
                .use { it.readBounded(MAX_RESPONSE_BYTES) }
                .toString(Charsets.UTF_8)
            if (code !in 200..299) {
                throw IllegalStateException("GitHub request failed (HTTP " + code + ")" + if (body.isBlank()) "." else ": " + sanitizeError(body))
            }
            JSONObject(body)
        } finally {
            http.disconnect()
        }
    }

    fun createRepository(request: GitHubRepositoryCreationRequest): GitHubRepositoryResult {
        val normalizedName = request.name.trim()
        if (validateName(normalizedName) == null) return GitHubRepositoryResult.Failure("The repository name is invalid.")
        if (request.description.length > 5_000 || request.homepage.length > 2_000) {
            return GitHubRepositoryResult.Failure("Repository metadata is too large.")
        }
        val branch = request.defaultBranch.trim().ifBlank { "main" }
        val branchValid = branch.length <= 255 &&
            !branch.contains(' ') &&
            !branch.contains('\\') &&
            !branch.contains("..") &&
            !branch.startsWith("/") &&
            !branch.endsWith("/") &&
            !branch.contains('\n') &&
            !branch.contains('\r')
        if (!branchValid) return GitHubRepositoryResult.Failure("The default branch is invalid.")

        return runCatching {
            val payload = JSONObject()
                .put("name", normalizedName)
                .put("description", request.description.trim())
                .put("homepage", request.homepage.trim())
                .put("private", request.private)
                .put("has_issues", request.hasIssues)
                .put("has_projects", request.hasProjects)
                .put("has_wiki", request.hasWiki)
                .put("has_discussions", request.hasDiscussions)
                .put("has_downloads", request.hasDownloads)
                .put("is_template", request.isTemplate)
                .put("auto_init", request.autoInit)
                .put("allow_squash_merge", request.allowSquashMerge)
                .put("allow_merge_commit", request.allowMergeCommit)
                .put("allow_rebase_merge", request.allowRebaseMerge)
                .put("allow_auto_merge", request.allowAutoMerge)
                .put("delete_branch_on_merge", request.deleteBranchOnMerge)
                .put("squash_merge_commit_title", request.squashMergeTitle)
                .put("squash_merge_commit_message", request.squashMergeMessage)
                .put("merge_commit_title", request.mergeCommitTitle)
                .put("merge_commit_message", request.mergeCommitMessage)

            if (request.gitignoreTemplate.isNotBlank()) {
                payload.put("gitignore_template", request.gitignoreTemplate.trim())
            }
            if (request.licenseTemplate.isNotBlank()) {
                payload.put("license_template", request.licenseTemplate.trim())
            }

            val endpoint = request.organization?.trim()?.takeIf { it.isNotBlank() }
                ?.let { "/orgs/" + it + "/repos" }
                ?: "/user/repos"
            if (request.organization != null) {
                payload.put("visibility", request.visibility)
            }
            request.teamId?.let { payload.put("team_id", it) }
            if (request.customPropertiesJson.isNotBlank()) {
                payload.put("custom_properties", JSONObject(request.customPropertiesJson))
            }
            val created = postJson(endpoint, payload)
            val repository = parseRepository(created)
            if (request.autoInit && branch != repository.defaultBranch) {
                val repoOwner = created.optJSONObject("owner")?.optString("login").orEmpty()
                if (repoOwner.isBlank()) {
                    throw IllegalStateException("GitHub did not return the new repository owner.")
                }
                val baseSha = created.optString("default_branch_sha").takeIf { it.matches(SHA_PATTERN) }
                    ?: getJson("/repos/" + repoOwner + "/" + normalizedName + "/commits/" + repository.defaultBranch) {
                        it.optString("sha")
                    }.getOrNull()?.takeIf { it.matches(SHA_PATTERN) }
                    ?: throw IllegalStateException("Unable to resolve the initialized branch.")
                postJson(
                    "/repos/" + repoOwner + "/" + normalizedName + "/git/refs",
                    JSONObject().put("ref", "refs/heads/" + branch).put("sha", baseSha),
                )
                patchJson(
                    "/repos/" + repoOwner + "/" + normalizedName,
                    JSONObject().put("default_branch", branch),
                )
                return@runCatching GitHubRepository(
                    id = repository.id,
                    owner = repository.owner,
                    name = repository.name,
                    fullName = repository.fullName,
                    isPrivate = repository.isPrivate,
                    defaultBranch = branch,
                )
            }
            repository
        }.fold(
            onSuccess = { GitHubRepositoryResult.Success(it) },
            onFailure = { GitHubRepositoryResult.Failure(safeMessage(it)) },
        )
    }

    fun updateRepository(
        owner: String,
        repository: String,
        request: GitHubRepositoryUpdateRequest,
    ): GitHubRepositoryResult {
        val normalizedOwner = validateName(owner)
            ?: return GitHubRepositoryResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateName(repository)
            ?: return GitHubRepositoryResult.Failure("The GitHub repository is invalid.")
        val normalizedName = validateName(request.name)
            ?: return GitHubRepositoryResult.Failure("The repository name is invalid.")
        if (request.description.length > 5_000 || request.homepage.length > 2_000) {
            return GitHubRepositoryResult.Failure("Repository metadata is too large.")
        }
        val branch = request.defaultBranch.trim().ifBlank { "main" }
        requireSafeBranch(branch).getOrElse {
            return GitHubRepositoryResult.Failure(it.message ?: "The default branch is invalid.")
        }
        val visibility = request.visibility.trim().lowercase()
        if (visibility !in setOf("public", "private", "internal")) {
            return GitHubRepositoryResult.Failure("Repository visibility must be public, private, or internal.")
        }
        return runCatching {
            parseRepository(
                patchJson(
                    "/repos/" + normalizedOwner + "/" + normalizedRepository,
                    JSONObject()
                        .put("name", normalizedName)
                        .put("description", request.description.trim())
                        .put("homepage", request.homepage.trim())
.put("private", visibility == "private")
                        .put("visibility", visibility)
                        .put("has_issues", request.hasIssues)
                        .put("has_projects", request.hasProjects)
                        .put("has_wiki", request.hasWiki)
                        .put("has_discussions", request.hasDiscussions)
                        .put("has_downloads", request.hasDownloads)
                        .put("allow_squash_merge", request.allowSquashMerge)
                        .put("allow_merge_commit", request.allowMergeCommit)
                        .put("allow_rebase_merge", request.allowRebaseMerge)
                        .put("allow_auto_merge", request.allowAutoMerge)
                        .put("delete_branch_on_merge", request.deleteBranchOnMerge)
                        .put("squash_merge_commit_title", request.squashMergeTitle)
                        .put("squash_merge_commit_message", request.squashMergeMessage)
                        .put("merge_commit_title", request.mergeCommitTitle)
                        .put("merge_commit_message", request.mergeCommitMessage)
                        .put("default_branch", branch),
                ),
            )
        }.fold(
            onSuccess = { GitHubRepositoryResult.Success(it) },
            onFailure = { GitHubRepositoryResult.Failure(safeMessage(it)) },
        )
    }

    fun getCommitDetails(
        owner: String,
        repository: String,
        commitSha: String,
    ): GitHubCommitDetailsResult {
        val o = validateName(owner) ?: return GitHubCommitDetailsResult.Failure("The GitHub owner is invalid.")
        val r = validateName(repository) ?: return GitHubCommitDetailsResult.Failure("The GitHub repository is invalid.")
        val sha = commitSha.trim().takeIf { it.matches(SHA_PATTERN) }
            ?: return GitHubCommitDetailsResult.Failure("The commit SHA is invalid.")
        return getJson("/repos/" + o + "/" + r + "/commits/" + sha) { json ->
            val commit = json.optJSONObject("commit") ?: JSONObject()
            val author = commit.optJSONObject("author")
            val parents = buildList {
                val array = json.optJSONArray("parents") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val parent = array.optJSONObject(i)?.optString("sha").orEmpty()
                    if (parent.matches(SHA_PATTERN)) add(parent)
                }
            }
            val files = buildList {
                val array = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until minOf(array.length(), MAX_COMMIT_FILES)) {
                    val item = array.optJSONObject(i) ?: continue
                    val filename = item.optString("filename").takeIf(String::isNotBlank) ?: continue
                    add(
                        GitHubCommitFileChange(
                            path = filename,
                            status = item.optString("status", "modified").take(32),
                            additions = item.optInt("additions", 0).coerceAtLeast(0),
                            deletions = item.optInt("deletions", 0).coerceAtLeast(0),
                            changes = item.optInt("changes", 0).coerceAtLeast(0),
                            previousPath = item.optString("previous_filename").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
            GitHubCommitDetails(
                sha = json.optString("sha", sha),
                subject = commit.optString("message").lineSequence().firstOrNull().orEmpty().trim(),
                fullMessage = commit.optString("message").take(MAX_COMMIT_MESSAGE),
                author = author?.optString("name").orEmpty().ifBlank {
                    json.optJSONObject("author")?.optString("login").orEmpty()
                },
                authoredAt = author?.optString("date"),
                parents = parents,
                additions = json.optJSONObject("stats")?.optInt("additions", 0)?.coerceAtLeast(0) ?: 0,
                deletions = json.optJSONObject("stats")?.optInt("deletions", 0)?.coerceAtLeast(0) ?: 0,
                changedFiles = files,
            )
        }.fold(
            onSuccess = { GitHubCommitDetailsResult.Success(it) },
            onFailure = { GitHubCommitDetailsResult.Failure(safeMessage(it)) },
        )
    }

    fun revertCommit(
        owner: String,
        repository: String,
        branch: String,
        commitSha: String,
        message: String,
    ): GitHubCommitResult {
        val details = when (val result = getCommitDetails(owner, repository, commitSha)) {
            is GitHubCommitDetailsResult.Success -> result.value
            is GitHubCommitDetailsResult.Failure -> return GitHubCommitResult.Failure(result.message)
        }
        val parent = details.parents.firstOrNull()
            ?: return GitHubCommitResult.Failure("The initial repository commit cannot be reverted automatically.")
        if (details.changedFiles.isEmpty()) {
            return GitHubCommitResult.Failure("This commit has no file-level changes that can be reverted.")
        }
        val changes = mutableListOf<GitHubTreeChange>()
        details.changedFiles.take(MAX_COMMIT_FILES).forEach { file ->
            val parentPath = file.previousPath ?: file.path
            val parentEntry = when (val result = listContents(owner, repository, parentPath, parent)) {
                is GitHubContentsResult.Success -> result.entries.singleOrNull { it.path == parentPath && it.type == "file" }
                is GitHubContentsResult.Failure -> null
            }
            if (file.status == "added" || file.status == "renamed") {
                changes += GitHubTreeChange(path = file.path, delete = true)
            }
            if (file.status != "added") {
                val sha = parentEntry?.sha
                    ?: return GitHubCommitResult.Failure("Unable to resolve the parent blob for " + parentPath + ".")
                changes += GitHubTreeChange(path = parentPath, blobSha = sha)
            }
        }
        if (changes.isEmpty()) return GitHubCommitResult.Failure("There are no revertable file changes.")
        return commitChanges(
            owner = owner,
            repository = repository,
            branch = branch,
            message = message.ifBlank { "Revert " + commitSha.take(12) },
            changes = changes.distinctBy { it.path + "|" + it.delete + "|" + it.blobSha },
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

    fun createBranch(owner: String, repository: String, branch: String, fromBranch: String = "main"): Result<String> {
        val o = validateName(owner) ?: return Result.failure(IllegalArgumentException("The GitHub owner is invalid."))
        val r = validateName(repository) ?: return Result.failure(IllegalArgumentException("The GitHub repository is invalid."))
        val b = branch.trim().take(120)
        requireSafeBranch(b).getOrElse { return Result.failure(it) }
        val source = fromBranch.trim().ifBlank { "main" }
        return runCatching {
            val sha = getJson("/repos/" + o + "/" + r + "/git/ref/heads/" + encodePathSegment(source)) {
                it.getJSONObject("object").getString("sha")
            }.getOrThrow()
            postJson(
                "/repos/" + o + "/" + r + "/git/refs",
                JSONObject().put("ref", "refs/heads/" + b).put("sha", sha),
            )
            b
        }
    }

    fun createIssue(owner: String, repository: String, title: String, body: String = ""): Result<String> {
        val o = validateName(owner) ?: return Result.failure(IllegalArgumentException("The GitHub owner is invalid."))
        val r = validateName(repository) ?: return Result.failure(IllegalArgumentException("The GitHub repository is invalid."))
        val cleanTitle = title.trim().take(300)
        require(cleanTitle.isNotBlank()) { "Issue title cannot be empty." }
        return runCatching {
            val response = postJson(
                "/repos/" + o + "/" + r + "/issues",
                JSONObject().put("title", cleanTitle).put("body", body.take(65_000)),
            )
            response.optString("html_url").ifBlank { response.optString("url") }
        }
    }

    fun commentIssue(owner: String, repository: String, issueNumber: Long, body: String): Result<String> {
        val o = validateName(owner) ?: return Result.failure(IllegalArgumentException("The GitHub owner is invalid."))
        val r = validateName(repository) ?: return Result.failure(IllegalArgumentException("The GitHub repository is invalid."))
        require(issueNumber > 0L) { "Issue number must be positive." }
        return runCatching {
            val response = postJson(
                "/repos/" + o + "/" + r + "/issues/" + issueNumber + "/comments",
                JSONObject().put("body", body.take(65_000)),
            )
            response.optString("html_url").ifBlank { response.optString("url") }
        }
    }

    fun createPullRequest(
        owner: String,
        repository: String,
        title: String,
        head: String,
        base: String = "main",
        body: String = "",
    ): Result<String> {
        val o = validateName(owner) ?: return Result.failure(IllegalArgumentException("The GitHub owner is invalid."))
        val r = validateName(repository) ?: return Result.failure(IllegalArgumentException("The GitHub repository is invalid."))
        val cleanTitle = title.trim().take(300)
        require(cleanTitle.isNotBlank()) { "Pull request title cannot be empty." }
        val cleanHead = head.trim().take(250)
        val cleanBase = base.trim().take(250).ifBlank { "main" }
        require(cleanHead.isNotBlank() && cleanBase.isNotBlank()) { "Pull request branches are required." }
        return runCatching {
            val response = postJson(
                "/repos/" + o + "/" + r + "/pulls",
                JSONObject().put("title", cleanTitle).put("head", cleanHead).put("base", cleanBase).put("body", body.take(65_000)),
            )
            response.optString("html_url").ifBlank { response.optString("url") }
        }
    }
    fun listOpenIssues(owner: String, repository: String): GitHubActivityResult =
        listActivity(owner, repository, "/issues")

    fun listOpenPullRequests(owner: String, repository: String): GitHubActivityResult =
        listActivity(owner, repository, "/pulls")

    private fun listActivity(owner: String, repository: String, resource: String): GitHubActivityResult {
        val normalizedOwner = validateName(owner)
            ?: return GitHubActivityResult.Failure("The GitHub owner is invalid.")
        val normalizedRepository = validateName(repository)
            ?: return GitHubActivityResult.Failure("The GitHub repository is invalid.")
        val path = "/repos/" + normalizedOwner + "/" + normalizedRepository + resource + "?state=open&per_page=30&page=1"
        return getJsonArray(path).map { json ->
            buildList(minOf(json.length(), 30)) {
                for (index in 0 until minOf(json.length(), 30)) {
                    val item = json.optJSONObject(index) ?: continue
                    if (resource == "/issues" && item.has("pull_request")) continue
                    val number = item.optLong("number", -1L)
                    val title = item.optString("title").trim().take(500)
                    if (number <= 0L || title.isBlank()) continue
                    add(
                        GitHubActivityItem(
                            number = number,
                            title = title,
                            state = item.optString("state", "open"),
                            author = item.optJSONObject("user")?.optString("login").orEmpty().take(160),
                            createdAt = item.optString("created_at").takeIf(String::isNotBlank)?.take(80),
                            url = item.optString("html_url").takeIf(String::isNotBlank)?.take(2000),
                        ),
                    )
                }
            }
        }.fold(
            onSuccess = { GitHubActivityResult.Success(it) },
            onFailure = { GitHubActivityResult.Failure(safeMessage(it)) },
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

    fun deleteWorkflow(
        owner: String,
        repository: String,
        branch: String,
        workflow: GitHubWorkflow,
    ): Result<String> {
        val normalizedOwner = validateName(owner)
            ?: return Result.failure(IllegalArgumentException("The GitHub owner is invalid."))
        val normalizedRepository = validateName(repository)
            ?: return Result.failure(IllegalArgumentException("The GitHub repository is invalid."))
        val normalizedPath = normalizeRepoPath(workflow.path)
        if (!normalizedPath.startsWith(".github/workflows/") ||
            normalizedPath.contains("..") ||
            normalizedPath.endsWith("/")
        ) {
            return Result.failure(IllegalArgumentException("Only workflow files under .github/workflows/ can be deleted."))
        }
        val targetBranch = branch.trim().ifBlank { "main" }
        val file = when (val result = readFile(normalizedOwner, normalizedRepository, normalizedPath, targetBranch)) {
            is GitHubFileResult.Success -> result
            is GitHubFileResult.Failure -> return Result.failure(IllegalStateException(result.message))
        }
        if (file.path != normalizedPath || file.sha.isNullOrBlank()) {
            return Result.failure(IllegalStateException("GitHub did not return a valid workflow file revision."))
        }
        return when (
            val result = commitChanges(
                normalizedOwner,
                normalizedRepository,
                targetBranch,
                "DevForge: delete workflow " + workflow.name.trim().take(120),
                listOf(GitHubTreeChange(path = file.path, delete = true, blobSha = file.sha)),
            )
        ) {
            is GitHubCommitResult.Success -> Result.success(result.commitSha)
            is GitHubCommitResult.Failure -> Result.failure(IllegalStateException(result.message))
        }
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
            visibility = json.optString("visibility").takeIf(String::isNotBlank)
                ?: if (json.optBoolean("private", false)) "private" else "public",
            defaultBranch = json.optString("default_branch", "main"),
            description = json.optString("description", ""),
            homepage = json.optString("homepage", ""),
            hasIssues = json.optBoolean("has_issues", true),
            hasProjects = json.optBoolean("has_projects", true),
            hasWiki = json.optBoolean("has_wiki", false),
            hasDiscussions = json.optBoolean("has_discussions", false),
            isTemplate = json.optBoolean("is_template", false),
            hasDownloads = json.optBoolean("has_downloads", true),
            allowSquashMerge = json.optBoolean("allow_squash_merge", true),
            allowMergeCommit = json.optBoolean("allow_merge_commit", true),
            allowRebaseMerge = json.optBoolean("allow_rebase_merge", true),
            allowAutoMerge = json.optBoolean("allow_auto_merge", false),
            deleteBranchOnMerge = json.optBoolean("delete_branch_on_merge", false),
            squashMergeTitle = json.optString("squash_merge_commit_title", "PR_TITLE"),
            squashMergeMessage = json.optString("squash_merge_commit_message", "COMMIT_MESSAGES"),
            mergeCommitTitle = json.optString("merge_commit_title", "PR_TITLE"),
            mergeCommitMessage = json.optString("merge_commit_message", "PR_TITLE"),
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
        private const val MAX_REPOSITORY_PAGES = 100
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val API_VERSION = "2026-03-10"
        private const val MAX_COMMIT_FILES = 200
        private const val MAX_COMMIT_MESSAGE = 10_000
        private val SAFE_NAME = Regex("^[A-Za-z0-9_.-]+$")
        private val SHA_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}

data class GitHubContentEntry(
    val path: String,
    val name: String,
    val type: String,
    val sizeBytes: Long?,
    val sha: String?,
    val contentBase64: String?,
    val htmlUrl: String?,
)

data class GitHubTreeChange(
    val path: String,
    val content: String? = null,
    val delete: Boolean = false,
    val blobSha: String? = null,
)

sealed interface GitHubContentsResult {
    data class Success(val entries: List<GitHubContentEntry>) : GitHubContentsResult
    data class Failure(val message: String) : GitHubContentsResult
}

sealed interface GitHubFileResult {
    data class Success(val path: String, val sha: String?, val content: String) : GitHubFileResult
    data class Failure(val message: String) : GitHubFileResult
}

sealed interface GitHubCommitResult {
    data class Success(val commitSha: String, val message: String, val paths: List<String>) : GitHubCommitResult
    data class Failure(val message: String) : GitHubCommitResult
}

sealed interface GitHubBranchesResult {
    data class Success(val branches: List<String>) : GitHubBranchesResult
    data class Failure(val message: String) : GitHubBranchesResult
}

data class GitHubCommitHistoryEntry(
    val sha: String,
    val subject: String,
    val author: String,
    val authoredAt: String?,
    val url: String?,
)

data class GitHubCommitDetails(
    val sha: String,
    val subject: String,
    val fullMessage: String,
    val author: String,
    val authoredAt: String?,
    val parents: List<String>,
    val additions: Int,
    val deletions: Int,
    val changedFiles: List<GitHubCommitFileChange>,
)

data class GitHubCommitFileChange(
    val path: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val previousPath: String?,
)

sealed interface GitHubCommitDetailsResult {
    data class Success(val value: GitHubCommitDetails) : GitHubCommitDetailsResult
    data class Failure(val message: String) : GitHubCommitDetailsResult
}

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
