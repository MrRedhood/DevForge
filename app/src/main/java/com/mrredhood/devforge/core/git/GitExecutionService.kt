package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.zip.DeflaterOutputStream

sealed interface GitExecutionResult {
    data class Success(val message: String, val revision: String? = null) : GitExecutionResult
    data class Failure(val message: String) : GitExecutionResult
}

/**
 * Bounded Git plumbing mutations over the user-selected SAF workspace.
 * No shell, arbitrary command execution, or hidden filesystem access is used.
 */
class GitExecutionService(private val resolver: ContentResolver) {
    suspend fun stage(root: Uri, gitDirectory: Uri, paths: List<String>): GitExecutionResult = runMutation {
        val normalizedPaths = normalizePaths(paths)
        require(normalizedPaths.isNotEmpty()) { "Select at least one workspace path to stage." }
        val index = loadIndex(gitDirectory)
        val entries = index.entries.toMutableList()

        normalizedPaths.forEach { path ->
            entries.removeAll { it.path == path }
            val file = findPath(root, path) ?: return@forEach
            require(!file.directory) { "Cannot stage a directory directly: $path" }
            val bytes = readBytes(file.uri, MAX_STAGE_BYTES)
            val objectId = writeLooseObject(gitDirectory, "blob", bytes)
            entries += GitIndexEntry(path, objectId, existingMode(index.entries, path) ?: 33188L, 0)
        }

        writeIndex(gitDirectory, normalizeIndexEntries(entries))
        "Staged ${normalizedPaths.size} path(s)."
    }

    suspend fun unstage(gitDirectory: Uri, headRevision: String?, paths: List<String>): GitExecutionResult = runMutation {
        val normalizedPaths = normalizePaths(paths)
        require(normalizedPaths.isNotEmpty()) { "Select at least one workspace path to unstage." }
        require(headRevision?.matches(SHA_PATTERN) == true) { "HEAD objects are unavailable; unstage is safely disabled." }
        val index = loadIndex(gitDirectory)
        val restored = readHeadFiles(gitDirectory, headRevision)
        val entries = index.entries.toMutableList()
        normalizedPaths.forEach { path ->
            entries.removeAll { it.path == path }
            restored[path]?.let { head ->
                entries += GitIndexEntry(path, head.objectId, head.mode.toLongOrNull(8) ?: 33188L, 0)
            }
        }
        writeIndex(gitDirectory, normalizeIndexEntries(entries))
        "Unstaged ${normalizedPaths.size} path(s)."
    }

    suspend fun commit(gitDirectory: Uri, headRevision: String?, message: String): GitExecutionResult = runMutation {
        val normalizedMessage = message.trim().take(MAX_COMMIT_MESSAGE)
        require(normalizedMessage.isNotBlank()) { "Commit message cannot be empty." }
        val index = loadIndex(gitDirectory)
        require(!index.entries.any { it.stage != 0 }) { "The index contains unresolved conflicts; resolve them before committing." }
        val stagedEntries = normalizeIndexEntries(index.entries)
        require(stagedEntries.isNotEmpty()) { "Nothing is staged to commit." }

        val identity = readIdentity(gitDirectory)
            ?: throw IllegalStateException("Configure user.name and user.email in .git/config before committing.")
        val treeId = writeTree(gitDirectory, stagedEntries)
        val timestamp = ZonedDateTime.now()
        val author = formatIdentity(identity.name, identity.email, timestamp)
        val parent = headRevision?.takeIf { SHA_PATTERN.matches(it) }
        val commitContent = buildString {
            append("tree ").append(treeId).append('\n')
            parent?.let { append("parent ").append(it).append('\n') }
            append("author ").append(author).append('\n')
            append("committer ").append(author).append("\n\n")
            append(normalizedMessage).append('\n')
        }.toByteArray(Charsets.UTF_8)
        val commitId = writeLooseObject(gitDirectory, "commit", commitContent)
        updateHead(gitDirectory, commitId)
        "Created commit ${commitId.take(12)}." to commitId
    }

