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
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.lib.ObjectId
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface GitHistoryResult {
    data class Success(val message: String) : GitHistoryResult
    data class Conflict(
        val operation: String,
        val paths: List<String>,
        val sessionId: String? = null,
        val approvalId: String? = null,
    ) : GitHistoryResult
    data class Failure(val message: String) : GitHistoryResult
}

/**
 * Safe local history operations over an ephemeral JGit mirror.
 *
 * SAF remains the canonical workspace boundary. Operations require a clean worktree,
 * are bounded by the transport mirror limits, and are only copied back after success.
 * A conflicted operation remains inside an isolated app-cache session so it can be
 * inspected/resolved without exposing partial Git state to the user workspace.
 */
class GitHistoryOperationService(
    private val context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) {
    private val repositoryService = GitRepositoryService(resolver)
    private val statusService = GitWorkspaceStatusService(resolver)
    private val conflictSessions = ConcurrentHashMap<String, ActiveConflictSession>()

    init {
        cleanupAbandonedSessionDirectories()
    }

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

    suspend fun merge(repository: GitRepositoryState, branchName: String, approvalId: String? = null): GitHistoryResult =
        executeHistory(repository, "merge '$branchName'", OPERATION_MERGE, approvalId) { git ->
            val branch = validateBranchName(branchName)
            requireClean(git)
            require(git.repository.findRef("refs/heads/$branch") != null) { "Local branch '$branch' does not exist." }
            val result = git.merge().include(git.repository.findRef("refs/heads/$branch")!!.objectId).call()
            handleMergeResult(result, "Merge '$branchName'")
        }

    suspend fun rebase(repository: GitRepositoryState, branchName: String, approvalId: String? = null): GitHistoryResult =
        executeHistory(repository, "rebase '$branchName'", OPERATION_REBASE, approvalId) { git ->
            val branch = validateBranchName(branchName)
            requireClean(git)
            require(git.repository.findRef("refs/heads/$branch") != null) { "Local branch '$branch' does not exist." }
            val result = git.rebase().setUpstream("refs/heads/$branch").call()
            if (result.status.isSuccessful) GitHistoryResult.Success("Rebased current branch onto '$branch'.")
            else if (result.status.name.equals("CONFLICTS", ignoreCase = true)) conflictResult(git, "Rebase onto '$branchName'")
            else GitHistoryResult.Failure("Rebase did not complete (status: ${result.status.name}). The workspace was not changed.")
        }

    suspend fun cherryPick(repository: GitRepositoryState, revision: String, approvalId: String? = null): GitHistoryResult =
        executeHistory(repository, "cherry-pick", OPERATION_CHERRY_PICK, approvalId) { git ->
            val commitId = revision.trim()
            require(commitId.matches(SHA_PATTERN)) { "Cherry-pick requires a 40-character commit SHA." }
            requireClean(git)
            val commit = git.repository.parseCommit(ObjectId.fromString(commitId))
            val result = git.cherryPick().include(commit).call()
            if (result.status == CherryPickResult.CherryPickStatus.OK) GitHistoryResult.Success("Cherry-picked ${commitId.take(12)}.")
            else if (git.status().call().conflicting.isNotEmpty()) conflictResult(git, "Cherry-pick ${commitId.take(12)}")
            else GitHistoryResult.Failure("Cherry-pick did not complete (status: ${result.status.name}). The workspace was not changed.")
        }

    suspend fun loadConflictSession(sessionId: String): GitConflictSessionSnapshot? = withContext(Dispatchers.IO) {
        conflictSessions[sessionId]?.let { session ->
            if (!session.repoRoot.exists()) {
                conflictSessions.remove(sessionId)
                return@withContext null
            }
            runCatching { snapshotSession(session) }.getOrNull()
        }
    }

    suspend fun resolveConflict(sessionId: String, path: String, content: String?): GitConflictSessionSnapshot? = withContext(Dispatchers.IO) {
        val session = activeSession(sessionId) ?: return@withContext null
        val normalized = normalizePath(path)
        Git.open(session.repoRoot).use { git ->
            require(normalized in git.status().call().conflicting) { "The file is no longer unresolved: $normalized" }
            if (content == null) {
                val target = safeRepoFile(session.repoRoot, normalized)
                if (target.exists() && !target.isDirectory) target.delete()
            } else {
                require(content.toByteArray(Charsets.UTF_8).size <= MAX_CONFLICT_BYTES) { "Resolved content exceeds the conflict editor limit." }
                val target = safeRepoFile(session.repoRoot, normalized)
                target.parentFile?.mkdirs()
                target.writeText(content, Charsets.UTF_8)
            }
            git.add().addFilepattern(normalized).call()
            snapshotSession(session)
        }
    }

    suspend fun continueConflict(sessionId: String): GitHistoryResult = withContext(Dispatchers.IO) {
        val session = activeSession(sessionId) ?: return@withContext GitHistoryResult.Failure("Conflict session is no longer available.")
        try {
            requireWorkspaceUnchanged(session)
            Git.open(session.repoRoot).use { git ->
                val remaining = git.status().call().conflicting.sorted().take(MAX_CONFLICT_PATHS)
                require(remaining.isEmpty()) { "Resolve all ${remaining.size.coerceAtLeast(1)} remaining conflict(s) before continuing." }
                val result = when (session.operation) {
                    OPERATION_MERGE -> {
                        git.commit().setMessage(readMergeCommitMessage(session.repoRoot) ?: "Merge conflict resolution").call()
                        GitHistoryResult.Success("Merge conflict resolution committed.")
                    }
                    OPERATION_CHERRY_PICK -> {
                        git.commit().setMessage(readCherryPickCommitMessage(git, session.repoRoot) ?: "Cherry-pick conflict resolution").call()
                        GitHistoryResult.Success("Cherry-pick conflict resolution committed.")
                    }
                    OPERATION_REBASE -> {
                        val rebase = git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call()
                        if (rebase.status.isSuccessful) GitHistoryResult.Success("Rebase conflict resolution continued successfully.")
                        else if (rebase.status.name.equals("CONFLICTS", ignoreCase = true)) conflictResult(git, "Rebase conflict resolution")
                        else GitHistoryResult.Failure("Rebase continuation did not complete (status: ${rebase.status.name}).")
                    }
                    else -> GitHistoryResult.Failure("Unsupported conflict operation.")
                }
                when (result) {
                    is GitHistoryResult.Conflict -> result.copy(sessionId = session.sessionId, approvalId = session.approvalId)
                    is GitHistoryResult.Success -> {
                        syncWorkspaceBack(session.repoRoot, session.workspaceRoot)
                        conflictSessions.remove(session.sessionId)
                        result
                    }
                    is GitHistoryResult.Failure -> result
                }
            }
        } catch (error: Throwable) {
            GitHistoryResult.Failure("${operationLabel(session.operation)} continuation failed: ${sanitizeError(error)}")
        } finally {
            if (!conflictSessions.containsKey(session.sessionId) && session.workRoot.exists()) session.workRoot.deleteRecursively()
        }
    }

    suspend fun abortConflict(sessionId: String): GitHistoryResult = withContext(Dispatchers.IO) {
        val session = conflictSessions.remove(sessionId) ?: return@withContext GitHistoryResult.Failure("Conflict session is no longer available.")
        session.workRoot.deleteRecursively()
        GitHistoryResult.Success("${operationLabel(session.operation)} conflict session aborted. The workspace was left unchanged.")
    }

    private suspend fun executeHistory(
        repository: GitRepositoryState,
        operation: String,
        sessionOperation: String,
        approvalId: String?,
        block: (Git) -> GitHistoryResult,
    ): GitHistoryResult = withContext(Dispatchers.IO) {
        val workRoot = File(context.cacheDir, "devforge-git-history/${UUID.randomUUID()}")
        val repoRoot = File(workRoot, "repo")
        var preserve = false
        try {
            copySafWorkspaceToFile(repository.rootUri, repoRoot)
            Git.open(repoRoot).use { git ->
                when (val result = block(git)) {
                    is GitHistoryResult.Success -> {
                        syncWorkspaceBack(repoRoot, repository.rootUri)
                        result
                    }
                    is GitHistoryResult.Conflict -> {
                        val sessionId = UUID.randomUUID().toString()
                        conflictSessions[sessionId] = ActiveConflictSession(
                            sessionId = sessionId,
                            operation = sessionOperation,
                            workRoot = workRoot,
                            repoRoot = repoRoot,
                            workspaceRoot = repository.rootUri,
                            baseHeadRevision = repository.headRevision,
                            approvalId = approvalId,
                            createdAtEpochMs = System.currentTimeMillis(),
                        )
                        preserve = true
                        result.copy(sessionId = sessionId, approvalId = approvalId)
                    }
                    is GitHistoryResult.Failure -> result
                }
            }
        } catch (error: Throwable) {
            GitHistoryResult.Failure("$operation failed: ${sanitizeError(error)}")
        } finally {
            if (!preserve) workRoot.deleteRecursively()
        }
    }

    private suspend fun execute(repository: GitRepositoryState, syncWorktree: Boolean, block: (Git) -> String): GitHistoryResult = withContext(Dispatchers.IO) {
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

    private suspend fun requireWorkspaceUnchanged(session: ActiveConflictSession) {
        val detected = repositoryService.detect(session.workspaceRoot) as? GitDetectionState.Detected
            ?: throw IllegalStateException("The active Git repository could no longer be detected.")
        require(detected.repository.headRevision == session.baseHeadRevision) { "The workspace HEAD changed while the conflict editor was open. Resolution was not applied." }
        val current = statusService.inspect(detected.repository.rootUri, detected.repository.gitDirectoryUri, detected.repository.headRevision)
        require(current.mode == GitStatusAvailability.IndexAndHeadAware && !current.truncated) { "The workspace Git state could not be fully verified. Resolution was not applied." }
        require(current.files.all { it.gitStatus == GitFileStatus.Clean }) { "The workspace changed while the conflict editor was open. Commit or preserve those changes before continuing." }
    }

    private fun handleMergeResult(result: MergeResult, operation: String): GitHistoryResult =
        if (result.mergeStatus.isSuccessful) GitHistoryResult.Success("$operation completed (${result.mergeStatus.name}).")
        else {
            val conflicts = result.conflicts?.keys?.sorted().orEmpty()
            if (conflicts.isNotEmpty()) GitHistoryResult.Conflict(operation, conflicts.take(MAX_CONFLICT_PATHS))
            else GitHistoryResult.Failure("$operation did not complete (${result.mergeStatus.name}). The workspace was not changed.")
        }

    private fun conflictResult(git: Git, operation: String): GitHistoryResult {
        val conflicts = git.status().call().conflicting.sorted().take(MAX_CONFLICT_PATHS)
        return if (conflicts.isNotEmpty()) GitHistoryResult.Conflict(operation, conflicts) else GitHistoryResult.Failure("$operation encountered an unresolved Git state without readable conflict paths.")
    }

    private fun snapshotSession(session: ActiveConflictSession): GitConflictSessionSnapshot {
        Git.open(session.repoRoot).use { git ->
            val paths = git.status().call().conflicting.sorted().take(MAX_CONFLICT_PATHS)
            val files = paths.mapNotNull { readConflictFile(git, session.repoRoot, it) }
            return GitConflictSessionSnapshot(session.sessionId, session.operation, paths, paths.firstOrNull(), files, session.approvalId, session.createdAtEpochMs)
        }
    }

    private fun readConflictFile(git: Git, repoRoot: File, path: String): GitConflictFile {
        normalizePath(path)
        val index = git.repository.readDirCache()
        var ours: BlobContent? = null
        var base: BlobContent? = null
        var theirs: BlobContent? = null
        for (indexPosition in 0 until index.entryCount) {
            val entry = index.getEntry(indexPosition)
            if (entry.pathString != path) continue
            when (entry.stage) {
                1 -> base = readBlobContent(git, entry.objectId)
                2 -> ours = readBlobContent(git, entry.objectId)
                3 -> theirs = readBlobContent(git, entry.objectId)
            }
        }
        val working = readWorkingContent(safeRepoFile(repoRoot, path))
        val values = listOf(ours, base, theirs, working)
        return GitConflictFile(
            path = path,
            ours = ours?.text,
            base = base?.text,
            theirs = theirs?.text,
            merged = working?.text,
            binary = values.any { it?.binary == true },
            oversized = values.any { it?.oversized == true },
        )
    }

    private fun readBlobContent(git: Git, objectId: ObjectId): BlobContent = runCatching {
        git.repository.newObjectReader().use { reader -> reader.open(objectId).openStream().use { input -> toBlobContent(readBounded(input, MAX_CONFLICT_BYTES + 1)) } }
    }.getOrElse { BlobContent(null, false, true) }

    private fun readWorkingContent(file: File): BlobContent? {
        if (!file.exists()) return null
        if (file.isDirectory) return BlobContent(null, true, false)
        return runCatching { file.inputStream().use { input -> toBlobContent(readBounded(input, MAX_CONFLICT_BYTES + 1)) } }.getOrElse { BlobContent(null, false, true) }
    }

    private fun toBlobContent(bytes: ByteArray): BlobContent {
        if (bytes.size > MAX_CONFLICT_BYTES) return BlobContent(null, false, true)
        val text = bytes.toString(Charsets.UTF_8)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        return BlobContent(if (textBytes.contentEquals(bytes)) text else null, !textBytes.contentEquals(bytes), false)
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return ByteArray(maxBytes + 1)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun activeSession(sessionId: String): ActiveConflictSession? {
        val session = conflictSessions[sessionId] ?: return null
        if (!session.workRoot.exists() || System.currentTimeMillis() - session.createdAtEpochMs > MAX_SESSION_AGE_MS) {
            conflictSessions.remove(sessionId)
            session.workRoot.deleteRecursively()
            return null
        }
        return session
    }

    private fun cleanupAbandonedSessionDirectories() {
        val root = File(context.cacheDir, "devforge-git-history")
        val cutoff = System.currentTimeMillis() - MAX_SESSION_AGE_MS
        root.listFiles()?.forEach { child -> if (child.lastModified() < cutoff) child.deleteRecursively() }
    }

    private fun operationLabel(operation: String): String = when (operation) {
        OPERATION_MERGE -> "Merge"
        OPERATION_REBASE -> "Rebase"
        OPERATION_CHERRY_PICK -> "Cherry-pick"
        else -> operation
    }

    private fun requireClean(git: Git) {
        require(git.status().call().isClean) { "This history operation requires a clean working tree. Commit or preserve local changes first." }
    }

    private fun readMergeCommitMessage(repoRoot: File): String? = readSmallControlFile(File(repoRoot, ".git/MERGE_MSG"))?.trim()?.takeIf(String::isNotBlank)?.take(MAX_COMMIT_MESSAGE)

    private fun readCherryPickCommitMessage(git: Git, repoRoot: File): String? {
        val head = readSmallControlFile(File(repoRoot, ".git/CHERRY_PICK_HEAD"))?.trim() ?: return null
        if (!head.matches(SHA_PATTERN)) return null
        return runCatching { git.repository.parseCommit(ObjectId.fromString(head)).fullMessage.trim().take(MAX_COMMIT_MESSAGE) }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun readSmallControlFile(file: File): String? = runCatching {
        if (!file.isFile || file.length() > 8 * 1024L) return@runCatching null
        file.readText(Charsets.UTF_8)
    }.getOrNull()

    private fun normalizePath(path: String): String {
        val normalized = path.replace('\\', '/').trim('/')
        require(normalized.isNotBlank() && normalized != ".") { "Git path is invalid." }
        require(!normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) { "Git path is invalid." }
        require(!normalized.split('/').contains(".git")) { "Git metadata is not a conflict-editable workspace path." }
        return normalized
    }

    private fun safeRepoFile(root: File, relativePath: String): File {
        val normalized = normalizePath(relativePath)
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, normalized).canonicalFile
        require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) { "Git path escapes the isolated repository mirror." }
        return target
    }

    private fun validateBranchName(value: String): String {
        val branch = value.trim()
        require(branch.isNotBlank()) { "Branch name cannot be empty." }
        require(branch.length <= 200) { "Branch name is too long." }
        require(!branch.startsWith('/') && !branch.endsWith('/') && !branch.startsWith('.') && !branch.endsWith('.')) { "Invalid Git branch name." }
        require(!branch.contains("..") && !branch.contains("@{") && !branch.contains(' ')) { "Invalid Git branch name." }
        require(branch.none { it.code < 32 || it == '~' || it == '^' || it == ':' || it == '?' || it == '*' || it == '[' || it == '\\' }) { "Invalid Git branch name." }
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
            listChildren(source).forEach { child -> copySafNode(child.uri, File(target, child.name), budget, if (relativePath.isBlank()) child.name else "$relativePath/${child.name}") }
            return
        }
        if (metadata.size > MAX_FILE_BYTES) throw IOException("History mirror encountered an oversized file: $relativePath")
        budget.consumeFile(metadata.size, relativePath)
        resolver.openInputStream(source)?.use { input -> target.outputStream().use { output -> copyBounded(input, output, metadata.size) } }
            ?: throw IOException("Unable to read workspace file: $relativePath")
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
                resolver.openOutputStream(document, "wt")?.use { output -> item.inputStream().use { input -> copyBounded(input, output, item.length()) } }
                    ?: throw IOException("Unable to write workspace file: $itemPath")
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
        if (input.read() != -1) {
            throw IOException("Workspace file changed while it was being copied.")
        }
    }

    private fun listChildren(parent: Uri): List<DocumentRef> {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        val cursor = resolver.query(
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
        ) ?: throw IOException("Unable to enumerate workspace directory.")

        cursor.use {
            val result = buildList {
                while (it.moveToNext()) {
                    if (size >= MAX_CHILDREN_PER_DIRECTORY) {
                        throw IOException("History operation encountered a directory larger than $MAX_CHILDREN_PER_DIRECTORY entries.")
                    }
                    val id = it.getString(0) ?: continue
                    val name = it.getString(1) ?: continue
                    val mime = it.getString(2).orEmpty()
                    add(
                        DocumentRef(
                            DocumentsContract.buildDocumentUriUsingTree(parent, id),
                            name,
                            mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            it.getLong(3).takeIf { value -> value >= 0L } ?: 0L,
                        ),
                    )
                }
            }
            return result
        }
    }

    private fun queryDocument(uri: Uri): DocumentMetadata? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DocumentMetadata(cursor.getString(0).orEmpty() == DocumentsContract.Document.MIME_TYPE_DIR, cursor.getLong(1).takeIf { it >= 0L } ?: 0L)
        }
    }.getOrNull()

    private fun sanitizeError(error: Throwable): String = error.message?.takeIf(String::isNotBlank)?.take(320) ?: error.javaClass.simpleName

    private data class ActiveConflictSession(
        val sessionId: String,
        val operation: String,
        val workRoot: File,
        val repoRoot: File,
        val workspaceRoot: Uri,
        val baseHeadRevision: String?,
        val approvalId: String?,
        val createdAtEpochMs: Long,
    )

    private data class BlobContent(val text: String?, val binary: Boolean, val oversized: Boolean)
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
        const val OPERATION_MERGE = "merge"
        const val OPERATION_REBASE = "rebase"
        const val OPERATION_CHERRY_PICK = "cherry-pick"
        const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L
        const val MAX_FILES = 5_000
        const val MAX_CHILDREN_PER_DIRECTORY = 1_000
        const val MAX_CONFLICT_PATHS = 50
        const val MAX_CONFLICT_BYTES = 256 * 1024
        const val MAX_COMMIT_MESSAGE = 12_000
        const val MAX_SESSION_AGE_MS = 2L * 60L * 60L * 1000L
        val SHA_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}
