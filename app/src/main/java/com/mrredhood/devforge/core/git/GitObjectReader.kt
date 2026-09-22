package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

internal sealed interface GitObjectResult {
    data class Success(val type: String, val content: ByteArray) : GitObjectResult
    data class Unavailable(val reason: String) : GitObjectResult
}

internal data class GitTreeEntry(
    val name: String,
    val objectId: String,
    val mode: String,
)

internal class GitObjectReader(
    private val resolver: ContentResolver,
    private val gitDirectory: Uri,
) {
    fun read(sha: String, maxBytes: Int = MAX_OBJECT_BYTES): GitObjectResult {
        val normalized = sha.lowercase()
        if (!SHA_PATTERN.matches(normalized)) {
            return GitObjectResult.Unavailable("The requested Git object ID is invalid.")
        }
        val loose = readLoose(normalized, maxBytes)
        if (loose is GitObjectResult.Success) return loose
        val objects = findDirectChild(gitDirectory, "objects")
            ?: return loose
        return GitPackObjectReader(
            resolver = resolver,
            objectsDirectory = objects,
            looseFallback = { objectSha, limit -> readLoose(objectSha, limit) },
        ).read(normalized, maxBytes)
    }

    private fun readLoose(sha: String, maxBytes: Int): GitObjectResult = runCatching {
        val objects = findDirectChild(gitDirectory, "objects")
            ?: return@runCatching GitObjectResult.Unavailable("The repository object directory is unavailable.")
        val prefix = findDirectChild(objects, sha.take(2))
            ?: return@runCatching GitObjectResult.Unavailable("Git object $sha is not available as a loose object.")
        val objectUri = findDirectChild(prefix, sha.drop(2))
            ?: return@runCatching GitObjectResult.Unavailable("Git object $sha is not available as a loose object.")
        val inflated = inflate(objectUri, maxBytes)
            ?: return@runCatching GitObjectResult.Unavailable("Git object $sha could not be decompressed within the safety limit.")
        val headerEnd = inflated.indexOf(0)
        if (headerEnd <= 0 || headerEnd >= inflated.size) {
            return@runCatching GitObjectResult.Unavailable("Git object $sha has an invalid object header.")
        }
        val header = String(inflated, 0, headerEnd, Charsets.UTF_8)
        val separator = header.indexOf(' ')
        if (separator <= 0 || separator == header.lastIndex) {
            return@runCatching GitObjectResult.Unavailable("Git object $sha has an invalid object header.")
        }
        val type = header.substring(0, separator)
        val declaredSize = header.substring(separator + 1).toLongOrNull()
            ?: return@runCatching GitObjectResult.Unavailable("Git object $sha has an invalid size header.")
        val content = inflated.copyOfRange(headerEnd + 1, inflated.size)
        if (declaredSize != content.size.toLong()) {
            return@runCatching GitObjectResult.Unavailable("Git object $sha is truncated or malformed.")
        }
        GitObjectResult.Success(type, content)
    }.getOrElse { error ->
        GitObjectResult.Unavailable(error.message ?: "Git object could not be read.")
    }

    fun readCommitTree(sha: String): String? = when (val objectResult = read(sha, MAX_COMMIT_BYTES)) {
        is GitObjectResult.Success -> if (objectResult.type == "commit") {
            objectResult.content.toString(Charsets.UTF_8).lineSequence().takeWhile { it.isNotEmpty() }.firstOrNull { it.startsWith("tree ") }?.removePrefix("tree ")?.takeIf { SHA_PATTERN.matches(it) }
        } else null
        is GitObjectResult.Unavailable -> null
    }

    fun readTree(sha: String, maxEntries: Int = MAX_TREE_ENTRIES): GitObjectResultWithEntries = when (val objectResult = read(sha, MAX_TREE_BYTES)) {
        is GitObjectResult.Success -> if (objectResult.type != "tree") GitObjectResultWithEntries.Unavailable("Git object $sha is not a tree object.") else parseTree(objectResult.content, maxEntries)
        is GitObjectResult.Unavailable -> GitObjectResultWithEntries.Unavailable(objectResult.reason)
    }

    private fun parseTree(content: ByteArray, maxEntries: Int): GitObjectResultWithEntries {
        val result = ArrayList<GitTreeEntry>(minOf(maxEntries, 256))
        var offset = 0
        while (offset < content.size && result.size < maxEntries) {
            val modeEnd = findByte(content, ' '.code.toByte(), offset) ?: return GitObjectResultWithEntries.Unavailable("A Git tree entry has an invalid mode field.")
            val nameEnd = findByte(content, 0, modeEnd + 1) ?: return GitObjectResultWithEntries.Unavailable("A Git tree entry has an unterminated name.")
            val objectIdOffset = nameEnd + 1
            if (objectIdOffset + 20 > content.size) return GitObjectResultWithEntries.Unavailable("A Git tree entry is truncated.")
            val mode = String(content, offset, modeEnd - offset, Charsets.US_ASCII)
            val name = String(content, modeEnd + 1, nameEnd - modeEnd - 1, Charsets.UTF_8)
            val objectId = content.copyOfRange(objectIdOffset, objectIdOffset + 20).joinToString("") { "%02x".format(it) }
            result += GitTreeEntry(name = name, objectId = objectId, mode = mode)
            offset = objectIdOffset + 20
        }
        return GitObjectResultWithEntries.Success(result, truncated = offset < content.size)
    }

    private fun inflate(uri: Uri, maxBytes: Int): ByteArray? {
        resolver.openInputStream(uri)?.use { raw ->
            InflaterInputStream(raw).use { input ->
                val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return null
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        }
        return null
    }

    private fun findDirectChild(parent: Uri, name: String): Uri? = listChildren(parent).firstOrNull { it.name == name }?.uri
    private data class ChildDocument(val uri: Uri, val name: String)

    private fun listChildren(parent: Uri): List<ChildDocument> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < MAX_CHILDREN) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    add(ChildDocument(DocumentsContract.buildDocumentUriUsingTree(parent, id), name))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_OBJECT_BYTES = 4 * 1024 * 1024
        const val MAX_COMMIT_BYTES = 128 * 1024
        const val MAX_TREE_BYTES = 2 * 1024 * 1024
        const val MAX_TREE_ENTRIES = 20_000
        const val MAX_CHILDREN = 256
        val SHA_PATTERN = Regex("^[0-9a-f]{40}$")
    }
}

internal sealed interface GitObjectResultWithEntries {
    data class Success(val entries: List<GitTreeEntry>, val truncated: Boolean) : GitObjectResultWithEntries
    data class Unavailable(val reason: String) : GitObjectResultWithEntries
}

private fun ByteArray.indexOf(value: Int): Int = indexOfFirst { it.toInt() == value }
private fun findByte(bytes: ByteArray, value: Byte, start: Int): Int? {
    for (index in start until bytes.size) if (bytes[index] == value) return index
    return null
}