    suspend fun createBranch(gitDirectory: Uri, headRevision: String?, name: String): GitExecutionResult = runMutation {
        val branch = validateBranchName(name)
        val revision = headRevision?.takeIf { SHA_PATTERN.matches(it) }
            ?: throw IllegalStateException("A valid HEAD revision is required before creating a branch.")
        require(findPath(gitDirectory, "refs/heads/$branch") == null) { "Branch '$branch' already exists." }
        val parent = ensureDirectoryPath(gitDirectory, listOf("refs", "heads", *branch.split('/').dropLast(1).toTypedArray()))
        val created = DocumentsContract.createDocument(resolver, parent, "text/plain", branch.substringAfterLast('/'))
            ?: throw IOException("Unable to create branch ref '$branch'.")
        writeText(created, "$revision\n")
        "Created branch '$branch'." to null
    }

    suspend fun deleteBranch(gitDirectory: Uri, currentBranch: String?, name: String): GitExecutionResult = runMutation {
        val branch = validateBranchName(name)
        require(branch != currentBranch) { "The current branch cannot be deleted." }
        val ref = findPath(gitDirectory, "refs/heads/$branch")
            ?: throw IllegalStateException("Branch '$branch' does not exist.")
        require(!ref.directory) { "Branch ref '$branch' is not a file." }
        require(DocumentsContract.deleteDocument(resolver, ref.uri)) { "Unable to delete branch '$branch'." }
        "Deleted branch '$branch'." to null
    }

    private fun updateHead(gitDirectory: Uri, revision: String) {
        val head = findDirectChild(gitDirectory, "HEAD") ?: throw IOException("Git HEAD is unavailable.")
        val current = readText(head, 4096)?.trim().orEmpty()
        if (current.startsWith("ref:")) {
            val branch = current.removePrefix("ref:").trim().removePrefix("refs/heads/")
            val ref = findPath(gitDirectory, "refs/heads/$branch") ?: throw IOException("Current branch ref '$branch' is unavailable.")
            writeText(ref.uri, "$revision\n")
        } else {
            writeText(head, "$revision\n")
        }
    }

    private fun loadIndex(gitDirectory: Uri): ParsedIndex {
        val indexUri = findDirectChild(gitDirectory, "index") ?: return ParsedIndex(2, emptyList())
        val bytes = readBytes(indexUri, MAX_INDEX_BYTES)
        return when (val parsed = GitIndexParser.parse(bytes)) {
            is GitIndexParseResult.Success -> {
                require(!parsed.truncated) { "The Git index exceeds DevForge's mutation safety limit." }
                ParsedIndex(parsed.version, parsed.entries)
            }
            is GitIndexParseResult.Unsupported -> throw IllegalStateException(parsed.reason)
        }
    }

    private fun writeIndex(gitDirectory: Uri, entries: List<GitIndexEntry>) {
        require(entries.size <= MAX_INDEX_ENTRIES) { "The Git index exceeds DevForge's mutation safety limit." }
        val body = ByteArrayOutputStream()
        writeAscii(body, "DIRC")
        writeUInt32(body, 2)
        writeUInt32(body, entries.size.toLong())
        entries.forEach { entry ->
            val entryStart = body.size()
            repeat(2) { writeUInt32(body, 0) }
            writeUInt32(body, entry.mode)
            repeat(4) { writeUInt32(body, 0) }
            writeBytes(body, hexToBytes(entry.objectId))
            val pathBytes = entry.path.toByteArray(Charsets.UTF_8)
            val flags = ((entry.stage and 0x3) shl 12) or minOf(pathBytes.size, 0x0fff)
            writeUInt16(body, flags)
            writeBytes(body, pathBytes)
            body.write(0)
            val padding = (8 - (body.size() - entryStart).mod(8)) % 8
            repeat(padding) { body.write(0) }
        }
        val content = body.toByteArray()
        val checksum = MessageDigest.getInstance("SHA-1").digest(content)
        val output = ByteArrayOutputStream(content.size + checksum.size)
        output.write(content)
        output.write(checksum)
        val indexUri = findDirectChild(gitDirectory, "index")
            ?: DocumentsContract.createDocument(resolver, gitDirectory, "application/octet-stream", "index")
            ?: throw IOException("Unable to create the Git index.")
        writeBytes(indexUri, output.toByteArray())
    }

