package com.mrredhood.devforge.core.agent

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitRemoteResult
import com.mrredhood.devforge.core.git.GitRemoteTransportService
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.WorkspaceDao
import com.mrredhood.devforge.core.editor.ContentHasher
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import com.mrredhood.devforge.core.workspace.WorkspaceSearch
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceRemote
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceUris
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.github.GitHubTreeChange
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Registers bounded SAF workspace tools. There is deliberately no shell/exec tool here. */
class WorkspaceAgentToolProvider(
    private val context: Context,
    private val resolver: ContentResolver,
    private val workspaceDao: WorkspaceDao,
    private val gitRemoteService: GitRemoteTransportService,
) {
    private val gitRepositoryService = GitRepositoryService(resolver)
    private val gitSyncMutex = kotlinx.coroutines.sync.Mutex()
    private val githubStore = GitHubWorkspaceStore(context)
    private val githubGateway = GitHubRepositoryGateway(CredentialSecurityStore(context))
    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry = registry
        .register(ReadFileTool())
        .register(ListFilesTool())
        .register(SearchWorkspaceTool())
        .register(FindFilesTool())
        .register(FileInfoTool())
        .register(CountLinesTool())
        .register(HashFileTool())
        .register(SearchContentTool())
        .register(DirectoryTreeTool())
        .register(PatchFileTool())
        .register(WriteFileTool())
        .register(CreateFileTool())
        .register(CreateFolderTool())
        .register(DeletePathTool())

    private abstract inner class WorkspaceTool : AgentTool {
        protected suspend fun root(context: AgentToolContext): Uri {
            val workspace = workspaceDao.findById(context.workspaceId)
                ?: throw IllegalArgumentException("Workspace '${context.workspaceId}' was not found.")
            return documentUri(Uri.parse(workspace.treeUri))
        }

        protected suspend fun scopedPath(
            context: AgentToolContext,
            rawPath: String,
            allowEmpty: Boolean = false,
        ): String {
            val workspace = workspaceDao.findById(context.workspaceId)
                ?: throw IllegalArgumentException("Workspace '${context.workspaceId}' was not found.")
            val isRemote = remoteWorkspace(context) != null
            val toolPath = normalizeToolPath(rawPath, isRemote)
            val normalized = WorkspacePathScope.normalize(toolPath, allowEmpty)
            if (normalized.isBlank() && !allowEmpty) {
                throw IllegalArgumentException("A file or folder path is required; DevForge selects the exact path automatically from the workspace.")
            }
            val directPathExists = if (isRemote) false else access.exists(root(context), normalized)
            return AgentWorkspacePath.canonicalize(
                rawPath = normalized,
                workspaceName = workspace.name,
                scope = context.pathScope,
                allowEmpty = allowEmpty,
                directPathExists = directPathExists,
            )
        }

        protected val access = WorkspaceAgentFileAccess(resolver)

        private fun normalizeToolPath(rawPath: String, remote: Boolean): String {
            var value = rawPath.trim()
            if (remote) {
                when {
                    value.startsWith("github://", ignoreCase = true) ->
                        value = value.substring(9)
                    value.startsWith("devforge://", ignoreCase = true) ->
                        value = runCatching { GitHubWorkspaceUris.remotePath(Uri.parse(value)) }.getOrDefault(value)
                }
            }
            return value
        }

        protected suspend fun syncGitHub(context: AgentToolContext, summary: String): String? {
            val remote = remoteWorkspace(context)
            if (remote != null) {
                return "GitHub-backed workspace changes are committed directly by the mutation tool."
            }
            val workspace = workspaceDao.findById(context.workspaceId) ?: return null
            val rootUri = Uri.parse(workspace.treeUri)
            return when (val detected = gitRepositoryService.detect(rootUri)) {
                is GitDetectionState.Detected -> {
                    val validation = gitRemoteService.validateConfigured(detected.repository.remoteUrl)
                    if (validation.owner == null || validation.repository == null) null
                    else when (val result = gitSyncMutex.withLock {
                        if (summary.startsWith("create folder ")) {
                            val folderPath = summary.removePrefix("create folder ").trim()
                            access.ensureGitKeepIfEmpty(rootUri, folderPath)
                        }
                        gitRemoteService.autoSyncChanges(
                            detected.repository,
                            "DevForge agent: " + summary.take(160),
                        )
                    }) {
                        is GitRemoteResult.Success -> result.message
                        is GitRemoteResult.Failure -> "Local change saved. GitHub synchronization failed: " + result.message
                    }
                }
                else -> null
            }
        }

        protected suspend fun remoteWorkspace(context: AgentToolContext): GitHubWorkspaceRemote? {
            val workspace = workspaceDao.findById(context.workspaceId) ?: return null
            return githubStore.get(workspace.id)
        }

        protected suspend fun commitRemoteChanges(
            remote: GitHubWorkspaceRemote,
            changes: List<GitHubTreeChange>,
            summary: String,
        ): String {
            val result = githubGateway.commitChanges(
                owner = remote.owner,
                repository = remote.repository,
                branch = remote.branch,
                message = "DevForge agent: " + summary.take(160),
                changes = changes,
            )
            return when (result) {
                is com.mrredhood.devforge.core.github.GitHubCommitResult.Success ->
                    "GitHub commit " + result.commitSha.take(10) + " pushed to " + remote.branch + "."
                is com.mrredhood.devforge.core.github.GitHubCommitResult.Failure ->
                    throw IllegalStateException("GitHub synchronization failed: " + result.message)
            }
        }

        protected suspend fun remoteRead(context: AgentToolContext, path: String): String {
            val remote = remoteWorkspace(context) ?: error("No GitHub-backed workspace is active.")
            val content = when (val result = githubGateway.readFile(remote.owner, remote.repository, path, remote.branch)) {
                is com.mrredhood.devforge.core.github.GitHubFileResult.Success -> result.content
                is com.mrredhood.devforge.core.github.GitHubFileResult.Failure -> error(result.message)
            }
            require(content.toByteArray(Charsets.UTF_8).size <= MAX_READ_BYTES) {
                "File exceeds the agent read limit of 128 KiB."
            }
            return content
        }

        protected suspend fun remoteList(context: AgentToolContext, path: String, limit: Int): List<AgentWorkspaceEntry> {
            val remote = remoteWorkspace(context) ?: return emptyList()
            return when (val result = githubGateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Success ->
                    result.entries.take(limit).map { AgentWorkspaceEntry(it.name, it.type == "dir", it.sizeBytes) }
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Failure -> error(result.message)
            }
        }

        protected suspend fun remoteRecursiveFiles(remote: GitHubWorkspaceRemote, prefix: String): List<String> {
            val output = mutableListOf<String>()
            suspend fun visit(path: String) {
                if (output.size >= 600) return
                when (val result = githubGateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                    is com.mrredhood.devforge.core.github.GitHubContentsResult.Success -> {
                        for (item in result.entries) {
                            if (item.type == "dir") visit(item.path) else output += item.path
                            if (output.size >= 600) return
                        }
                    }
                    is com.mrredhood.devforge.core.github.GitHubContentsResult.Failure -> error(result.message)
                }
            }
            visit(prefix)
            return output
        }

        private fun documentUri(documentOrTree: Uri): Uri {
            val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(documentOrTree) }.getOrNull()
            return treeDocumentId?.let { DocumentsContract.buildDocumentUriUsingTree(documentOrTree, it) }
                ?: documentOrTree
        }
    }

    private inner class ReadFileTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.READ_FILE,
            "Read a bounded text file from the selected workspace; optional 1-based startLine/endLine return only the requested range.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val path = scopedPath(context, args.optString("path").trim())
            val startLine = args.optInt("startLine", 0).takeIf { it > 0 }
            val endLine = args.optInt("endLine", 0).takeIf { it > 0 }
            require((startLine == null || endLine == null || endLine >= startLine)) {
                "endLine must be greater than or equal to startLine."
            }
            val rawContent = if (remoteWorkspace(context) != null) remoteRead(context, path) else access.readText(root(context), path)
            val selected = selectLineRange(rawContent, startLine, endLine)
            AgentToolResult.Success(
                summary = if (selected.wasRanged) {
                    "Read $path lines " + selected.startLine + "-" + selected.endLine + "."
                } else {
                    "Read $path."
                },
                output = JSONObject()
                    .put("path", path)
                    .put("content", selected.content)
                    .put("startLine", selected.startLine)
                    .put("endLine", selected.endLine)
                    .put("totalLines", selected.totalLines)
                    .put("ranged", selected.wasRanged)
                    .toString(),
                affectedPaths = listOf(path),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to read file.")
        }
    }

    private inner class ListFilesTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.LIST_FILES,
            "List bounded direct children of a workspace directory.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val path = scopedPath(context, args.optString("path", "").trim(), allowEmpty = true)
            val limit = args.optInt("limit", 100).coerceIn(1, MAX_LIST_ENTRIES)
            val remote = remoteWorkspace(context)
            val listing = if (remote != null) {
                val entries = remoteList(context, path, limit)
                WorkspaceAgentFileAccess.ListResult(
                    requestedPath = path,
                    resolvedPath = path,
                    recovered = false,
                    entries = entries,
                )
            } else {
                access.list(root(context), path, limit)
            }
            val result = JSONArray()
            listing.entries.forEach { entry ->
                result.put(
                    JSONObject()
                        .put("name", entry.name)
                        .put("directory", entry.isDirectory)
                        .put("sizeBytes", entry.sizeBytes),
                )
            }
            AgentToolResult.Success(
                summary = if (listing.recovered) {
                    "Recovered workspace listing for ${listing.requestedPath}: ${listing.recoveryMessage.orEmpty()}"
                } else {
                    "Listed ${listing.entries.size} entries${if (path.isBlank()) "" else " in $path"}."
                },
                output = JSONObject()
                    .put("requestedPath", listing.requestedPath)
                    .put("resolvedPath", listing.resolvedPath)
                    .put("recovered", listing.recovered)
                    .put("recoveryMessage", listing.recoveryMessage ?: JSONObject.NULL)
                    .put("entries", result)
                    .toString(),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to list workspace files.")
        }
    }

    private inner class SearchWorkspaceTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.SEARCH_WORKSPACE,
            "Search workspace names inside the authorized path scope.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val query = args.optString("query").trim()
            require(query.isNotBlank()) { "Search query cannot be empty." }
            val limit = args.optInt("limit", 30).coerceIn(1, MAX_SEARCH_RESULTS)
            val remote = remoteWorkspace(context)
            if (remote != null) {
                val output = JSONArray()
                suspend fun visit(path: String) {
                    if (output.length() >= limit) return
                    when (val result = githubGateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                        is com.mrredhood.devforge.core.github.GitHubContentsResult.Success -> {
                            for (item in result.entries) {
                                if (item.name.contains(query, true) || item.path.contains(query, true)) {
                                    output.put(
                                        JSONObject()
                                            .put("scope", "")
                                            .put("path", item.path)
                                            .put("name", item.name)
                                            .put("directory", item.type == "dir")
                                            .put("sizeBytes", item.sizeBytes)
                                            .put("uri", "github://" + item.path),
                                    )
                                }
                                if (item.type == "dir") visit(item.path)
                                if (output.length() >= limit) return
                            }
                        }
                        is com.mrredhood.devforge.core.github.GitHubContentsResult.Failure -> error(result.message)
                    }
                }
                visit("")
                return AgentToolResult.Success(
                    summary = "Found " + output.length() + " workspace matches for '" + query + "'.",
                    output = JSONObject().put("query", query).put("results", output).toString(),
                )
            }

            val workspaceRoot = root(context)
            val output = JSONArray()
            val prefixes = context.pathScope.canonicalPrefixes().ifEmpty { listOf("") }
            val search = WorkspaceSearch(resolver)
            prefixes.forEach { prefix ->
                if (output.length() >= limit) return@forEach
                val scopedRoot = access.resolveDirectory(workspaceRoot, prefix)
                val remaining = (limit - output.length()).coerceAtLeast(1)
                search.search(scopedRoot, query, remaining).forEach { item ->
                    if (output.length() < limit) {
                        output.put(
                            JSONObject()
                                .put("scope", prefix)
                                .put("path", if (prefix.isBlank()) item.name else prefix + "/" + item.name)
                                .put("name", item.name)
                                .put("directory", item.isDirectory)
                                .put("sizeBytes", item.sizeBytes)
                                .put("uri", item.uri.toString()),
                        )
                    }
                }
            }
            AgentToolResult.Success(
                summary = "Found ${output.length()} workspace matches for '$query'.",
                output = JSONObject().put("query", query).put("results", output).toString(),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Workspace search failed.")
        }
    }

    private inner class PatchFileTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.PATCH_FILE,
            "Apply one bounded structured text patch after an exact pre-image check.",
            Capability.EDIT_FILES,
            RiskLevel.R2,
            sideEffecting = true,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val patch = AgentFilePatchCodec.decode(request.argumentsJson)
            val path = scopedPath(context, patch.path)
            val remote = remoteWorkspace(context)
            val rootUri = if (remote == null) root(context) else null
            val before = if (remote != null) {
                runCatching { remoteRead(context, path) }.getOrElse { "" }
            } else {
                runCatchingCancellable { access.readText(rootUri!!, path) }.getOrElse {
                    require(it.message?.contains("does not exist", true) == true) { it.message ?: "Unable to read patch target." }
                    ""
                }
            }
            val currentHash = ContentHasher.sha256(before)
            patch.expectedContentHash?.let { expected ->
                require(expected.equals(currentHash, ignoreCase = true)) {
                    "Patch precondition failed for $path; the file changed after the patch was prepared."
                }
            }
            require(before != patch.content) { "Patch produces no content change for $path." }
            val sync = if (remote != null) {
                val github = remote ?: error("GitHub workspace is unavailable.")
                commitRemoteChanges(
                    github,
                    listOf(GitHubTreeChange(path, content = patch.content)),
                    patch.summary.ifBlank { "update " + path },
                )
            } else {
                access.writeText(rootUri!!, path, patch.content)
                syncGitHub(context, "update " + path)
            }
            AgentToolResult.Success(
                summary = (patch.summary.ifBlank { "Applied patch to $path." } +
                    (sync?.let { " $it" } ?: "")).take(500),
                output = JSONObject()
                    .put("path", path)
                    .put("beforeHash", currentHash)
                    .put("afterHash", ContentHasher.sha256(patch.content))
                    .put("bytes", patch.content.toByteArray(Charsets.UTF_8).size)
                    .toString(),
                affectedPaths = listOf(path),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to apply patch.")
        }

        override suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> =
            listOf(scopedPath(context, AgentFilePatchCodec.decode(request.argumentsJson).path))

        override suspend fun preconditionHash(context: AgentToolContext, request: AgentToolRequest): String? {
            val patch = AgentFilePatchCodec.decode(request.argumentsJson)
            val path = scopedPath(context, patch.path)
            val remote = remoteWorkspace(context)
            val rootUri = if (remote == null) root(context) else null
            val before = if (remote != null) {
                runCatching { remoteRead(context, path) }.getOrElse { "" }
            } else {
                runCatchingCancellable { access.readText(rootUri!!, path) }.getOrElse {
                    require(it.message?.contains("does not exist", true) == true) { it.message ?: "Unable to read patch target." }
                    ""
                }
            }
            val currentHash = ContentHasher.sha256(before)
            patch.expectedContentHash?.let { expected ->
                require(expected.equals(currentHash, ignoreCase = true)) {
                    "Patch precondition failed for $path."
                }
            }
            return currentHash
        }
    }

    private inner class CreateFileTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.CREATE_FILE,
            "Create a new bounded UTF-8 text file inside the selected workspace.",
            Capability.EDIT_FILES,
            RiskLevel.R2,
            sideEffecting = true,
        )

        override suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> =
            listOf(scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim()))

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val path = scopedPath(context, args.optString("path").trim())
            val content = args.optString("content", "")
            val sync = if (remoteWorkspace(context) != null) {
                val remote = remoteWorkspace(context) ?: error("GitHub workspace is unavailable.")
                commitRemoteChanges(
                    remote,
                    listOf(GitHubTreeChange(path, content = content)),
                    "create " + path,
                )
            } else {
                access.createText(root(context), path, content)
                syncGitHub(context, "create " + path)
            }
            AgentToolResult.Success(
                summary = ("Created $path." + (sync?.let { " $it" } ?: "")).take(500),
                output = JSONObject().put("path", path).put("bytes", content.toByteArray(Charsets.UTF_8).size).toString(),
                affectedPaths = listOf(path),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to create file.")
        }
    }

    private inner class CreateFolderTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.CREATE_FOLDER,
            "Create one new folder inside the selected workspace.",
            Capability.EDIT_FILES,
            RiskLevel.R2,
            sideEffecting = true,
        )

        override suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> =
            listOf(scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim()))

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val path = scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim())
            val sync = if (remoteWorkspace(context) != null) {
                val remote = remoteWorkspace(context) ?: error("GitHub workspace is unavailable.")
                val keep = if (path.endsWith("/")) path + ".gitkeep" else path + "/.gitkeep"
                commitRemoteChanges(
                    remote,
                    listOf(GitHubTreeChange(keep, content = "")),
                    "create folder " + path,
                )
            } else {
                access.createDirectory(root(context), path)
                syncGitHub(context, "create folder " + path)
            }
            AgentToolResult.Success(
                summary = ("Created folder $path." + (sync?.let { " $it" } ?: "")).take(500),
                output = JSONObject().put("path", path).toString(),
                affectedPaths = listOf(path),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to create folder.")
        }
    }

    private inner class DeletePathTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.DELETE_PATH,
            "Delete one workspace file or folder.",
            Capability.DELETE_FILES,
            RiskLevel.R3,
            sideEffecting = true,
        )

        override suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> =
            listOf(scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim()))

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val path = scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim())
            require(path != ".git" && !path.startsWith(".git/")) { "The .git directory is protected." }
            val sync = if (remoteWorkspace(context) != null) {
                val remote = remoteWorkspace(context) ?: error("GitHub workspace is unavailable.")
                val name = path.substringAfterLast('/')
                val parent = path.substringBeforeLast('/', "")
                val entry = remoteList(context, parent, 100).firstOrNull { it.name == name }
                    ?: error("Workspace path does not exist: " + path)
                val changes = if (entry.isDirectory) {
                    remoteRecursiveFiles(remote, path).map { GitHubTreeChange(it, delete = true) }
                } else {
                    listOf(GitHubTreeChange(path, delete = true))
                }
                require(changes.isNotEmpty()) {
                    "The GitHub folder is already empty or is not tracked by Git: " + path
                }
                commitRemoteChanges(
                    remote,
                    changes,
                    "delete " + path,
                )
            } else {
                access.delete(root(context), path)
                syncGitHub(context, "delete " + path)
            }
            AgentToolResult.Success(
                summary = ("Deleted $path." + (sync?.let { " $it" } ?: "")).take(500),
                output = JSONObject().put("path", path).toString(),
                affectedPaths = listOf(path),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to delete path.")
        }
    }

    private inner class WriteFileTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.WRITE_FILE,
            "Write one bounded UTF-8 text file inside the selected workspace.",
            Capability.EDIT_FILES,
            RiskLevel.R2,
            sideEffecting = true,
        )

        override suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> =
            listOf(scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim()))

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val path = scopedPath(context, args.optString("path").trim())
            val content = args.optString("content", "")
            val sync = if (remoteWorkspace(context) != null) {
                val remote = remoteWorkspace(context) ?: error("GitHub workspace is unavailable.")
                commitRemoteChanges(
                    remote,
                    listOf(GitHubTreeChange(path, content = content)),
                    "write " + path,
                )
            } else {
                access.writeText(root(context), path, content)
                syncGitHub(context, "write " + path)
            }
            AgentToolResult.Success(
                summary = ("Wrote $path." + (sync?.let { " $it" } ?: "")).take(500),
                output = JSONObject()
                    .put("path", path)
                    .put("bytes", content.toByteArray(Charsets.UTF_8).size)
                    .toString(),
                affectedPaths = listOf(path),
            )
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to write file.")
        }
    }


    private inner class FindFilesTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.FIND_FILES,
            "Recursively find bounded workspace files or folders matching a query.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val query = args.optString("query").trim()
            require(query.isNotBlank()) { "Find query cannot be empty." }
            val start = scopedPath(context, args.optString("path", "").trim(), allowEmpty = true)
            val limit = args.optInt("limit", 40).coerceIn(1, 100)
            val results = JSONArray()
            val visited = intArrayOf(0)

            suspend fun visit(path: String, depth: Int) {
                if (results.length() >= limit || depth > MAX_TREE_DEPTH || visited[0] >= MAX_TREE_NODES) return
                visited[0]++
                val remote = remoteWorkspace(context)
                val entries = if (remote != null) remoteList(context, path, 100)
                else access.list(root(context), path, 100).entries
                for (entry in entries) {
                    if (results.length() >= limit) break
                    val childPath = if (path.isBlank()) entry.name else path + "/" + entry.name
                    if (entry.name.contains(query, true) || childPath.contains(query, true)) {
                        results.put(
                            JSONObject()
                                .put("path", childPath)
                                .put("name", entry.name)
                                .put("directory", entry.isDirectory)
                                .put("sizeBytes", entry.sizeBytes),
                        )
                    }
                    if (entry.isDirectory) visit(childPath, depth + 1)
                }
            }
            visit(start, 0)
            AgentToolResult.Success(
                summary = "Found " + results.length() + " matches for '" + query + "'.",
                output = JSONObject().put("query", query).put("results", results).toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to find workspace entries.")
        }
    }

    private inner class FileInfoTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.FILE_INFO,
            "Inspect bounded type and metadata for one workspace path.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val path = scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim())
            val name = path.substringAfterLast('/')
            val parent = path.substringBeforeLast('/', "")
            val remote = remoteWorkspace(context)
            val entry = if (remote != null) {
                remoteList(context, parent, 100).firstOrNull { it.name == name }
            } else {
                access.list(root(context), parent, 100).entries.firstOrNull { it.name == name }
            } ?: throw IllegalArgumentException("Workspace path does not exist: " + path)
            AgentToolResult.Success(
                summary = "Inspected " + path + ".",
                output = JSONObject()
                    .put("path", path)
                    .put("name", entry.name)
                    .put("directory", entry.isDirectory)
                    .put("sizeBytes", entry.sizeBytes ?: JSONObject.NULL)
                    .toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to inspect workspace path.")
        }
    }

    private inner class CountLinesTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.COUNT_LINES,
            "Count lines, characters and UTF-8 bytes in a bounded text file.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val path = scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim())
            val content = if (remoteWorkspace(context) != null) remoteRead(context, path) else access.readText(root(context), path)
            val bytes = content.toByteArray(Charsets.UTF_8).size
            val lines = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
            AgentToolResult.Success(
                summary = "Counted " + path + ".",
                output = JSONObject()
                    .put("path", path)
                    .put("lines", lines)
                    .put("characters", content.length)
                    .put("bytes", bytes)
                    .toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to count file.")
        }
    }

    private inner class HashFileTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.HASH_FILE,
            "Calculate SHA-256 for a bounded UTF-8 workspace text file.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val path = scopedPath(context, JSONObject(request.argumentsJson).optString("path").trim())
            val content = if (remoteWorkspace(context) != null) remoteRead(context, path) else access.readText(root(context), path)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            AgentToolResult.Success(
                summary = "Hashed " + path + ".",
                output = JSONObject().put("path", path).put("algorithm", "SHA-256").put("hash", digest).toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to hash file.")
        }
    }

    private inner class SearchContentTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.SEARCH_CONTENT,
            "Search bounded text content inside workspace files and return matching lines.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val query = args.optString("query").trim()
            require(query.isNotBlank()) { "Content search query cannot be empty." }
            val start = scopedPath(context, args.optString("path", "").trim(), allowEmpty = true)
            val limit = args.optInt("limit", 30).coerceIn(1, 50)
            val results = JSONArray()
            val visited = intArrayOf(0)

            suspend fun visit(path: String, depth: Int) {
                if (results.length() >= limit || depth > MAX_TREE_DEPTH || visited[0] >= MAX_TREE_NODES) return
                visited[0]++
                val remote = remoteWorkspace(context)
                val entries = if (remote != null) remoteList(context, path, 100)
                else access.list(root(context), path, 100).entries
                for (entry in entries) {
                    if (results.length() >= limit) break
                    val childPath = if (path.isBlank()) entry.name else path + "/" + entry.name
                    if (entry.isDirectory) {
                        visit(childPath, depth + 1)
                        continue
                    }
                    val text = runCatching {
                        if (remote != null) remoteRead(context, childPath) else access.readText(root(context), childPath)
                    }.getOrNull() ?: continue
                    val lines = text.lines()
                    lines.forEachIndexed { index, line ->
                        if (results.length() >= limit) return@forEachIndexed
                        if (line.contains(query, true)) {
                            results.put(
                                JSONObject()
                                    .put("path", childPath)
                                    .put("line", index + 1)
                                    .put("text", line.take(500)),
                            )
                        }
                    }
                }
            }
            visit(start, 0)
            AgentToolResult.Success(
                summary = "Found " + results.length() + " content matches for '" + query + "'.",
                output = JSONObject().put("query", query).put("results", results).toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to search workspace content.")
        }
    }

    private inner class DirectoryTreeTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.DIRECTORY_TREE,
            "Build a bounded directory tree for the selected workspace path.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val start = scopedPath(context, args.optString("path", "").trim(), allowEmpty = true)
            val depthLimit = args.optInt("depth", 4).coerceIn(1, MAX_TREE_DEPTH)
            val nodeLimit = args.optInt("limit", 120).coerceIn(1, MAX_TREE_NODES)
            val lines = mutableListOf<String>()
            val visited = intArrayOf(0)

            suspend fun visit(path: String, prefix: String, depth: Int) {
                if (depth > depthLimit || visited[0] >= nodeLimit) return
                visited[0]++
                val remote = remoteWorkspace(context)
                val entries = (if (remote != null) remoteList(context, path, 100)
                    else access.list(root(context), path, 100).entries)
                    .sortedBy { it.name.lowercase() }
                entries.forEachIndexed { index, entry ->
                    if (visited[0] >= nodeLimit) return@forEachIndexed
                    val last = index == entries.lastIndex
                    val marker = if (last) "└─ " else "├─ "
                    val childPath = if (path.isBlank()) entry.name else path + "/" + entry.name
                    lines += prefix + marker + entry.name
                    if (entry.isDirectory) {
                        visit(childPath, prefix + if (last) "   " else "│  ", depth + 1)
                    }
                }
            }
            visit(start, "", 0)
            AgentToolResult.Success(
                summary = "Built a workspace tree with " + lines.size + " visible entries.",
                output = JSONObject()
                    .put("path", start)
                    .put("depth", depthLimit)
                    .put("entries", JSONArray(lines.take(nodeLimit)))
                    .toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to build directory tree.")
        }
    }

    companion object {
        private const val MAX_LIST_ENTRIES = 100
        private const val MAX_SEARCH_RESULTS = 50
        private const val MAX_TREE_DEPTH = 8
        private const val MAX_TREE_NODES = 300
    }
}

