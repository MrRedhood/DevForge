package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Computes bounded Git status through the SAF tree.
 *
 * When the Git HEAD commit/tree can be read from loose objects, status includes
 * staged-only and staged+unstaged distinctions. When objects are packed or otherwise
 * unavailable, the service safely falls back to index/worktree comparison.
 */
class GitWorkspaceStatusService(private val resolver: ContentResolver) {
    suspend fun inspect(
        root: Uri,
        gitDirectory: Uri? = null,
        headRevision: String? = null,
        maxFiles: Int = MAX_FILES,
    ): GitWorkspaceStatus = withContext(Dispatchers.IO) {
        val indexUri = gitDirectory?.let { findDirectChild(it, "index") }
        val indexBytes = indexUri?.let { readBytes(it, MAX_INDEX_BYTES) }
        val parsedIndex = indexBytes?.let(GitIndexParser::parse)

        when (parsedIndex) {
            is GitIndexParseResult.Success -> {
                val reader = gitDirectory?.let { GitObjectReader(resolver, it) }
                val headFiles = if (reader != null && headRevision != null) {
                    readHeadFiles(reader, headRevision)
                } else {
                    HeadReadResult.Unavailable("Git HEAD objects are unavailable on this access path.")
                }
                if (headFiles is HeadReadResult.Success) {
                    inspectAgainstHeadIndexWorktree(root, parsedIndex.entries, headFiles.files, maxFiles, parsedIndex.truncated, headFiles.truncated)
                } else {
                    val reason = (headFiles as? HeadReadResult.Unavailable)?.reason ?: "Git HEAD objects are unavailable on this access path."
                    inspectAgainstIndex(root, parsedIndex.entries, maxFiles, parsedIndex.truncated, reason)
                }
            }
            is GitIndexParseResult.Unsupported -> observeOnly(root, maxFiles, parsedIndex.reason)
            null -> observeOnly(root, maxFiles, "The Git index is unavailable; showing bounded workspace observation instead.")
        }
    }

    private fun inspectAgainstHeadIndexWorktree(
        root: Uri,
        indexEntries: List<GitIndexEntry>,
        headFiles: Map<String, String>,
        maxFiles: Int,
        parserTruncated: Boolean,
        headTruncated: Boolean,
    ): GitWorkspaceStatus {
        val worktree = mutableListOf<WorktreeFile>()
        walk(root, "", worktree, maxFiles, 0)
        val worktreeByPath = worktree.associateBy { it.path }
        val indexByPath = indexEntries.filter { it.stage == 0 }.associateBy { it.path }
        val conflictPaths = indexEntries.filter { it.stage != 0 }.map { it.path }.toSet()
        val allPaths = (headFiles.keys + indexByPath.keys + conflictPaths + worktreeByPath.keys).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        val files = ArrayList<GitWorkspaceFile>(minOf(maxFiles, allPaths.size))

        for (path in allPaths) {
            if (files.size >= maxFiles) break
            val headId = headFiles[path]
            val indexEntry = indexByPath[path]
            val current = worktreeByPath[path]
            val status = when {
                path in conflictPaths -> GitFileStatus.Conflict
                headId == null && indexEntry == null -> if (current?.readError == true) GitFileStatus.Unchecked else GitFileStatus.Untracked
                current == null -> {
                    val indexChanged = indexEntry?.objectId != headId
                    if (indexEntry == null && headId != null) GitFileStatus.Staged
                    else if (indexChanged) GitFileStatus.StagedAndModified
                    else GitFileStatus.Deleted
                }
                current.gitBlobHash == null -> GitFileStatus.Unchecked
                else -> {
                    val indexId = indexEntry?.objectId
                    val indexChanged = indexId != headId
                    val worktreeChanged = indexId != current.gitBlobHash
                    when {
                        !indexChanged && !worktreeChanged -> GitFileStatus.Clean
                        indexChanged && !worktreeChanged -> GitFileStatus.Staged
                        !indexChanged && worktreeChanged -> GitFileStatus.Modified
                        else -> GitFileStatus.StagedAndModified
                    }
                }
            }
            files += GitWorkspaceFile(
                path = path,
                sizeBytes = current?.sizeBytes,
                contentHash = current?.gitBlobHash,
                readError = current?.readError == true,
                gitStatus = status,
                indexObjectId = indexEntry?.objectId,
                headObjectId = headId,
                worktreeObjectId = current?.gitBlobHash,
            )
        }

        val truncated = parserTruncated || headTruncated || worktree.size >= maxFiles || files.size >= maxFiles
        return GitWorkspaceStatus(
            state = if (truncated || files.any { it.gitStatus == GitFileStatus.Unchecked }) GitStatusConfidence.Partial else GitStatusConfidence.Observed,
            mode = GitStatusAvailability.IndexAndHeadAware,
            files = files,
            truncated = truncated,
            message = when {
                truncated -> "HEAD/index/worktree status is bounded; some entries were not inspected. Git mutations are limited until the status is complete."
                else -> "Compared HEAD, the Git index, and the bounded working tree. Stage/commit/push operations are available."
            },
        )
    }

