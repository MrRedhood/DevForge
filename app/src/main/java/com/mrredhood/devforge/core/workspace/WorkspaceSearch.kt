package com.mrredhood.devforge.core.workspace

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class WorkspaceSearch(private val resolver: ContentResolver) {
    suspend fun search(root: Uri, query: String, maxResults: Int = 60): List<WorkspaceSearchResult> =
        withContext(Dispatchers.IO) {
            val boundedResults = maxResults.coerceIn(1, MAX_RESULTS)
            if (query.isBlank()) return@withContext emptyList()
            val results = mutableListOf<WorkspaceSearchResult>()
            val visited = HashSet<String>()
            walk(root, query.trim(), results, boundedResults, visited, 0)
            results
                .sortedWith(compareBy<WorkspaceSearchResult> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

    private fun walk(
        parent: Uri,
        query: String,
        results: MutableList<WorkspaceSearchResult>,
        maxResults: Int,
        visited: MutableSet<String>,
        depth: Int,
    ) {
        if (results.size >= maxResults || depth >= MAX_DEPTH) return
        if (!visited.add(parent.toString())) return
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
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext() && results.size < maxResults) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex) ?: "Unnamed"
                val mime = cursor.getString(mimeIndex)
                val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent, id)
                if (name.contains(query, ignoreCase = true)) {
                    results += WorkspaceSearchResult(uri, name, directory, size)
                }
                if (directory) walk(uri, query, results, maxResults, visited, depth + 1)
            }
        }
    }
}

data class WorkspaceSearchResult(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

    companion object {
        private const val MAX_RESULTS = 200
        private const val MAX_DEPTH = 32
    }
}

object FilePreviewPolicy {
    const val MAX_PREVIEW_BYTES = 512L * 1024L

    fun allowsPreview(sizeBytes: Long?, mimeType: String?): Boolean {
        if (sizeBytes != null && sizeBytes > MAX_PREVIEW_BYTES) return false
        if (mimeType == null) return true
        if (mimeType.startsWith("text/")) return true
        return mimeType in setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-javascript",
            "application/x-sh",
            "application/yaml",
            "application/x-yaml",
        )
    }

    fun looksBinary(sample: ByteArray): Boolean = sample.any { it == 0.toByte() }

    fun decode(sample: ByteArray): String = sample.toString(StandardCharsets.UTF_8)
}