private data class AgentWorkspaceEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

internal object AgentWorkspacePath {
    fun canonicalize(
        rawPath: String,
        workspaceName: String,
        scope: WorkspacePathScope,
        allowEmpty: Boolean = false,
        directPathExists: Boolean = false,
    ): String {
        var normalized = WorkspacePathScope.normalize(rawPath, allowEmpty)
        if (!directPathExists) {
            normalized = removeAccidentalTextSuffix(normalized)
        }
        val displayName = workspaceName.trim().trim('/','\\')
        val displayPath = runCatching {
            WorkspacePathScope.normalize(displayName, allowEmpty = true)
        }.getOrDefault("")
        val first = normalized.substringBefore('/')
        val stripped = when {
            directPathExists -> normalized
            displayPath.isNotEmpty() && (
                normalized.equals(displayPath, ignoreCase = true) ||
                    normalized.startsWith("$displayPath/", ignoreCase = true)
            ) -> normalized.substring(displayPath.length).trimStart('/')
            displayName.isNotEmpty() && first.equals(displayName, ignoreCase = true) ->
                normalized.substringAfter('/', missingDelimiterValue = "")
            else -> normalized
        }
        val canonical = WorkspacePathScope.normalize(stripped, allowEmpty)
        return scope.requireAllowed(canonical)
    }