    private fun inspectAgainstIndex(
        root: Uri,
        indexEntries: List<GitIndexEntry>,
        maxFiles: Int,
        parserTruncated: Boolean,
        fallbackReason: String,
    ): GitWorkspaceStatus {
        val worktree = mutableListOf<WorktreeFile>()
        walk(root, "", worktree, maxFiles, 0)

        val worktreeByPath = worktree.associateBy { it.path }
        val indexByPath = indexEntries.filter { it.stage == 0 }.associateBy { it.path }
        val conflictPaths = indexEntries.filter { it.stage != 0 }.map { it.path }.toSet()
        val allPaths = (indexByPath.keys + conflictPaths + worktreeByPath.keys).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        val files = ArrayList<GitWorkspaceFile>(minOf(maxFiles, allPaths.size))

        for (path in allPaths) {
            if (files.size >= maxFiles) break
            val entry = indexByPath[path]
            val current = worktreeByPath[path]
            val status = when {
                path in conflictPaths -> GitFileStatus.Conflict
                entry == null -> if (current?.readError == true) GitFileStatus.Unchecked else GitFileStatus.Untracked
                current == null -> GitFileStatus.Deleted
                current.gitBlobHash == null -> GitFileStatus.Unchecked
                current.gitBlobHash == entry.objectId -> GitFileStatus.Clean
                else -> GitFileStatus.Modified
            }
            files += GitWorkspaceFile(
                path = path,
                sizeBytes = current?.sizeBytes,
                contentHash = current?.gitBlobHash,
                readError = current?.readError == true,
                gitStatus = status,
                indexObjectId = entry?.objectId,
                headObjectId = null,
                worktreeObjectId = current?.gitBlobHash,
            )
        }

        val truncated = parserTruncated || worktree.size >= maxFiles || files.size >= maxFiles
        return GitWorkspaceStatus(
            state = if (truncated || files.any { it.gitStatus == GitFileStatus.Unchecked }) GitStatusConfidence.Partial else GitStatusConfidence.Observed,
            mode = GitStatusAvailability.IndexAwareWorktree,
            files = files,
            truncated = truncated,
            message = "$fallbackReason Index/worktree comparison is available, but staged-vs-unstaged state could not be verified.",
        )
    }

    private fun observeOnly(root: Uri, maxFiles: Int, reason: String): GitWorkspaceStatus {
        val worktree = mutableListOf<WorktreeFile>()
        walk(root, "", worktree, maxFiles, 0)
        val files = worktree.map { current ->
            GitWorkspaceFile(
                path = current.path,
                sizeBytes = current.sizeBytes,
                contentHash = current.gitBlobHash,
                readError = current.readError,
                gitStatus = GitFileStatus.Unchecked,
                indexObjectId = null,
                headObjectId = null,
                worktreeObjectId = current.gitBlobHash,
            )
        }
        return GitWorkspaceStatus(
            state = if (files.any { it.readError }) GitStatusConfidence.Partial else GitStatusConfidence.Observed,
            mode = GitStatusAvailability.MetadataOnly,
            files = files,
            truncated = files.size >= maxFiles,
            message = "$reason Staged/unstaged status is unavailable on this access path.",
        )
    }

    private fun readHeadFiles(reader: GitObjectReader, headRevision: String): HeadReadResult {
        val treeId = reader.readCommitTree(headRevision)
            ?: return HeadReadResult.Unavailable("HEAD commit $headRevision could not be read as a loose Git object; packed object storage is not supported yet.")
        val result = linkedMapOf<String, String>()
        val traversed = traverseTree(reader, treeId, "", result, 0)
        return when (traversed) {
            is TreeTraversal.Success -> HeadReadResult.Success(result, traversed.truncated)
            is TreeTraversal.Unavailable -> HeadReadResult.Unavailable(traversed.reason)
        }
    }

