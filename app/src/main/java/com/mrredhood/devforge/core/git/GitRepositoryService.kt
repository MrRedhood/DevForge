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

        GitDetectionState.Detected(
            GitRepositoryState(
                rootUri = root,
                gitDirectoryUri = gitDirectory,
                branchName = branch,
                headRevision = headRevision,
                remoteUrl = remoteUrl,
                detachedHead = detached,
            ),
        )
    }

    private fun findDirectChild(parent: Uri, name: String): Uri? =
        runCatching {
            val documentId = runCatching { DocumentsContract.getDocumentId(parent) }
                .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: continue
                    if (displayName == name) return@use DocumentsContract.buildDocumentUriUsingTree(parent, id)
                }
            }
            null
        }.getOrNull()

    private fun isDirectory(uri: Uri): Boolean =
        runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
            } == true
        }.getOrDefault(false)

    private fun readText(uri: Uri, maxBytes: Int = 64 * 1024): String? =
        runCatching {
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
                return@forEach
            }
            if (inOrigin && line.startsWith("url")) return line.substringAfter('=').trim()
        }
        return null
    }
}