    private fun removeAccidentalTextSuffix(path: String): String {
        if (!path.endsWith(".txt", ignoreCase = true)) return path
        val stem = path.dropLast(4)
        val extension = stem.substringAfterLast('.', "")
        return if (extension.lowercase() in KNOWN_EXTENSIONS) stem else path
    }

    private val KNOWN_EXTENSIONS = setOf(
        "py", "pyw", "js", "jsx", "ts", "tsx", "mjs", "cjs",
        "kt", "kts", "java", "go", "rs", "c", "h", "cc", "cpp", "hpp",
        "cs", "swift", "dart", "php", "rb", "lua", "sh", "bash",
        "html", "htm", "css", "scss", "less", "json", "xml", "yaml", "yml",
        "toml", "ini", "gradle", "properties", "sql", "md", "markdown",
        "vue", "svelte", "astro", "r", "scala", "ex", "exs",
    )
}

private class WorkspaceAgentFileAccess(private val resolver: ContentResolver) {
    private val tree = WorkspaceFileTree(resolver)

    data class ListResult(
        val requestedPath: String,
        val resolvedPath: String,
        val recovered: Boolean,
        val entries: List<AgentWorkspaceEntry>,
        val recoveryMessage: String? = null,
    )

    suspend fun list(root: Uri, path: String, limit: Int): ListResult = withContext(Dispatchers.IO) {
        val normalized = WorkspacePathScope.normalize(path, allowEmpty = true)
        val direct = runCatching { resolve(root, normalized) }
        val target = direct.getOrElse { error ->
            val parentPath = normalized.substringBeforeLast('/', missingDelimiterValue = "")
            if (parentPath == normalized) {
                return@getOrElse root
            }
            resolve(root, parentPath)
        }
        if (isDirectory(target)) {
            val recovered = !direct.isSuccess
            return@withContext ListResult(
                requestedPath = normalized,
                resolvedPath = if (recovered) normalized.substringBeforeLast('/', missingDelimiterValue = "") else normalized,
                recovered = recovered,
                entries = tree.list(target, limit).map { AgentWorkspaceEntry(it.name, it.isDirectory, it.sizeBytes) },
                recoveryMessage = if (recovered) "Requested directory was not found; listed its nearest existing parent directory." else null,
            )
        }

        val parentPath = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        val parent = resolve(root, parentPath)
        ListResult(
            requestedPath = normalized,
            resolvedPath = parentPath,
            recovered = true,
            entries = tree.list(parent, limit).map { AgentWorkspaceEntry(it.name, it.isDirectory, it.sizeBytes) },
            recoveryMessage = "Requested path is a file; listed its parent directory instead.",
        )
    }