    private fun writeTree(gitDirectory: Uri, entries: List<GitIndexEntry>): String {
        val root = TreeNode()
        entries.forEach { entry ->
            require(entry.stage == 0) { "Cannot write a tree from conflict-stage entries." }
            val parts = entry.path.split('/').filter(String::isNotEmpty)
            require(parts.isNotEmpty()) { "Git index contains an empty path." }
            var node = root
            parts.dropLast(1).forEach { part -> node = node.directories.getOrPut(part) { TreeNode() } }
            node.files[parts.last()] = entry
        }
        return writeTreeNode(gitDirectory, root)
    }

    private fun writeTreeNode(gitDirectory: Uri, node: TreeNode): String {
        val entries = mutableListOf<TreeLine>()
        node.files.values.forEach { entries += TreeLine(it.path.substringAfterLast('/'), formatMode(it.mode), it.objectId, false) }
        node.directories.forEach { (name, child) -> entries += TreeLine(name, "040000", writeTreeNode(gitDirectory, child), true) }
        entries.sortWith(Comparator { left, right -> compareBytes(left.sortKey.toByteArray(Charsets.UTF_8), right.sortKey.toByteArray(Charsets.UTF_8)) })
        val content = ByteArrayOutputStream()
        entries.forEach { line ->
            writeAscii(content, line.mode)
            content.write(' '.code)
            writeAscii(content, line.name)
            content.write(0)
            content.write(hexToBytes(line.objectId))
        }
        return writeLooseObject(gitDirectory, "tree", content.toByteArray())
    }

