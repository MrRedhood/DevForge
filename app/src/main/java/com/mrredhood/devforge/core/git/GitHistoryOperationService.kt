package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.lib.ObjectId
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface GitHistoryResult {
    data class Success(val message: String) : GitHistoryResult
    data class Conflict(val operation: String, val paths: List<String>) : GitHistoryResult
    data class Failure(val message: String) : GitHistoryResult
}

/**
 * Safe local history operations over an ephemeral JGit mirror.
 *
 * SAF remains the canonical workspace boundary. Operations require a clean worktree,
 * are bounded by the transport mirror limits, and are only copied back after success.
 * Conflicted operations are discarded from the temporary mirror so DevForge never
 * silently leaves half-applied merge/rebase/cherry-pick state in the user workspace.
 */
class GitHistoryOperationService(
    private val context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) {
    suspend fun switchBranch(repository: GitRepositoryState, branchName: String): GitHistoryResult =
        execute(repository, syncWorktree = true) { git ->
            val branch = validateBranchName(branchName)
            require(git.status().call().isClean) {
                "Branch switch is blocked while the working tree has local changes. Commit or preserve them first."
            }
            require(git.repository.findRef("refs/heads/$branch") != null) {
                "Local branch '$branch' does not exist."
            }
            git.checkout().setName(branch).call()
            "Switched to branch '$branch'."
        }

    suspend fun merge(repository: GitRepositoryState, branchName: String): GitHistoryResult =
        executeHistory(repository, "merge '$branchName'") { git ->
            val branch = validateBranchName(branchName)
            requireClean(git)
            require(git.repository.findRef("refs/heads/$branch") != null) {
                "Local branch '$branch' does not exist."
            }
            val result = git.merge()
                .include(git.repository.findRef("refs/heads/$branch")!!.objectId)
                .call()
            handleMergeResult(result, "Merge '$branchName'")
        }

    suspend fun rebase(repository: GitRepositoryState, branchName: String): GitHistoryResult =
        executeHistory(repository, "rebase '$branchName'") { git ->
            val branch = validateBranchName(branchName)
            requireClean(git)
            require(git.repository.findRef("refs/heads/$branch") != null) {
                "Local branch '$branch' does not exist."
            }
            val result = git.rebase()
                .setUpstream("refs/heads/$branch")
                .call()
            if (result.status.isSuccessful) {
                GitHistoryResult.Success("Rebased current branch onto '$branch'.")
            } else {
                GitHistoryResult.Failure("Rebase did not complete (status: ${result.status.name}). The workspace was not changed.")
            }
        }

    suspend fun cherryPick(repository: GitRepositoryState, revision: String): GitHistoryResult =
        executeHistory(repository, "cherry-pick") { git ->
            val commitId = revision.trim()
            require(commitId.matches(SHA_PATTERN)) { "Cherry-pick requires a 40-character commit SHA." }
            requireClean(git)
            val objectId = ObjectId.fromString(commitId)
            val commit = git.repository.parseCommit(objectId)
            val result = git.cherryPick().include(commit).call()
            if (result.status == CherryPickResult.CherryPickStatus.OK) {
                GitHistoryResult.Success("Cherry-picked ${commitId.take(12)}.")
            } else {
                GitHistoryResult.Failure("Cherry-pick did not complete (status: ${result.status.name}). The workspace was not changed.")
            }
        }

    private suspend fun executeHistory(
        repository: GitRepositoryState,
        operation: String,
        block: (Git) -> GitHistoryResult,
    ): GitHistoryResult = withContext(Dispatchers.IO) {
        val workRoot = File(context.cacheDir, "devforge-git-history/${UUID.randomUUID()}")
        val repoRoot = File(workRoot, "repo")
        try {
            copySafWorkspaceToFile(repository.rootUri, repoRoot)
            Git.open(repoRoot).use { git ->
                when (val result = block(git)) {
                    is GitHistoryResult.Success -> {
                        syncWorkspaceBack(repoRoot, repository.rootUri)
                        result
                    }
                    is GitHistoryResult.Conflict -> result
                    is GitHistoryResult.Failure -> result
                }
            }
        } catch (error: Throwable) {
            GitHistoryResult.Failure("$operation failed: ${sanitizeError(error)}")
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private suspend fun execute(
        repository: GitRepositoryState,
        syncWorktree: Boolean,
        block: (Git) -> String,
    ): GitHistoryResult = withContext(Dispatchers.IO) {
        val workRoot = File(context.cacheDir, "devforge-git-history/${UUID.randomUUID()}")
        val repoRoot = File(workRoot, "repo")
        try {
            copySafWorkspaceToFile(repository.rootUri, repoRoot)
            Git.open(repoRoot).use { git ->
                val message = block(git)
                if (syncWorktree) syncWorkspaceBack(repoRoot, repository.rootUri)
                GitHistoryResult.Success(message)
            }
        } catch (error: Throwable) {
            GitHistoryResult.Failure(sanitizeError(error))
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private fun handleMergeResult(result: MergeResult, operation: String): GitHistoryResult {
        if (result.mergeStatus.isSuccessful) {
            return GitHistoryResult.Success("$operation completed (${result.mergeStatus.name}).")
        }
        val conflicts = result.conflicts?.keys?.sorted().orEmpty()
        return if (conflicts.isNotEmpty()) {
            GitHistoryResult.Conflict(operation, conflicts.take(MAX_CONFLICT_PATHS))
        } else {
            GitHistoryResult.Failure("$operation did not complete (${result.mergeStatus.name}). The workspace was not changed.")
        }
    }

    private fun requireClean(git: Git) {
        require(git.status().call().isClean) {
            "This history operation requires a clean working tree. Commit or preserve local changes first."
        }
    }

    private fun validateBranchName(value: String): String {
        val branch = value.trim()
        require(branch.isNotBlank()) { "Branch name cannot be empty." }
        require(branch.length <= 200) { "Branch name is too long." }
        require(!branch.startsWith('/') && !branch.endsWith('/') && !branch.startsWith('.') && !branch.endsWith('.')) {
            "Invalid Git branch name."
        }
        require(!branch.contains("..") && !branch.contains("@{") && !branch.contains(' ')) {
            "Invalid Git branch name."
        }
        require(branch.none { it.code < 32 || it == '~' || it == '^' || it == ':' || it == '?' || it == '*' || it == '[' || it == '\\' }) {
            "Invalid Git branch name."
        }
        return branch
    }

    private fun copySafWorkspaceToFile(sourceRoot: Uri, targetRoot: File) {
        targetRoot.mkdirs()
        copySafNode(sourceRoot, targetRoot, CopyBudget(), "")
    }

    private fun copySafNode(source: Uri, target: File, budget: CopyBudget, relativePath: String) {
        val metadata = queryDocument(source) ?: throw IOException("Unable to inspect workspace document.")
        if (metadata.isDirectory) {
            target.mkdirs()
            listChildren(source).forEach { child ->
                copySafNode(child.uri, File(target, child.name), budget, if (relativePath.isBlank()) child.name else "$relativePath/${child.name}")
            }
            return
        }
        if (metadata.size > MAX_FILE_BYTES) throw IOException("History mirror encountered an oversized file: $relativePath")
        budget.consumeFile(metadata.size, relativePath)
        resolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> copyBounded(input, output, metadata.size) }
        } ?: throw IOException("Unable to read workspace file: $relativePath")
    }

    private fun syncWorkspaceBack(sourceRoot: File, targetRoot: Uri) {
        val budget = CopyBudget()
        syncDirectoryFromFile(sourceRoot, targetRoot, budget, "")
    }

    private fun syncDirectoryFromFile(source: File, target: Uri, budget: CopyBudget, relativePath: String) {
        if (!source.exists() || !source.isDirectory) throw IOException("History output directory is missing: $relativePath")
        val targetChildren = listChildren(target).associateBy { it.name }.toMutableMap()
        source.listFiles()?.sortedBy { it.name }?.forEach { item ->
            val itemPath = if (relativePath.isBlank()) item.name else "$relativePath/${item.name}"
            val existing = targetChildren.remove(item.name)?.uri
            if (item.isDirectory) {
                val directory = existing ?: DocumentsContract.createDocument(resolver, target, DocumentsContract.Document.MIME_TYPE_DIR, item.name)
                    ?: throw IOException("Unable to create workspace directory: $itemPath")
                syncDirectoryFromFile(item, directory, budget, itemPath)
            } else {
                if (item.length() > MAX_FILE_BYTES) throw IOException("History output contains an oversized file: $itemPath")
                budget.consumeFile(item.length(), itemPath)
                val document = existing ?: DocumentsContract.createDocument(resolver, target, "application/octet-stream", item.name)
                    ?: throw IOException("Unable to create workspace file: $itemPath")
                resolver.openOutputStream(document, "wt")?.use { output ->
                    item.inputStream().use { input -> copyBounded(input, output, item.length()) }
                } ?: throw IOException("Unable to write workspace file: $itemPath")
            }
        }
        targetChildren.values.forEach { orphan -> DocumentsContract.deleteDocument(resolver, orphan.uri) }
    }

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (copied < expectedBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedBytes - copied).toInt())
            if (read <= 0) throw IOException("Unexpected end of workspace file during history operation.")
            output.write(buffer, 0, read)
            copied += read
        }
    }

    private fun listChildren(parent: Uri): List<DocumentRef> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < MAX_CHILDREN_PER_DIRECTORY) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    add(DocumentRef(DocumentsContract.buildDocumentUriUsingTree(parent, id), name, mime == DocumentsContract.Document.MIME_TYPE_DIR, cursor.getLong(3).takeIf { it >= 0L } ?: 0L))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun queryDocument(uri: Uri): DocumentMetadata? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DocumentMetadata(cursor.getString(0).orEmpty() == DocumentsContract.Document.MIME_TYPE_DIR, cursor.getLong(1).takeIf { it >= 0L } ?: 0L)
        }
    }.getOrNull()

    private fun sanitizeError(error: Throwable): String = error.message?.takeIf(String::isNotBlank)?.take(320) ?: error.javaClass.simpleName

    private data class DocumentRef(val uri: Uri, val name: String, val directory: Boolean, val size: Long)
    private data class DocumentMetadata(val isDirectory: Boolean, val size: Long)

    private class CopyBudget {
        private var bytes = 0L
        private var files = 0
        fun consumeFile(size: Long, path: String) {
            files++
            bytes += size
            require(files <= MAX_FILES) { "History operation exceeded the repository file safety limit at $path." }
            require(bytes <= MAX_TOTAL_BYTES) { "History operation exceeded the repository size safety limit at $path." }
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L
        const val MAX_FILES = 5_000
        const val MAX_CHILDREN_PER_DIRECTORY = 1_000
        const val MAX_CONFLICT_PATHS = 100
        val SHA_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}
