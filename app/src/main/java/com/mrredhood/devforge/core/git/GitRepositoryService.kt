package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitRepositoryService(private val resolver: ContentResolver) {
    suspend fun detect(root: Uri): GitDetectionState = withContext(Dispatchers.IO) {
        val gitDirectory = findDirectChild(root, ".git")
            ?: return@withContext GitDetectionState.NotDetected
        if (!isDirectory(gitDirectory)) {
            return@withContext GitDetectionState.Unsupported("A .git file was found; linked/worktree repositories are not yet readable through the SAF metadata path.")
        }

        val headUri = findDirectChild(gitDirectory, "HEAD")
            ?: return@withContext GitDetectionState.Unsupported("The repository was found, but its HEAD metadata is unavailable.")
        val head = readText(headUri)?.trim()
            ?: return@withContext GitDetectionState.Unsupported("The repository was found, but HEAD could not be read.")

        val detached = !head.startsWith("ref:")
        val branch = if (detached) null else head.removePrefix("ref:").trim().removePrefix("refs/heads/")
        val headRevision = if (detached) head.takeIf { it.matches(Regex("[0-9a-fA-F]{7,64}")) } else null
        val remoteUrl = findDirectChild(gitDirectory, "config")?.let { readText(it) }?.let(::parseOriginUrl)
        val branches = readBranches(gitDirectory, branch)

        GitDetectionState.Detected(
            GitRepositoryState(
                rootUri = root,
                gitDirectoryUri = gitDirectory,
                branchName = branch,
                headRevision = headRevision,
                remoteUrl = remoteUrl,
                detachedHead = detached,
                branches = branches,
            ),
        )
    }

    private fun readBranches(gitDirectory: Uri, currentBranch: String?): List<GitBranch> {
        val result = linkedMapOf<String, GitBranch>()
        val heads = findDirectChild(gitDirectory, "refs")?.let { findDirectChild(it, "heads") }
        if (heads != null) collectLocalBranches(heads, "", currentBranch, result, 0)

        findDirectChild(gitDirectory, "packed-refs")?.let { readText(it, 512 * 1024) }?.lineSequence()?.forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith("^")) return@forEach
            val parts = line.split(' ', limit = 2)
            if (parts.size != 2 || !parts[1].startsWith("refs/heads/")) return@forEach
            val name = parts[1].removePrefix("refs/heads/")
            result.putIfAbsent(name, GitBranch(name, parts[0], name == currentBranch))
        }

        return result.values.sortedWith(compareBy<GitBranch> { !it.isCurrent }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun collectLocalBranches(
        parent: Uri,
        prefix: String,
        currentBranch: String?,
        result: MutableMap<String, GitBranch>,
        depth: Int,
    ) {
        if (depth > MAX_BRANCH_DEPTH || result.size >= MAX_BRANCHES) return
        listChildren(parent).forEach { child ->
            if (child.directory) {
                collectLocalBranches(child.uri, if (prefix.isBlank()) child.name else "$prefix/${child.name}", currentBranch, result, depth + 1)
            } else {
                val name = if (prefix.isBlank()) child.name else "$prefix/${child.name}"
                val revision = readText(child.uri)?.trim()?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,64}")) }
                result.putIfAbsent(name, GitBranch(name, revision, name == currentBranch))
            }
        }
    }

    private data class ChildDocument(val uri: Uri, val name: String, val directory: Boolean)

    private fun listChildren(parent: Uri, maxEntries: Int = 500): List<ChildDocument> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < maxEntries) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    add(
                        ChildDocument(
                            DocumentsContract.buildDocumentUriUsingTree(parent, id),
                            name,
                            mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        ),
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun findDirectChild(parent: Uri, name: String): Uri? =
        listChildren(parent).firstOrNull { it.name == name }?.uri

    private fun isDirectory(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
        } == true
    }.getOrDefault(false)

    private fun readText(uri: Uri, maxBytes: Int = 64 * 1024): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            input.readNBytes(maxBytes + 1).let { bytes ->
                if (bytes.size > maxBytes || bytes.any { it == 0.toByte() }) null
                else bytes.toString(Charsets.UTF_8)
            }
        }
    }.getOrNull()

    private fun parseOriginUrl(config: String): String? {
        var inOrigin = false
        config.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("[remote \"")) {
                inOrigin = line == "[remote \"origin\"]"
            } else if (inOrigin && line.startsWith("url")) {
                return line.substringAfter('=').trim()
            }
        }
        return null
    }

    private companion object {
        const val MAX_BRANCHES = 256
        const val MAX_BRANCH_DEPTH = 8
    }
}
