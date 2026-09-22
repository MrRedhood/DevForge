package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.editor.DiffEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitDiffService(private val resolver: ContentResolver) {
    suspend fun compute(
        root: Uri,
        gitDirectory: Uri,
        headRevision: String?,
        maxFiles: Int = MAX_DIFF_FILES,
    ): List<GitDiffDocument> = withContext(Dispatchers.IO) {
        val status = GitWorkspaceStatusService(resolver).inspect(root, gitDirectory, headRevision, maxFiles = MAX_STATUS_FILES)
        val reader = GitObjectReader(resolver, gitDirectory)
        val diffEngine = DiffEngine()
        val documents = mutableListOf<GitDiffDocument>()

        for (file in status.files) {
            if (documents.size >= maxFiles) break
            if (file.gitStatus == GitFileStatus.Clean || file.gitStatus == GitFileStatus.Unchecked) continue

            val worktree = readWorktreeText(root, file.path)
            val head = readBlobText(reader, file.headObjectId)
            val index = readBlobText(reader, file.indexObjectId)

            val sections = when (file.gitStatus) {
                GitFileStatus.Staged -> listOfNotNull(
                    createSection(diffEngine, "Staged changes", "HEAD", "Index", head, index),
                )
                GitFileStatus.Modified -> listOfNotNull(
                    createSection(diffEngine, "Working-tree changes", if (index != null) "Index" else "HEAD", "Working tree", index ?: head, worktree),
                )
                GitFileStatus.StagedAndModified -> listOfNotNull(
                    createSection(diffEngine, "Staged changes", "HEAD", "Index", head, index),
                    createSection(diffEngine, "Unstaged changes", "Index", "Working tree", index, worktree),
                )
                GitFileStatus.Deleted -> listOfNotNull(
                    createSection(diffEngine, "Deletion", if (index != null) "Index" else "HEAD", "Working tree", index ?: head, ""),
                )
                GitFileStatus.Untracked -> listOfNotNull(
                    createSection(diffEngine, "New file", "Empty", "Working tree", "", worktree),
                )
                GitFileStatus.Conflict -> emptyList()
                GitFileStatus.Clean, GitFileStatus.Unchecked -> emptyList()
            }

            val reason = when {
                file.gitStatus == GitFileStatus.Conflict -> "Conflict entries need resolution before a reliable structured diff can be produced."
                sections.isNotEmpty() -> null
                else -> "The changed file is binary, unreadable, or exceeds the bounded diff text limit."
            }
            documents += GitDiffDocument(file.path, file.gitStatus, sections, reason)
        }
        documents
    }

    private fun createSection(
        diffEngine: DiffEngine,
        title: String,
        beforeLabel: String,
        afterLabel: String,
        before: String?,
        after: String?,
    ): GitDiffSection? {
        if (before == null || after == null) return null
        return GitDiffSection(title, beforeLabel, afterLabel, diffEngine.compare(before, after))
    }

    private fun readBlobText(reader: GitObjectReader, objectId: String?): String? {
        if (objectId.isNullOrBlank()) return null
        return when (val result = reader.read(objectId, MAX_BLOB_BYTES)) {
            is GitObjectResult.Success -> {
                if (result.type != "blob" || result.content.size > MAX_TEXT_BYTES || result.content.any { it == 0.toByte() }) null
                else result.content.toString(Charsets.UTF_8)
            }
            is GitObjectResult.Unavailable -> null
        }
    }

    private fun readWorktreeText(root: Uri, relativePath: String): String? {
        val document = findPath(root, relativePath) ?: return null
        return runCatching {
            resolver.openInputStream(document)?.use { input ->
                val bytes = input.readBytesLimited(MAX_TEXT_BYTES + 1)
                if (bytes.size > MAX_TEXT_BYTES || bytes.any { it == 0.toByte() }) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun findPath(root: Uri, relativePath: String): Uri? {
        var current = root
        for (part in relativePath.split('/').filter(String::isNotBlank)) {
            val child = listChildren(current).firstOrNull { it.name == part } ?: return null
            current = child.uri
        }
        return current
    }

    private fun listChildren(parent: Uri): List<ChildDocument> = runCatching {
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
            buildList {
                while (cursor.moveToNext() && size < MAX_CHILDREN) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    add(ChildDocument(DocumentsContract.buildDocumentUriUsingTree(parent, id), name))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private data class ChildDocument(val uri: Uri, val name: String)

    private companion object {
        const val MAX_DIFF_FILES = 80
        const val MAX_STATUS_FILES = 400
        const val MAX_BLOB_BYTES = 512 * 1024
        const val MAX_TEXT_BYTES = 512 * 1024
        const val MAX_CHILDREN = 256
    }
}

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > maxBytes) return ByteArray(maxBytes + 1)
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
