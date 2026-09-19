package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.github.GitHubConnectionViewModel
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.transport.RefSpec
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

class GitRemoteTransportService(
    private val context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) {
    private val secretStore = CredentialSecurityStore(context)

    fun validateConfigured(remoteUrl: String?): GitRemoteValidation {
        if (remoteUrl.isNullOrBlank()) return GitRemoteValidation(false, reason = "No origin remote is configured.")
        val remote = parseGitHubRemote(remoteUrl)
            ?: return GitRemoteValidation(false, reason = "Only HTTPS GitHub remotes are supported by the safe transport layer.")
        if (secretStore.get(GitHubConnectionViewModel.TOKEN_KEY).isNullOrBlank()) {
            return GitRemoteValidation(false, remote.owner, remote.repository, "Connect GitHub before using remote Git operations.")
        }
        return GitRemoteValidation(true, remote.owner, remote.repository)
    }

    suspend fun cloneRepositoryInto(
        parentUri: Uri,
        owner: String,
        repository: String,
        branch: String,
        targetName: String = repository,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            require(isDirectoryUri(parentUri)) { "The selected destination is not a directory." }
            val normalizedOwner = owner.trim().takeIf { VALID_NAME.matches(it) }
                ?: throw IllegalArgumentException("The GitHub owner is invalid.")
            val normalizedRepository = repository.trim().takeIf { VALID_NAME.matches(it) }
                ?: throw IllegalArgumentException("The GitHub repository is invalid.")
            val normalizedBranch = branch.trim()
            require(
                normalizedBranch.isNotBlank() &&
                    normalizedBranch.length <= 255 &&
                    !normalizedBranch.startsWith("/") &&
                    !normalizedBranch.contains("..") &&
                    !normalizedBranch.any { it == '\\' || it == '\n' || it == '\r' },
            ) { "The GitHub branch is invalid." }

            val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY)
                ?: throw IllegalStateException("Connect GitHub before importing a repository.")
            require(token.length <= MAX_TOKEN_LENGTH) { "The stored GitHub credential is invalid." }

            val safeTarget = requireSafeDocumentName(targetName.ifBlank { normalizedRepository })
            val targetRoot = createUniqueDirectory(parentUri, safeTarget)

            val tempRoot = File(context.cacheDir, "devforge-git-clone/" + UUID.randomUUID())
            tempRoot.mkdirs()
            val localRepo = File(tempRoot, "repo")
            try {
                Git.cloneRepository()
                    .setURI("https://github.com/" + normalizedOwner + "/" + normalizedRepository + ".git")
                    .setDirectory(localRepo)
                    .setBranch(normalizedBranch)
                    .setBranchesToClone(listOf("refs/heads/" + normalizedBranch))
                    .setDepth(1)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider("x-access-token", token))
                    .setTimeout(NETWORK_TIMEOUT_SECONDS)
                    .call()
                    .use { }

                syncDirectoryFromFile(
                    source = localRepo,
                    target = targetRoot,
                    budget = CopyBudget(),
                )
            } finally {
                tempRoot.deleteRecursively()
            }
            targetRoot
        }
    }

    suspend fun autoSyncChanges(
        repository: GitRepositoryState,
        commitMessage: String,
    ): GitRemoteResult = withContext(Dispatchers.IO) {
        execute(repository, syncWorktree = true) { git, credentials ->
            validateRemoteAndCredentials(repository.remoteUrl, credentials)
            val status = git.status().call()
            if (status.isClean) return@execute "No remote changes were needed."

            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()

            val staged = git.status().call()
            if (staged.isClean) return@execute "No remote changes were needed."

            val message = commitMessage.trim().take(200).ifBlank { "DevForge: synchronize workspace changes" }
            val commit = git.commit().setMessage(message).call()
            val branch = repository.branchName
                ?: throw IllegalStateException("Automatic GitHub sync requires an attached branch.")
            val pushSpec = RefSpec("refs/heads/" + branch + ":refs/heads/" + branch)
            val results = git.push()
                .setRemote("origin")
                .setRefSpecs(pushSpec)
                .setCredentialsProvider(credentials)
                .setForce(false)
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()
            val rejection = results.asSequence()
                .flatMap { it.getRemoteUpdates().asSequence() }
                .firstOrNull { update ->
                    val statusName = update.getStatus().name
                    statusName.contains("REJECTED", true) ||
                        statusName.contains("NON_FAST_FORWARD", true) ||
                        statusName.contains("NON_EXISTING", true)
                }
            if (rejection != null) {
                throw IllegalStateException(
                    "GitHub rejected automatic sync (" + rejection.getStatus().name + "). DevForge never force-pushes.",
                )
            }
            "Synced workspace to GitHub in commit " + commit.id.name.take(12) + "."
        }
    }
    suspend fun fetch(repository: GitRepositoryState): GitRemoteResult = withContext(Dispatchers.IO) {
        execute(repository, syncWorktree = false) { git, credentials ->
            validateRemoteAndCredentials(repository.remoteUrl, credentials)
            val branch = repository.branchName ?: throw IllegalStateException("Fetch requires an attached local branch.")
            val refSpec = RefSpec("+refs/heads/$branch:refs/remotes/origin/$branch")
            val result = git.fetch()
                .setRemote("origin")
                .setRefSpecs(refSpec)
                .setCredentialsProvider(credentials)
                .setRemoveDeletedRefs(false)
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()
            val advertised = result.getAdvertisedRefs().any { it.getName() == "refs/heads/$branch" }
            "Fetched origin/$branch${if (advertised) " and refreshed remote metadata" else "; branch is not advertised by the remote"}."
        }
    }

    suspend fun pull(repository: GitRepositoryState): GitRemoteResult = withContext(Dispatchers.IO) {
        execute(repository, syncWorktree = true) { git, credentials ->
            validateRemoteAndCredentials(repository.remoteUrl, credentials)
            val branch = repository.branchName ?: throw IllegalStateException("Pull requires an attached local branch.")
            if (!git.status().call().isClean) {
                throw IllegalStateException("Pull is blocked while the working tree has local changes. Commit or discard them first.")
            }

            val refSpec = RefSpec("+refs/heads/$branch:refs/remotes/origin/$branch")
            git.fetch()
                .setRemote("origin")
                .setRefSpecs(refSpec)
                .setCredentialsProvider(credentials)
                .setRemoveDeletedRefs(false)
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()

            val remoteRef = git.repository.findRef("refs/remotes/origin/$branch")
                ?: throw IllegalStateException("Remote branch origin/$branch was not advertised by GitHub.")
            val merge = git.merge()
                .include(remoteRef.objectId)
                .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                .call()
            if (!merge.mergeStatus.isSuccessful) {
                throw IllegalStateException("Pull did not complete as a fast-forward operation (status: ${merge.mergeStatus.name}). No merge conflict was applied by DevForge.")
            }
            val newHead = merge.newHead?.name
            if (newHead.isNullOrBlank() || newHead == repository.headRevision) {
                "Already up to date with origin/$branch."
            } else {
                "Pulled origin/$branch to ${newHead.take(12)}."
            }
        }
    }

    suspend fun push(repository: GitRepositoryState): GitRemoteResult = withContext(Dispatchers.IO) {
        execute(repository, syncWorktree = false) { git, credentials ->
            validateRemoteAndCredentials(repository.remoteUrl, credentials)
            val branch = repository.branchName ?: throw IllegalStateException("Push requires an attached local branch.")
            val currentHead = git.getRepository().resolve("HEAD")?.name ?: throw IllegalStateException("A valid local HEAD is required before pushing.")
            val pushSpec = RefSpec("refs/heads/$branch:refs/heads/$branch")
            val results = git.push()
                .setRemote("origin")
                .setRefSpecs(pushSpec)
                .setCredentialsProvider(credentials)
                .setForce(false)
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()
            val rejection = results.asSequence()
                .flatMap { it.getRemoteUpdates().asSequence() }
                .firstOrNull { update ->
                    val status = update.getStatus().name
                    status.contains("REJECTED", ignoreCase = true) || status.contains("NON_FAST_FORWARD", ignoreCase = true)
                }
            if (rejection != null) {
                throw IllegalStateException("Push was rejected by the remote (${rejection.getStatus().name}). DevForge never force-pushes remote branches.")
            }
            "Pushed $branch (${currentHead.take(12)}) to origin."
        }
    }

    private suspend fun execute(
        repository: GitRepositoryState,
        syncWorktree: Boolean,
        block: (Git, CredentialsProvider) -> String,
    ): GitRemoteResult {
        val remoteUrl = repository.remoteUrl
            ?: return GitRemoteResult.Failure("The repository has no configured remote URL.")
        val validation = validateConfigured(remoteUrl)
        if (!validation.available) return GitRemoteResult.Failure(validation.reason ?: "Remote transport is unavailable.")
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) ?: return GitRemoteResult.Failure("GitHub is not connected on this device.")
        val credentials = UsernamePasswordCredentialsProvider("x-access-token", token)
        val workRoot = File(context.cacheDir, "devforge-git-transport/${UUID.randomUUID()}")
        val repoRoot = File(workRoot, "repo")
        return try {
            copySafWorkspaceToFile(repository.rootUri, repoRoot)
            ensureOriginRemote(repoRoot, remoteUrl)
            Git.open(repoRoot).use { git ->
                val message = block(git, credentials)
                syncFileWorkspaceBack(sourceRoot = repoRoot, targetRoot = repository.rootUri, includeWorktree = syncWorktree)
                GitRemoteResult.Success(message)
            }
        } catch (error: Throwable) {
            GitRemoteResult.Failure(sanitizeError(error))
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private fun validateRemoteAndCredentials(remoteUrl: String?, credentials: CredentialsProvider) {
        val remote = parseGitHubRemote(remoteUrl) ?: throw IllegalStateException("Only HTTPS GitHub remotes are supported by DevForge's safe remote transport.")
        val token = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) ?: throw IllegalStateException("GitHub credentials are unavailable.")
        if (token.length > MAX_TOKEN_LENGTH) throw IllegalStateException("The stored GitHub credential is invalid.")
        try {
            Git.lsRemoteRepository()
                .setRemote("https://github.com/${remote.owner}/${remote.repository}.git")
                .setCredentialsProvider(credentials)
                .setHeads(true)
                .setTags(false)
                .setTimeout(NETWORK_TIMEOUT_SECONDS)
                .call()
        } catch (error: Throwable) {
            throw IllegalStateException("GitHub remote validation failed: ${sanitizeError(error)}")
        }
    }

    private fun ensureOriginRemote(repoRoot: File, remoteUrl: String) {
        val config = File(File(repoRoot, ".git"), "config")
        if (!config.exists()) throw IOException("The mirrored Git repository has no .git/config.")
        val normalized = parseGitHubRemote(remoteUrl) ?: throw IllegalStateException("Origin remote is outside the supported HTTPS GitHub scope.")
        val expected = "https://github.com/${normalized.owner}/${normalized.repository}.git"
        Git.open(repoRoot).use { git ->
            val existing = git.remoteList().call().firstOrNull { it.getName() == "origin" } ?: throw IllegalStateException("The local repository has no origin remote.")
            val configured = existing.getURIs().singleOrNull()?.toString() ?: throw IllegalStateException("The origin remote must have exactly one configured URL.")
            if (configured != expected) throw IllegalStateException("The mirrored origin remote does not match the validated GitHub remote.")
        }
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
                val safeName = requireSafeDocumentName(child.name)
                val childPath = if (relativePath.isBlank()) safeName else relativePath + "/" + safeName
                copySafNode(child.uri, File(target, safeName), budget, childPath)
            }
            return
        }
        val length = metadata.size
        if (length > MAX_FILE_BYTES) throw IOException("Remote transport mirror encountered an oversized file: $relativePath")
        budget.consumeFile(length, relativePath)
        resolver.openInputStream(source)?.use { input -> target.outputStream().use { output -> copyBounded(input, output, length) } }
            ?: throw IOException("Unable to read workspace file: $relativePath")
    }

    private fun syncFileWorkspaceBack(sourceRoot: File, targetRoot: Uri, includeWorktree: Boolean) {
        val budget = CopyBudget()
        val sourceGit = File(sourceRoot, ".git")
        val targetGit = findDirectChild(targetRoot, ".git") ?: throw IOException("The selected workspace lost its .git directory during transport.")
        syncDirectoryFromFile(sourceGit, targetGit, budget, ".git")
        if (!includeWorktree) return
        syncDirectoryFromFile(sourceRoot, targetRoot, budget, "") { it == ".git" }
    }

    private fun syncDirectoryFromFile(source: File, target: Uri, budget: CopyBudget, relativePath: String, skip: (String) -> Boolean = { false }) {
        if (!source.exists() || !source.isDirectory) throw IOException("Transport output directory is missing: $relativePath")
        val targetChildren = listChildren(target).associateBy { it.name }.toMutableMap()
        source.listFiles()?.sortedBy { it.name }?.forEach { item ->
            if (skip(item.name)) return@forEach
            val itemPath = if (relativePath.isBlank()) item.name else "$relativePath/${item.name}"
            val existing = targetChildren.remove(item.name)?.uri
            if (item.isDirectory) {
                val directory = existing ?: DocumentsContract.createDocument(resolver, target, DocumentsContract.Document.MIME_TYPE_DIR, item.name) ?: throw IOException("Unable to create workspace directory: $itemPath")
                syncDirectoryFromFile(item, directory, budget, itemPath, skip)
            } else {
                val bytes = item.length()
                if (bytes > MAX_FILE_BYTES) throw IOException("Transport output contains an oversized file: $itemPath")
                budget.consumeFile(bytes, itemPath)
                val document = existing ?: DocumentsContract.createDocument(resolver, target, "application/octet-stream", item.name) ?: throw IOException("Unable to create workspace file: $itemPath")
                resolver.openOutputStream(document, "wt")?.use { output -> item.inputStream().use { input -> copyBounded(input, output, bytes) } }
                    ?: throw IOException("Unable to write workspace file: $itemPath")
            }
        }
        targetChildren.values.forEach { orphan -> if (!skip(orphan.name)) DocumentsContract.deleteDocument(resolver, orphan.uri) }
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
        if (input.read() != -1) {
            throw IOException("Workspace file changed while it was being mirrored.")
        }
    }

    private fun documentParentUri(parent: Uri): Uri {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(parent) }.getOrNull()
        return treeDocumentId?.let { DocumentsContract.buildDocumentUriUsingTree(parent, it) } ?: parent
    }
    private fun listChildren(parent: Uri): List<DocumentRef> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
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

    private fun findDirectChild(parent: Uri, name: String): Uri? = listChildren(parent).firstOrNull { it.name == name }?.uri

    private fun isDirectoryUri(uri: Uri): Boolean = queryDocument(uri)?.isDirectory == true

    private fun createUniqueDirectory(parent: Uri, baseName: String): Uri {
        val parentDocument = documentParentUri(parent)
        val existing = listChildren(parentDocument).map { it.name }.toSet()
        var candidate = baseName.take(255)
        var suffix = 2
        while (candidate in existing) {
            val suffixText = " (" + suffix + ")"
            candidate = baseName.take((255 - suffixText.length).coerceAtLeast(1)) + suffixText
            suffix++
            require(suffix < 10_000) { "Unable to choose a unique workspace directory." }
        }
        return DocumentsContract.createDocument(
            resolver,
            parentDocument,
            DocumentsContract.Document.MIME_TYPE_DIR,
            candidate,
        ) ?: throw IOException("Unable to create repository destination folder.")
    }
    private fun requireSafeDocumentName(name: String): String {
        val value = name.trim()
        require(
            value.isNotBlank() &&
                value != "." &&
                value != ".." &&
                "/" !in value &&
                "\\" !in value &&
                "\u0000" !in value,
        ) { "Workspace contains an unsafe document name: " + name }
        return value.take(255)
    }

    private fun parseGitHubRemote(value: String?): ParsedRemote? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank() || raw.any { it == '\n' || it == '\r' }) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || !uri.host.equals("github.com", ignoreCase = true)) return null
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
