package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.editor.ContentHasher
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reports workspace drift that DevForge can prove through the SAF tree.
 * This is intentionally not presented as native Git status until an index-aware
 * Git operation layer is available.
 */
class GitWorkspaceStatusService(private val resolver: ContentResolver) {
    suspend fun inspect(root: Uri, maxFiles: Int = 300): GitWorkspaceStatus = withContext(Dispatchers.IO) {
        val files = mutableListOf<GitWorkspaceFile>()
        walk(root, "", files, maxFiles, 0)
        GitWorkspaceStatus(
            state = if (files.any { it.readError }) GitStatusConfidence.Partial else GitStatusConfidence.Observed,
            files = files,
            truncated = files.size >= maxFiles,
        )
    }

    private fun walk(
        parent: Uri,
        relativePrefix: String,
        files: MutableList<GitWorkspaceFile>,
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
                val digest = if (size != null && size <= MAX_HASH_BYTES) readHash(child.uri) else null
                files += GitWorkspaceFile(
                    path = relativePath,
                    sizeBytes = size,
                    contentHash = digest,
                    readError = size != null && size <= MAX_HASH_BYTES && digest == null,
                )
            }
        }
    }

    private fun readHash(uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            var remaining = MAX_HASH_BYTES
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
            if (remaining > 0 && resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L > MAX_HASH_BYTES) return@use null
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

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
        const val MAX_HASH_BYTES = 2L * 1024L * 1024L
    }
}

enum class GitStatusConfidence { Observed, Partial }

data class GitWorkspaceStatus(
    val state: GitStatusConfidence,
    val files: List<GitWorkspaceFile>,
    val truncated: Boolean,
)

data class GitWorkspaceFile(
    val path: String,
    val sizeBytes: Long?,
    val contentHash: String?,
    val readError: Boolean,
)