    fun exists(root: Uri, path: String): Boolean =
        runCatching { resolve(root, WorkspacePathScope.normalize(path, allowEmpty = true)); true }
            .getOrDefault(false)

    suspend fun ensureGitKeepIfEmpty(root: Uri, path: String) = withContext(Dispatchers.IO) {
        val directory = resolve(root, WorkspacePathScope.normalize(path))
        require(isDirectory(directory)) { "Folder path is not a directory: $path" }
        if (tree.list(directory, 2).isEmpty()) {
            DocumentsContract.createDocument(
                resolver,
                documentParentUri(directory),
                "text/plain",
                ".gitkeep",
            ) ?: throw IOException("Unable to preserve empty Git folder: $path")
        }
    }

    suspend fun readText(root: Uri, path: String): String = withContext(Dispatchers.IO) {
        val file = resolve(root, path)
        require(!isDirectory(file)) { "Cannot read a directory as a file: $path" }
        val bytes = readBounded(file, MAX_READ_BYTES)
        require(!bytes.contains(0.toByte())) { "Binary files are not readable through the agent text tool." }
        bytes.toString(Charsets.UTF_8)
    }

    suspend fun createText(root: Uri, path: String, content: String) = withContext(Dispatchers.IO) {
        val normalized = WorkspacePathScope.normalize(path)
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_WRITE_BYTES) { "Agent file writes are limited to 128 KiB." }
        val parts = normalized.split('/')
        val name = parts.last()
        val parent = resolve(root, parts.dropLast(1).joinToString("/"))
        require(isDirectory(parent)) { "Parent path is not a directory." }
        require(findChild(parent, name) == null) { "File already exists: $normalized" }
        val target = DocumentsContract.createDocument(
            resolver,
            documentParentUri(parent),
            "application/octet-stream",
            name,
        ) ?: throw IOException("Unable to create $normalized")
        val exactTarget = exactDisplayName(target, name)
        resolver.openOutputStream(exactTarget, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Unable to write $normalized")
    }

