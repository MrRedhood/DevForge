package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Computes bounded Git index/worktree status through SAF.
 *
 * This compares the working tree against the Git index. It intentionally does not
 * compare the index with HEAD yet, so staged-vs-unstaged state remains unavailable.
 */
class GitWorkspaceStatusService(private val resolver: ContentResolver) {
    suspend fun inspect(
        root: Uri,
        gitDirectory: Uri? = null,
        maxFiles: Int = MAX_FILES,
    ): GitWorkspaceStatus = withContext(Dispatchers.IO) {
        val indexUri = gitDirectory?.let { findDirectChild(it, "index") }
        val indexBytes = indexUri?.let { readBytes(it, MAX_INDEX_BYTES) }

        val parsedIndex = when {
            indexBytes == null -> null
            else -> GitIndexParser.parse(indexBytes)
        }

        when (parsedIndex) {
            is GitIndexParseResult.Success -> inspectAgainstIndex(root, parsedIndex.entries, maxFiles, parsedIndex.truncated)
            is GitIndexParseResult.Unsupported -> observeOnly(root, maxFiles, parsedIndex.reason)
            null -> observeOnly(root, maxFiles, "The Git index is unavailable; showing bounded workspace observation instead.")
        }
    }

    private fun inspectAgainstIndex(
        root: Uri,
        indexEntries: List<GitIndexEntry>,
        maxFiles: Int,
        parserTruncated: Boolean,
    ): GitWorkspaceStatus {
        val worktree = mutableListOf<WorktreeFile>()
        walk(root, "", worktree, maxFiles, 0)

        val worktreeByPath = worktree.associateBy { it.path }
        val indexByPath = indexEntries.associateBy { it.path }
        val files = ArrayList<GitWorkspaceFile>(minOf(maxFiles, indexEntries.size + worktree.size))

        for (entry in indexEntries) {
            if (files.size >= maxFiles) break
            val current = worktreeByPath[entry.path]
            val status = when {
                current == null -> GitFileStatus.Deleted
                current.gitBlobHash == null -> GitFileStatus.Unchecked
                current.gitBlobHash == entry.objectId -> GitFileStatus.Clean
                else -> GitFileStatus.Modified
            }
            files += GitWorkspaceFile(
                path = entry.path,
                sizeBytes = current?.sizeBytes,
                contentHash = current?.gitBlobHash,
                readError = current?.readError == true,
                gitStatus = status,
                indexObjectId = entry.objectId,
                worktreeObjectId = current?.gitBlobHash,
            )
        }

        for (current in worktree) {
            if (files.size >= maxFiles) break
            if (current.path !in indexByPath) {
                files += GitWorkspaceFile(
                    path = current.path,
                    sizeBytes = current.sizeBytes,
                    contentHash = current.gitBlobHash,
                    readError = current.readError,
                    gitStatus = if (current.readError) GitFileStatus.Unchecked else GitFileStatus.Untracked,
                    indexObjectId = null,
                    worktreeObjectId = current.gitBlobHash,
                )
            }
        }

        val truncated = parserTruncated || worktree.size >= maxFiles || files.size >= maxFiles
        return GitWorkspaceStatus(
            state = if (truncated || files.any { it.gitStatus == GitFileStatus.Unchecked }) GitStatusConfidence.Partial else GitStatusConfidence.Observed,
            mode = GitStatusAvailability.IndexAwareWorktree,
            files = files,
            truncated = truncated,
            message = if (truncated) {
                "Index/worktree status is bounded; some entries were not inspected. Staged-vs-unstaged state is not computed yet."
            } else {
                "Compared the bounded working tree against the Git index. Staged-vs-unstaged state is not computed yet."
            },
        )
    }

    private fun observeOnly(root: Uri, maxFiles: Int, reason: String): GitWorkspaceStatus {
        val files = mutableListOf<GitWorkspaceFile>()
        walk(root, "", files.mapToMutableObservation(maxFiles), maxFiles, 0)
        return GitWorkspaceStatus(
            state = if (files.any { it.readError }) GitStatusConfidence.Partial else GitStatusConfidence.Observed,
            mode = GitStatusAvailability.MetadataOnly,
            files = files,
            truncated = files.size >= maxFiles,
            message = "$reason Staged/unstaged status is unavailable on this access path.",
        )
    }

    private fun MutableList<WorktreeFile>.mapToMutableObservation(maxFiles: Int): MutableList<WorktreeFile> = this

    private fun walk(
        parent: Uri,
        relativePrefix: String,
        files: MutableList<WorktreeFile>,
        maxFiles: Int,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH || files.size >= maxFiles) return
        for (child in listChildren(parent)) {
            if (files.size >= maxFiles) return
            if (child.name == ".git" || child.name == "build" || child.name == ".gradle") continue
            val relativePath = if (relativePrefix.isBlank()) child.name else "$relativePrefix/${child.name}"
            if (child.directory) {
                walk(child.uri, relativePath, files, maxFiles, depth + 1)
            } else {
                val size = child.sizeBytes
                val hash = if (size != null && size <= MAX_HASH_BYTES) readGitBlobHash(child.uri, size) else null
                files += WorktreeFile(
                    path = relativePath,
                    sizeBytes = size,
                    gitBlobHash = hash,
                    readError = size != null && size <= MAX_HASH_BYTES && hash == null,
                )
            }
        }
    }

    private fun readGitBlobHash(uri: Uri, declaredSize: Long): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-1")
            val header = "blob $declaredSize\u0000".toByteArray(Charsets.UTF_8)
            digest.update(header)
            var totalRead = 0L
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalRead += read
                if (totalRead > MAX_HASH_BYTES) return@use null
                digest.update(buffer, 0, read)
            }
            if (totalRead != declaredSize) null
            else digest.digest().joinToString("") { "%02x".format(it) }
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

    private fun findDirectChild(parent: Uri, name: String): Uri? =
        listChildren(parent).firstOrNull { it.name == name }?.uri

    private data class WorktreeFile(
        val path: String,
        val sizeBytes: Long?,
        val gitBlobHash: String?,
        val readError: Boolean,
    )

    private data class ChildDocument(
        val uri: Uri,
        val name: String,
        val directory: Boolean,
        val sizeBytes: Long?,
    )

    private fun listChildren(parent: Uri): List<ChildDocument> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
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
    val worktreeObjectId: String? = null,
)
