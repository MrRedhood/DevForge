package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.github.GitHubConnectionViewModel
import com.mrredhood.devforge.core.security.AndroidSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.RefSpec
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID

sealed interface GitRemoteResult {
    data class Success(val message: String) : GitRemoteResult
    data class Failure(val message: String) : GitRemoteResult
}

data class GitRemoteValidation(
    val available: Boolean,
    val owner: String? = null,
    val repository: String? = null,
    val reason: String? = null,
)

/**
 * Bounded HTTPS Git transport for GitHub remotes.
 *
 * SAF remains the canonical workspace boundary. JGit operates only inside a private,
 * ephemeral cache mirror. Credentials are read just-in-time from Android Keystore and
 * never written into action payloads, the mirror, Git config, or receipts.
 */
class GitRemoteTransportService(
    private val context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) {
    private val secretStore = AndroidSecretStore(context)

    fun validateConfigured(remoteUrl: String?): GitRemoteValidation {
        if (remoteUrl.isNullOrBlank()) return GitRemoteValidation(false, reason = "No origin remote is configured.")
        val remote = parseGitHubRemote(remoteUrl)
            ?: return GitRemoteValidation(false, reason = "Only HTTPS GitHub remotes are supported by the safe transport layer.")
        if (secretStore.get(GitHubConnectionViewModel.TOKEN_KEY).isNullOrBlank()) {
            return GitRemoteValidation(false, remote.owner, remote.repository, "Connect GitHub before using remote Git operations.")
        }
        return GitRemoteValidation(true, remote.owner, remote.repository)
    }

    suspend fun fetch(repository: GitRepositoryState): GitRemoteResult = withContext(Dispatchers.IO) {
        execute(repository, syncWorktree = false) { git, credentials ->
            validateRemoteAndCredentials(repository.remoteUrl, credentials)
            val branch = repository.branchName
                ?: throw IllegalStateException("Fetch requires an attached local branch.")
            val refSpec = RefSpec(
                "+refs/heads/$branch:refs/remotes/origin/$branch",
            )
            val result = git.fetch()
                .setRemote("origin")
                .setRefSpecs(refSpec)
                .setCredentialsProvider(credentials)
                .setRemoveDeletedRefs(false)
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()
            val changed = result.advertisedRefs.count { it.name == "refs/heads/$branch" }
            "Fetched origin/$branch${if (changed > 0) " and updated remote metadata" else "; remote metadata is unchanged"}."
        }
    }

    suspend fun pull(repository: GitRepositoryState): GitRemoteResult = withContext(Dispatchers.IO) {
        execute(repository, syncWorktree = true) { git, credentials ->
            validateRemoteAndCredentials(repository.remoteUrl, credentials)
            require(repository.branchName != null) { "Pull requires an attached local branch." }
            val status = git.status().call()
            if (!status.isClean) {
                throw IllegalStateException("Pull is blocked while the working tree has local changes. Commit or discard them first.")
            }

            val result = git.pull()
                .setRemote("origin")
                .setRemoteBranchName(repository.branchName)
                .setCredentialsProvider(credentials)
                .setFastForwardMode(org.eclipse.jgit.merge.MergeStrategy.RECURSIVE.defaultFastForwardMode())
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()

            if (result.mergeResult?.mergeStatus?.isSuccessful != true) {
                val statusText = result.mergeResult?.mergeStatus?.name ?: "unknown"
                throw IllegalStateException("Pull did not complete as a fast-forward operation (status: $statusText). No merge conflict was applied by DevForge.")
            }
            if (result.newHead == null || result.newHead.name == repository.headRevision) {
                "Already up to date with origin/${repository.branchName}."
            } else {
                "Pulled origin/${repository.branchName} to ${result.newHead.name.take(12)}."
            }
        }
    }

    suspend fun push(repository: GitRepositoryState): GitRemoteResult = withContext(Dispatchers.IO) {
        execute(repository, syncWorktree = false) { git, credentials ->
            validateRemoteAndCredentials(repository.remoteUrl, credentials)
            val branch = repository.branchName
                ?: throw IllegalStateException("Push requires an attached local branch.")
            val currentHead = git.repository.resolve("HEAD")?.name
                ?: throw IllegalStateException("A valid local HEAD is required before pushing.")
            val pushSpec = RefSpec("refs/heads/$branch:refs/heads/$branch")
            val results = git.push()
                .setRemote("origin")
                .setRefSpecs(pushSpec)
                .setCredentialsProvider(credentials)
                .setForce(false)
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()
            val rejection = results.asSequence()
                .flatMap { it.remoteUpdates.asSequence() }
                .firstOrNull { update -> update.status.name.contains("REJECTED", ignoreCase = true) || update.status.name.contains("NON_FAST_FORWARD", ignoreCase = true) }
            if (rejection != null) {
                throw IllegalStateException("Push was rejected by the remote (${rejection.status.name}). DevForge never force-pushes remote branches.")
            }
            "Pushed $branch (${currentHead.take(12)}) to origin."
        }
    }

    private suspend fun execute(
        repository: GitRepositoryState,
        syncWorktree: Boolean,
        block: (Git, CredentialsProvider) -> String,
    ): GitRemoteResult {
        val validation = validateConfigured(repository.remoteUrl)
        if (!validation.available) return GitRemoteResult.Failure(validation.reason ?: "Remote transport is unavailable.")

        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: return GitRemoteResult.Failure("GitHub is not connected on this device.")
        val credentials = UsernamePasswordCredentialsProvider("x-access-token", token)
        val workRoot = File(context.cacheDir, "devforge-git-transport/${UUID.randomUUID()}")
        val repoRoot = File(workRoot, "repo")

        return try {
            copySafWorkspaceToFile(repository.rootUri, repoRoot)
            ensureOriginRemote(repoRoot, repository.remoteUrl!!)
            Git.open(repoRoot).use { git ->
                val message = block(git, credentials)
                syncFileWorkspaceBack(
                    sourceRoot = repoRoot,
                    targetRoot = repository.rootUri,
                    includeWorktree = syncWorktree,
                )
                GitRemoteResult.Success(message)
            }
        } catch (error: Throwable) {
            GitRemoteResult.Failure(sanitizeError(error))
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private fun validateRemoteAndCredentials(remoteUrl: String?, credentials: CredentialsProvider) {
        val remote = parseGitHubRemote(remoteUrl)
            ?: throw IllegalStateException("Only HTTPS GitHub remotes are supported by DevForge's safe remote transport.")
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
            ?: throw IllegalStateException("GitHub credentials are unavailable.")
        if (token.length > MAX_TOKEN_LENGTH) throw IllegalStateException("The stored GitHub credential is invalid.")
        // lsRemote is intentionally required before every state-changing transport operation.
        val temp = File(context.cacheDir, "devforge-git-validate-${UUID.randomUUID()}")
        try {
            val repository = org.eclipse.jgit.lib.RepositoryBuilder()
                .setGitDir(File(temp, ".git"))
                .setBare()
                .build()
            repository.use {
                Git(it).lsRemote()
                    .setRemote("https://github.com/${remote.owner}/${remote.repository}.git")
                    .setCredentialsProvider(credentials)
                    .setHeads(true)
                    .setTags(false)
                    .setTimeout(NETWORK_TIMEOUT_SECONDS)
                    .call()
            }
        } catch (error: Throwable) {
            throw IllegalStateException("GitHub remote validation failed: ${sanitizeError(error)}")
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun ensureOriginRemote(repoRoot: File, remoteUrl: String) {
        val gitDir = File(repoRoot, ".git")
        val config = File(gitDir, "config")
        if (!config.exists()) throw IOException("The mirrored Git repository has no .git/config.")
        val normalized = parseGitHubRemote(remoteUrl)
            ?: throw IllegalStateException("Origin remote is outside the supported HTTPS GitHub scope.")
        val safeUrl = "https://github.com/${normalized.owner}/${normalized.repository}.git"
        Git.open(repoRoot).use { git ->
            val existing = git.remoteList().call().firstOrNull { it.name == "origin" }
            if (existing == null || existing.urIs.singleOrNull()?.toString() != safeUrl) {
                git.remoteSetUrl().setRemoteName("origin").setRemoteUri(org.eclipse.jgit.transport.URIish(safeUrl)).call()
            }
        }
    }

    private fun copySafWorkspaceToFile(sourceRoot: Uri, targetRoot: File) {
        targetRoot.mkdirs()
        val budget = CopyBudget()
        copySafNode(sourceRoot, targetRoot, budget, relativePath = "")
    }

    private fun copySafNode(source: Uri, target: File, budget: CopyBudget, relativePath: String) {
        val type = queryDocument(source) ?: throw IOException("Unable to inspect workspace document.")
        if (type.isDirectory) {
            if (relativePath == ".git") {
                // Git metadata must be mirrored exactly; it is still bounded by the same global budget.
            }
            target.mkdirs()
            listChildren(source).forEach { child ->
                if (child.name == ".git" || !relativePath.startsWith(".git") || relativePath.isNotBlank()) {
                    copySafNode(child.uri, File(target, child.name), budget, if (relativePath.isBlank()) child.name else "$relativePath/${child.name}")
                }
            }
            return
        }
        val length = type.size
        if (length > MAX_FILE_BYTES) throw IOException("Remote transport mirror encountered an oversized file: $relativePath")
        budget.consumeFile(length, relativePath)
        resolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output ->
                copyBounded(input, output, length)
            }
        } ?: throw IOException("Unable to read workspace file: $relativePath")
    }

    private fun syncFileWorkspaceBack(sourceRoot: File, targetRoot: Uri, includeWorktree: Boolean) {
        val budget = CopyBudget()
        val sourceGit = File(sourceRoot, ".git")
        val targetGit = findDirectChild(targetRoot, ".git") ?: throw IOException("The selected workspace lost its .git directory during transport.")
        syncDirectoryFromFile(sourceGit, targetGit, budget, ".git")
        if (!includeWorktree) return
        syncDirectoryFromFile(sourceRoot, targetRoot, budget, "") { name -> name == ".git" }
    }

    private fun syncDirectoryFromFile(
        source: File,
        target: Uri,
        budget: CopyBudget,
        relativePath: String,
        skip: (String) -> Boolean = { false },
    ) {
        if (!source.exists() || !source.isDirectory) throw IOException("Transport output directory is missing: $relativePath")
        val targetChildren = listChildren(target).associateBy { it.name }.toMutableMap()
        source.listFiles()?.sortedBy { it.name }?.forEach { item ->
            if (skip(item.name)) return@forEach
            val itemPath = if (relativePath.isBlank()) item.name else "$relativePath/${item.name}"
            val existing = targetChildren.remove(item.name)?.uri
            if (item.isDirectory) {
                val directory = existing ?: DocumentsContract.createDocument(resolver, target, DocumentsContract.Document.MIME_TYPE_DIR, item.name)
                    ?: throw IOException("Unable to create workspace directory: $itemPath")
                syncDirectoryFromFile(item, directory, budget, itemPath, skip)
            } else {
                val bytes = item.length()
                if (bytes > MAX_FILE_BYTES) throw IOException("Transport output contains an oversized file: $itemPath")
                budget.consumeFile(bytes, itemPath)
                val document = existing ?: DocumentsContract.createDocument(resolver, target, "application/octet-stream", item.name)
                    ?: throw IOException("Unable to create workspace file: $itemPath")
                resolver.openOutputStream(document, "wt")?.use { output ->
                    item.inputStream().use { input -> copyBounded(input, output, bytes) }
                } ?: throw IOException("Unable to write workspace file: $itemPath")
            }
        }
        targetChildren.values.forEach { orphan ->
            if (!skip(orphan.name)) DocumentsContract.deleteDocument(resolver, orphan.uri)
        }
    }

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (copied < expectedBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedBytes - copied).toInt())
            if (read <= 0) throw IOException("Unexpected end of workspace file while mirroring Git transport state.")
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
                    add(DocumentRef(
                        uri = DocumentsContract.buildDocumentUriUsingTree(parent, id),
                        name = name,
                        directory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = cursor.getLong(3).takeIf { it >= 0L } ?: 0L,
                    ))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun queryDocument(uri: Uri): DocumentMetadata? = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val mime = cursor.getString(0).orEmpty()
            DocumentMetadata(mime == DocumentsContract.Document.MIME_TYPE_DIR, cursor.getLong(1).takeIf { it >= 0L } ?: 0L)
        }
    }.getOrNull()

    private fun findDirectChild(parent: Uri, name: String): Uri? = listChildren(parent).firstOrNull { it.name == name }?.uri

    private fun parseGitHubRemote(value: String?): ParsedRemote? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank() || raw.any { it == '\n' || it == '\r' }) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https" || !uri.host.equals("github.com", ignoreCase = true)) return null
        if (!uri.userInfo.isNullOrBlank() || !uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank()) return null
        val pieces = uri.path.trim('/').split('/').filter(String::isNotBlank)
        if (pieces.size != 2) return null
        val owner = pieces[0]
        val repository = pieces[1].removeSuffix(".git")
        if (!VALID_NAME.matches(owner) || !VALID_NAME.matches(repository)) return null
        return ParsedRemote(owner, repository)
    }

    private fun sanitizeError(error: Throwable): String = error.message
        ?.replace(Regex("(?i)(password|token|authorization|credential)\\s*[:=]\\s*[^,}\\s]+"), "$1=[redacted]")
        ?.take(320)
        ?.ifBlank { null }
        ?: error.javaClass.simpleName

    private data class ParsedRemote(val owner: String, val repository: String)
    private data class DocumentRef(val uri: Uri, val name: String, val directory: Boolean, val size: Long)
    private data class DocumentMetadata(val isDirectory: Boolean, val size: Long)

    private class CopyBudget {
        private var bytes = 0L
        private var files = 0

        fun consumeFile(size: Long, path: String) {
            if (size < 0L) throw IOException("Invalid size for $path")
            files += 1
            bytes += size
            if (files > MAX_MIRROR_FILES) throw IOException("Remote transport mirror exceeded the file safety limit.")
            if (bytes > MAX_MIRROR_BYTES) throw IOException("Remote transport mirror exceeded the storage safety limit.")
        }
    }

    companion object {
        private const val NETWORK_TIMEOUT_SECONDS = 30
        private const val MAX_MIRROR_FILES = 8_000
        private const val MAX_MIRROR_BYTES = 256L * 1024L * 1024L
        private const val MAX_FILE_BYTES = 64L * 1024L * 1024L
        private const val MAX_CHILDREN_PER_DIRECTORY = 1_000
        private const val MAX_TOKEN_LENGTH = 512
        private val VALID_NAME = Regex("^[A-Za-z0-9_.-]+$")
    }
}
