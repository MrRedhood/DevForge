package com.mrredhood.devforge.core.workspace

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

class WorkspaceFileTree(private val resolver: ContentResolver) {
    fun listRoot(root: Uri, maxEntries: Int = 200): List<WorkspaceEntry> = list(root, maxEntries)

    fun list(parent: Uri, maxEntries: Int = 200): List<WorkspaceEntry> =
        listChildren(parent, maxEntries.coerceAtLeast(0))
            .sortedWith(compareBy<WorkspaceEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private fun listChildren(parent: Uri, maxEntries: Int): List<WorkspaceEntry> {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        val results = mutableListOf<WorkspaceEntry>()
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
            if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) return@use
            while (cursor.moveToNext() && results.size < maxEntries) {
                val id = cursor.getString(idIndex)
                if (id.isNullOrBlank()) continue
                val name = cursor.getString(nameIndex) ?: "Unnamed"
                val mime = cursor.getString(mimeIndex)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent, id)
                results += WorkspaceEntry(uri, name, isDirectory, size)
            }
        }
        return results
    }
}
