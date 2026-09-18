package com.mrredhood.devforge.core.workspace

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.security.WorkspacePathScope
import java.io.IOException

class WorkspaceFileOperations(private val resolver: ContentResolver) {
    fun createFile(parent: Uri, name: String, mimeType: String = guessMime(name)): Uri {
        val clean = safeName(name, "File name")
        require(clean != ".git") { "The .git directory is protected." }
        return DocumentsContract.createDocument(
            resolver,
            documentParentUri(parent),
            mimeType,
            clean,
        ) ?: throw IOException("Unable to create file: $clean")
    }

    fun createFolder(parent: Uri, name: String): Uri {
        val clean = safeName(name, "Folder name")
        require(clean != ".git") { "The .git directory is protected." }
        return DocumentsContract.createDocument(
            resolver,
            documentParentUri(parent),
            DocumentsContract.Document.MIME_TYPE_DIR,
            clean,
        ) ?: throw IOException("Unable to create folder: $clean")
    }

    /**
     * DocumentsContract.createDocument expects a document URI. A workspace root is
     * persisted as a tree URI, so normalize both forms here before creating a child.
     */
    private fun documentParentUri(parent: Uri): Uri {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(parent) }.getOrNull()
        return treeDocumentId?.let { DocumentsContract.buildDocumentUriUsingTree(parent, it) } ?: parent
    }

    fun delete(uri: Uri, name: String) {
        require(name != ".git") { "The .git directory is protected." }
        require(DocumentsContract.deleteDocument(resolver, uri)) {
            "Unable to delete: $name"
        }
    }

    fun rename(uri: Uri, oldName: String, newName: String): Uri {
        val clean = safeName(newName, "New name")
        require(oldName != ".git" && clean != ".git") { "The .git directory is protected." }
        return DocumentsContract.renameDocument(resolver, uri, clean)
            ?: throw IOException("Unable to rename: $oldName")
    }

    private fun safeName(value: String, label: String): String {
        val clean = value.trim()
        require(clean.isNotBlank()) { "$label is required." }
        require(clean.length <= 255) { "$label is too long." }
        require(clean != "." && clean != "..") { "$label is invalid." }
        require('/' !in clean && '\\' !in clean && '\u0000' !in clean) { "$label is invalid." }
        WorkspacePathScope.normalize(clean)
        return clean
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".kt", true) || name.endsWith(".java", true) ||
            name.endsWith(".js", true) || name.endsWith(".ts", true) ||
            name.endsWith(".py", true) || name.endsWith(".go", true) ||
            name.endsWith(".rs", true) || name.endsWith(".c", true) ||
            name.endsWith(".cpp", true) || name.endsWith(".h", true) -> "text/plain"
        name.endsWith(".json", true) -> "application/json"
        name.endsWith(".xml", true) -> "application/xml"
        name.endsWith(".html", true) -> "text/html"
        name.endsWith(".css", true) -> "text/css"
        name.endsWith(".md", true) -> "text/markdown"
        else -> "text/plain"
    }
}