    suspend fun createDirectory(root: Uri, path: String) = withContext(Dispatchers.IO) {
        val normalized = WorkspacePathScope.normalize(path)
        val parts = normalized.split('/')
        val name = parts.last()
        val parent = resolve(root, parts.dropLast(1).joinToString("/"))
        require(isDirectory(parent)) { "Parent path is not a directory." }
        require(findChild(parent, name) == null) { "Path already exists: $normalized" }
        DocumentsContract.createDocument(
            resolver,
            documentParentUri(parent),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: throw IOException("Unable to create folder $normalized")
    }

    suspend fun delete(root: Uri, path: String) = withContext(Dispatchers.IO) {
        val normalized = WorkspacePathScope.normalize(path)
        require(normalized != ".git" && !normalized.startsWith(".git/")) { "The .git directory is protected." }
        val target = resolve(root, normalized)
        require(target != root) { "The workspace root cannot be deleted." }
        deleteRecursively(target, normalized)
    }

    private fun deleteRecursively(target: Uri, path: String) {
        if (isDirectory(target)) {
            val children = tree.list(target, MAX_DIRECTORY_ENTRIES)
            require(children.size < MAX_DIRECTORY_ENTRIES) {
                "Refusing to delete '$path' because the folder has too many direct children."
            }
            children.forEach { child ->
                val childPath = if (path.isBlank()) child.name else "$path/${child.name}"
                require(child.name != ".git" && childPath != ".git" && !childPath.startsWith(".git/")) {
                    "The .git directory is protected."
                }
                deleteRecursively(child.uri, childPath)
            }
        }
        require(DocumentsContract.deleteDocument(resolver, target)) {
            "Unable to delete $path"
        }
    }

    suspend fun writeText(root: Uri, path: String, content: String) = withContext(Dispatchers.IO) {
        val normalized = WorkspacePathScope.normalize(path)
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_WRITE_BYTES) { "Agent file writes are limited to 128 KiB." }
        val parts = normalized.split('/')
        val name = parts.last()
        val parentPath = parts.dropLast(1).joinToString("/")
        val parent = resolve(root, parentPath)
        require(isDirectory(parent)) { "Parent path is not a directory: $parentPath" }
        val target = findChild(parent, name)
            ?: DocumentsContract.createDocument(resolver, documentParentUri(parent), "application/octet-stream", name)
            ?: throw IOException("Unable to create $normalized")
        val exactTarget = exactDisplayName(target, name)
        require(!isDirectory(exactTarget)) { "Cannot overwrite a directory: $normalized" }
        resolver.openOutputStream(exactTarget, "wt")?.use { output -> output.write(bytes) }
            ?: throw IOException("Unable to open $normalized for writing.")
    }