    private fun writeLooseObject(gitDirectory: Uri, type: String, content: ByteArray): String {
        require(content.size <= MAX_OBJECT_BYTES) { "Git object exceeds DevForge's safety limit." }
        val header = "$type ${content.size}\u0000".toByteArray(Charsets.UTF_8)
        val objectBytes = ByteArray(header.size + content.size)
        header.copyInto(objectBytes)
        content.copyInto(objectBytes, destinationOffset = header.size)
        val objectId = MessageDigest.getInstance("SHA-1").digest(objectBytes).toHex()
        val objects = findDirectChild(gitDirectory, "objects") ?: throw IOException("Git objects directory is unavailable.")
        val bucket = ensureDirectoryPath(objects, listOf(objectId.take(2)))
        if (findDirectChild(bucket, objectId.drop(2)) == null) {
            val target = DocumentsContract.createDocument(resolver, bucket, "application/octet-stream", objectId.drop(2))
                ?: throw IOException("Unable to create Git object $objectId.")
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed).use { it.write(objectBytes) }
            writeBytes(target, compressed.toByteArray())
        }
        return objectId
    }

    private fun readHeadFiles(gitDirectory: Uri, revision: String): Map<String, HeadEntry> {
        val reader = GitObjectReader(resolver, gitDirectory)
        val tree = reader.readCommitTree(revision) ?: throw IllegalStateException("HEAD commit objects are unavailable on this access path.")
        val result = linkedMapOf<String, HeadEntry>()
        traverseTree(reader, tree, "", result, 0)
        return result
    }

    private fun traverseTree(reader: GitObjectReader, treeId: String, prefix: String, result: MutableMap<String, HeadEntry>, depth: Int) {
        if (depth > MAX_TREE_DEPTH || result.size >= MAX_INDEX_ENTRIES) return
        when (val tree = reader.readTree(treeId, MAX_INDEX_ENTRIES)) {
            is GitObjectResultWithEntries.Unavailable -> throw IllegalStateException(tree.reason)
            is GitObjectResultWithEntries.Success -> tree.entries.forEach { entry ->
                val path = if (prefix.isBlank()) entry.name else "$prefix/${entry.name}"
                if (entry.mode.startsWith("04")) traverseTree(reader, entry.objectId, path, result, depth + 1)
                else result[path] = HeadEntry(entry.objectId, entry.mode)
            }
        }
    }

    private fun readIdentity(gitDirectory: Uri): GitIdentity? {
        val config = findDirectChild(gitDirectory, "config")?.let { readText(it, MAX_CONFIG_BYTES) } ?: return null
        var section = ""
        var name: String? = null
        var email: String? = null
        config.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("[") && line.endsWith("]")) section = line
            else if (section == "[user]" && line.startsWith("name =")) name = line.substringAfter('=').trim()
            else if (section == "[user]" && line.startsWith("email =")) email = line.substringAfter('=').trim()
        }
        return if (!name.isNullOrBlank() && !email.isNullOrBlank()) GitIdentity(name, email) else null
    }

    private fun formatIdentity(name: String, email: String, time: ZonedDateTime): String {
        val offset = time.offset.totalSeconds
        val sign = if (offset < 0) '-' else '+'
        val absolute = kotlin.math.abs(offset)
        val hours = absolute / 3600
        val minutes = (absolute % 3600) / 60
        return "$name <$email> ${time.toEpochSecond()} $sign%02d%02d".format(hours, minutes)
    }

    private fun normalizeIndexEntries(entries: List<GitIndexEntry>): List<GitIndexEntry> =
        entries.filter { it.stage in 0..3 }.sortedWith(Comparator { left, right ->
            val pathCompare = compareBytes(left.path.toByteArray(Charsets.UTF_8), right.path.toByteArray(Charsets.UTF_8))
            if (pathCompare != 0) pathCompare else left.stage.compareTo(right.stage)
        })

    private fun existingMode(entries: List<GitIndexEntry>, path: String): Long? = entries.firstOrNull { it.path == path && it.stage == 0 }?.mode

    private fun normalizePaths(paths: List<String>): List<String> = paths.map(::normalizePath).distinct()

    private fun normalizePath(path: String): String {
        val normalized = path.replace('\\', '/').trim('/')
        require(normalized.isNotBlank() && normalized != ".") { "Git path is invalid." }
        require(!normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) { "Git path is invalid." }
        require(!normalized.split('/').contains(".git")) { "Git metadata is not a stageable workspace path." }
        return normalized
    }

    private fun validateBranchName(name: String): String {
        val branch = name.trim()
        require(branch.isNotBlank()) { "Branch name cannot be empty." }
        require(branch.length <= 200) { "Branch name is too long." }
        require(!branch.startsWith('/') && !branch.endsWith('/') && !branch.startsWith('.') && !branch.endsWith('.')) { "Invalid Git branch name." }
        require(!branch.contains("..") && !branch.contains("@{") && !branch.contains(' ')) { "Invalid Git branch name." }
        require(branch.none { it.code < 32 || it == '~' || it == '^' || it == ':' || it == '?' || it == '*' || it == '[' || it == '\\' }) { "Invalid Git branch name." }
        return branch
    }

    private fun findPath(root: Uri, relativePath: String): DocumentRef? {
        var current = root
        val parts = relativePath.split('/').filter(String::isNotEmpty)
        for ((index, part) in parts.withIndex()) {
            val child = findDirectChild(current, part) ?: return null
            current = child
            if (index == parts.lastIndex) return DocumentRef(child, isDirectory(child))
        }
        return null
    }

    private fun ensureDirectoryPath(root: Uri, parts: List<String>): Uri {
        var current = root
        parts.filter(String::isNotBlank).forEach { part ->
            current = findDirectChild(current, part) ?: DocumentsContract.createDocument(resolver, current, DocumentsContract.Document.MIME_TYPE_DIR, part)
                ?: throw IOException("Unable to create Git directory '$part'.")
            require(isDirectory(current)) { "Git path component '$part' is not a directory." }
        }
        return current
    }

    private fun findDirectChild(parent: Uri, name: String): Uri? = listChildren(parent).firstOrNull { it.name == name }?.uri

    private fun listChildren(parent: Uri): List<ChildDocument> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < MAX_CHILDREN) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    add(ChildDocument(DocumentsContract.buildDocumentUriUsingTree(parent, id), name, cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun isDirectory(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR } == true
    }.getOrDefault(false)

    private fun readText(uri: Uri, maxBytes: Int): String? = readBytes(uri, maxBytes).toString(Charsets.UTF_8)

    private fun readBytes(uri: Uri, maxBytes: Int): ByteArray {
        resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) throw IOException("Git file exceeds the safety limit of $maxBytes bytes.")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        } ?: throw IOException("Unable to read the selected Git document.")
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        resolver.openOutputStream(uri, "rwt")?.use { it.write(bytes) }
            ?: throw IOException("Unable to write the selected Git document.")
    }

    private fun writeText(uri: Uri, value: String) = writeBytes(uri, value.toByteArray(Charsets.UTF_8))

    private fun runMutation(block: () -> Any): GitExecutionResult = runCatching {
        when (val result = block()) {
            is String -> GitExecutionResult.Success(result)
            is Pair<*, *> -> {
                val message = result.first as? String ?: error("Git mutation returned an invalid message.")
                val revision = result.second as? String
                GitExecutionResult.Success(message, revision)
            }
            else -> error("Git mutation returned an unsupported result.")
        }
    }.getOrElse { GitExecutionResult.Failure(it.message ?: "Git mutation failed safely.") }

    private data class ParsedIndex(val version: Int, val entries: List<GitIndexEntry>)
    private data class GitIdentity(val name: String, val email: String)
    private data class HeadEntry(val objectId: String, val mode: String)
    private data class DocumentRef(val uri: Uri, val directory: Boolean)
    private data class ChildDocument(val uri: Uri, val name: String, val directory: Boolean)
    private data class TreeNode(val files: MutableMap<String, GitIndexEntry> = linkedMapOf(), val directories: MutableMap<String, TreeNode> = linkedMapOf())
    private data class TreeLine(val name: String, val mode: String, val objectId: String, val directory: Boolean) {
        val sortKey: String get() = name + if (directory) "/" else ""
    }

    private companion object {
        const val MAX_STAGE_BYTES = 8 * 1024 * 1024
        const val MAX_INDEX_BYTES = 16 * 1024 * 1024
        const val MAX_INDEX_ENTRIES = 20_000
        const val MAX_OBJECT_BYTES = 4 * 1024 * 1024
        const val MAX_CONFIG_BYTES = 128 * 1024
        const val MAX_COMMIT_MESSAGE = 16 * 1024
        const val MAX_TREE_DEPTH = 16
        const val MAX_CHILDREN = 512
        val SHA_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}

private fun writeAscii(output: ByteArrayOutputStream, value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
private fun writeBytes(output: ByteArrayOutputStream, bytes: ByteArray) = output.write(bytes)

private fun writeUInt16(output: ByteArrayOutputStream, value: Int) {
    output.write((value ushr 8) and 0xff)
    output.write(value and 0xff)
}

private fun writeUInt32(output: ByteArrayOutputStream, value: Long) {
    output.write(((value ushr 24) and 0xff).toInt())
    output.write(((value ushr 16) and 0xff).toInt())
    output.write(((value ushr 8) and 0xff).toInt())
    output.write((value and 0xff).toInt())
}

private fun formatMode(mode: Long): String = mode.toString(8).padStart(6, '0').takeLast(6)
private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun compareBytes(left: ByteArray, right: ByteArray): Int {
    val limit = minOf(left.size, right.size)
    for (index in 0 until limit) {
        val diff = (left[index].toInt() and 0xff) - (right[index].toInt() and 0xff)
        if (diff != 0) return diff
    }
    return left.size.compareTo(right.size)
}