    private fun traverseTree(
        reader: GitObjectReader,
        treeId: String,
        prefix: String,
        result: MutableMap<String, String>,
        depth: Int,
    ): TreeTraversal {
        if (depth > MAX_TREE_DEPTH) return TreeTraversal.Success(truncated = true)
        return when (val tree = reader.readTree(treeId, MAX_TREE_ENTRIES)) {
            is GitObjectResultWithEntries.Unavailable -> TreeTraversal.Unavailable(tree.reason)
            is GitObjectResultWithEntries.Success -> {
                var truncated = tree.truncated
                for (entry in tree.entries) {
                    if (result.size >= MAX_HEAD_FILES) {
                        truncated = true
                        break
                    }
                    val path = if (prefix.isBlank()) entry.name else "$prefix/${entry.name}"
                    when {
                        entry.mode.startsWith("04") -> {
                            when (val nested = traverseTree(reader, entry.objectId, path, result, depth + 1)) {
                                is TreeTraversal.Success -> truncated = truncated || nested.truncated
                                is TreeTraversal.Unavailable -> return nested
                            }
                        }
                        entry.mode == "160000" -> result[path] = entry.objectId
                        else -> result[path] = entry.objectId
                    }
                }
                TreeTraversal.Success(truncated)
            }
        }
    }

    private fun walk(parent: Uri, relativePrefix: String, files: MutableList<WorktreeFile>, maxFiles: Int, depth: Int) {
        if (depth > MAX_DEPTH || files.size >= maxFiles) return
        for (child in listChildren(parent)) {
            if (files.size >= maxFiles) return
            if (child.name == ".git" || child.name == "build" || child.name == ".gradle") continue
            val relativePath = if (relativePrefix.isBlank()) child.name else "$relativePrefix/${child.name}"
            if (child.directory) walk(child.uri, relativePath, files, maxFiles, depth + 1)
            else {
                val size = child.sizeBytes
                val hash = if (size != null && size <= MAX_HASH_BYTES) readGitBlobHash(child.uri, size) else null
                files += WorktreeFile(path = relativePath, sizeBytes = size, gitBlobHash = hash, readError = size != null && size <= MAX_HASH_BYTES && hash == null)
            }
        }
    }

    private fun readGitBlobHash(uri: Uri, declaredSize: Long): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob $declaredSize\u0000".toByteArray(Charsets.UTF_8))
            var totalRead = 0L
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalRead += read
                if (totalRead > MAX_HASH_BYTES) return@use null
                digest.update(buffer, 0, read)
            }
            if (totalRead != declaredSize) null else digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    private fun readBytes(uri: Uri, maxBytes: Int): ByteArray? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 1024 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) return@use null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }.getOrNull()

    private fun findDirectChild(parent: Uri, name: String): Uri? = listChildren(parent).firstOrNull { it.name == name }?.uri

    private data class WorktreeFile(val path: String, val sizeBytes: Long?, val gitBlobHash: String?, val readError: Boolean)
    private sealed interface HeadReadResult {
        data class Success(val files: Map<String, String>, val truncated: Boolean) : HeadReadResult
        data class Unavailable(val reason: String) : HeadReadResult
    }
    private sealed interface TreeTraversal {
        data class Success(val truncated: Boolean) : TreeTraversal
        data class Unavailable(val reason: String) : TreeTraversal
    }
    private data class ChildDocument(val uri: Uri, val name: String, val directory: Boolean, val sizeBytes: Long?)

    private fun listChildren(parent: Uri): List<ChildDocument> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < MAX_ENTRIES_PER_FOLDER) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                    add(ChildDocument(DocumentsContract.buildDocumentUriUsingTree(parent, id), name, mime == DocumentsContract.Document.MIME_TYPE_DIR, size))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_DEPTH = 10
        const val MAX_ENTRIES_PER_FOLDER = 400
        const val MAX_FILES = 400
        const val MAX_HASH_BYTES = 2L * 1024L * 1024L
        const val MAX_INDEX_BYTES = 16 * 1024 * 1024
        const val MAX_TREE_DEPTH = 12
        const val MAX_TREE_ENTRIES = 20_000
        const val MAX_HEAD_FILES = 20_000
    }
}

enum class GitStatusConfidence { Observed, Partial }

data class GitWorkspaceStatus(
    val state: GitStatusConfidence,
    val mode: GitStatusAvailability,
    val files: List<GitWorkspaceFile>,
    val truncated: Boolean,
    val message: String? = null,
)

data class GitWorkspaceFile(
    val path: String,
    val sizeBytes: Long?,
    val contentHash: String?,
    val readError: Boolean,
    val gitStatus: GitFileStatus = GitFileStatus.Unchecked,
    val indexObjectId: String? = null,
    val headObjectId: String? = null,
    val worktreeObjectId: String? = null,
)
