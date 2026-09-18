package com.mrredhood.devforge.core.agent

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.WorkspaceDao
import com.mrredhood.devforge.core.editor.ContentHasher
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import com.mrredhood.devforge.core.workspace.WorkspaceSearch
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Registers bounded SAF workspace tools. There is deliberately no shell/exec tool here. */
class WorkspaceAgentToolProvider(
    private val resolver: ContentResolver,
    private val workspaceDao: WorkspaceDao,
) {
    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry = registry
        .register(ReadFileTool())
        .register(ListFilesTool())
        .register(SearchWorkspaceTool())
        .register(PatchFileTool())
        .register(WriteFileTool())

    private abstract inner class WorkspaceTool : AgentTool {
        protected suspend fun root(context: AgentToolContext): Uri {
            val workspace = workspaceDao.findById(context.workspaceId)
                ?: throw IllegalArgumentException("Workspace '${context.workspaceId}' was not found.")
            return Uri.parse(workspace.treeUri)
        }

        protected fun scopedPath(scope: WorkspacePathScope, rawPath: String, allowEmpty: Boolean = false): String =
            WorkspacePathScope.normalize(rawPath, allowEmpty).also { scope.requireAllowed(it) }

        protected val access = WorkspaceAgentFileAccess(resolver)
    }

    private inner class ReadFileTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.READ_FILE,
            "Read one bounded text file from the selected workspace.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val path = scopedPath(context.pathScope, JSONObject(request.argumentsJson).optString("path").trim())
            val content = access.readText(root(context), path)
            AgentToolResult.Success(
                summary = "Read $path.",
                output = JSONObject().put("path", path).put("content", content).toString(),
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
            val path = scopedPath(context.pathScope, args.optString("path", "").trim(), allowEmpty = true)
            val limit = args.optInt("limit", 100).coerceIn(1, MAX_LIST_ENTRIES)
            val entries = access.list(root(context), path, limit)
            val result = JSONArray()
            entries.forEach { entry ->
                result.put(
                    JSONObject()
                        .put("name", entry.name)
                        .put("directory", entry.isDirectory)
                        .put("sizeBytes", entry.sizeBytes),
                )
            }
            AgentToolResult.Success(
                summary = "Listed ${entries.size} entries${if (path.isBlank()) "" else " in $path"}.",
                output = JSONObject().put("path", path).put("entries", result).toString(),
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
            val workspaceRoot = root(context)
            val output = JSONArray()
            val prefixes = context.pathScope.canonicalPrefixes()
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
            val path = scopedPath(context.pathScope, patch.path)
            val rootUri = root(context)
            val before = runCatchingCancellable { access.readText(rootUri, path) }.getOrElse {
                require(it.message?.contains("does not exist", true) == true) { it.message ?: "Unable to read patch target." }
                ""
            }
            val currentHash = ContentHasher.sha256(before)
            patch.expectedContentHash?.let { expected ->
                require(expected.equals(currentHash, ignoreCase = true)) {
                    "Patch precondition failed for $path; the file changed after the patch was prepared."
                }
            }
            require(before != patch.content) { "Patch produces no content change for $path." }
            access.writeText(rootUri, path, patch.content)
            AgentToolResult.Success(
                summary = patch.summary.ifBlank { "Applied patch to $path." }.take(500),
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
            listOf(AgentFilePatchCodec.decode(request.argumentsJson).path)

        override suspend fun preconditionHash(context: AgentToolContext, request: AgentToolRequest): String? {
            val patch = AgentFilePatchCodec.decode(request.argumentsJson)
            val path = scopedPath(context.pathScope, patch.path)
            val rootUri = root(context)
            val before = runCatchingCancellable { access.readText(rootUri, path) }.getOrElse {
                require(it.message?.contains("does not exist", true) == true) { it.message ?: "Unable to read patch target." }
                ""
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

    private inner class WriteFileTool : WorkspaceTool() {
        override val definition = AgentToolDefinition(
            AgentToolId.WRITE_FILE,
            "Write one bounded UTF-8 text file inside the selected workspace.",
            Capability.EDIT_FILES,
            RiskLevel.R2,
            sideEffecting = true,
        )

        override suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> =
            listOf(JSONObject(request.argumentsJson).optString("path").trim())

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val path = scopedPath(context.pathScope, args.optString("path").trim())
            val content = args.optString("content", "")
            access.writeText(root(context), path, content)
            AgentToolResult.Success(
                summary = "Wrote $path.",
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

    companion object {
        private const val MAX_LIST_ENTRIES = 100
        private const val MAX_SEARCH_RESULTS = 50
    }
}

private data class AgentWorkspaceEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

private class WorkspaceAgentFileAccess(private val resolver: ContentResolver) {
    private val tree = WorkspaceFileTree(resolver)

    suspend fun list(root: Uri, path: String, limit: Int): List<AgentWorkspaceEntry> = withContext(Dispatchers.IO) {
        val directory = resolve(root, path)
        require(isDirectory(directory)) { "Workspace path is not a directory: $path" }
        tree.list(directory, limit).map { AgentWorkspaceEntry(it.name, it.isDirectory, it.sizeBytes) }
    }

    suspend fun readText(root: Uri, path: String): String = withContext(Dispatchers.IO) {
        val file = resolve(root, path)
        require(!isDirectory(file)) { "Cannot read a directory as a file: $path" }
        val bytes = readBounded(file, MAX_READ_BYTES)
        require(!bytes.contains(0.toByte())) { "Binary files are not readable through the agent text tool." }
        bytes.toString(Charsets.UTF_8)
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
            ?: DocumentsContract.createDocument(resolver, parent, "text/plain", name)
            ?: throw IOException("Unable to create $normalized")
        require(!isDirectory(target)) { "Cannot overwrite a directory: $normalized" }
        resolver.openOutputStream(target, "wt")?.use { output -> output.write(bytes) }
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

    private fun findChild(parent: Uri, name: String): Uri? =
        tree.list(parent, MAX_DIRECTORY_ENTRIES).firstOrNull { it.name == name }?.uri

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

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