    suspend fun resolveDirectory(root: Uri, path: String): Uri = withContext(Dispatchers.IO) {
        val normalized = WorkspacePathScope.normalize(path, allowEmpty = true)
        val target = resolve(root, normalized)
        require(isDirectory(target)) { "Workspace path is not a directory: $path" }
        target
    }

    private fun resolve(root: Uri, path: String): Uri {
        val normalized = WorkspacePathScope.normalize(path, allowEmpty = true)
        if (normalized.isEmpty()) return root
        var current = root
        normalized.split('/').forEach { segment ->
            current = findChild(current, segment)
                ?: throw IllegalArgumentException("Workspace path does not exist: $normalized")
        }
        return current
    }

    private fun documentUri(uri: Uri): Uri {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return treeDocumentId?.let { DocumentsContract.buildDocumentUriUsingTree(uri, it) } ?: uri
    }

    private fun documentParentUri(parent: Uri): Uri {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(parent) }.getOrNull()
        return treeDocumentId?.let { DocumentsContract.buildDocumentUriUsingTree(parent, it) } ?: parent
    }

    private fun exactDisplayName(uri: Uri, desiredName: String): Uri {
        val actual = runCatching {
            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return if (actual == desiredName) {
            uri
        } else {
            DocumentsContract.renameDocument(resolver, uri, desiredName)
                ?: uri
        }
    }

    private fun findChild(parent: Uri, name: String): Uri? {
        val entries = tree.list(parent, MAX_DIRECTORY_ENTRIES)
        return entries.firstOrNull { it.name == name }?.uri
            ?: entries.filter { it.name.equals(name, ignoreCase = true) }.singleOrNull()?.uri
    }

    private fun isDirectory(uri: Uri): Boolean =
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            cursor.moveToFirst() && index >= 0 && cursor.getString(index) == DocumentsContract.Document.MIME_TYPE_DIR
        } ?: false

    private fun readBounded(uri: Uri, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (output.size() <= maxBytes) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > maxBytes) {
                    throw IllegalArgumentException("File exceeds the agent read limit of 128 KiB.")
                }
                output.write(buffer, 0, read)
            }
        } ?: throw IOException("Unable to open file for reading.")
        return output.toByteArray()
    }

    companion object {
        private const val MAX_READ_BYTES = 128 * 1024
        private const val MAX_WRITE_BYTES = 128 * 1024
        private const val MAX_DIRECTORY_ENTRIES = 256
    }
}


private data class SelectedLineRange(
    val content: String,
    val startLine: Int,
    val endLine: Int,
    val totalLines: Int,
    val wasRanged: Boolean,
)

private fun selectLineRange(
    content: String,
    requestedStart: Int?,
    requestedEnd: Int?,
): SelectedLineRange {
    val lines = content.lines()
    val total = lines.size.coerceAtLeast(1)
    if (requestedStart == null && requestedEnd == null) {
        return SelectedLineRange(
            content = content,
            startLine = 1,
            endLine = total,
            totalLines = total,
            wasRanged = false,
        )
    }
    val start = (requestedStart ?: 1).coerceIn(1, total)
    val end = (requestedEnd ?: total)
        .coerceIn(start, total)
        .coerceAtMost(start + MAX_LINE_RANGE - 1)
    return SelectedLineRange(
        content = lines.subList(start - 1, end).joinToString("\n"),
        startLine = start,
        endLine = end,
        totalLines = total,
        wasRanged = true,
    )
}

private const val MAX_LINE_RANGE = 400
private const val MAX_READ_BYTES = 128 * 1024

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
