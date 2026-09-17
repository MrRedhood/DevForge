package com.mrredhood.devforge.core.git

import android.content.ContentResolver

/** Bounded read-only commit/file history over loose Git objects available through SAF. */
class GitCommitHistoryService(private val resolver: ContentResolver) {
    suspend fun load(repository: GitRepositoryState, maxCommits: Int = MAX_COMMITS): GitHistorySnapshot {
        val reader = GitObjectReader(resolver, repository.gitDirectoryUri)
        val commits = mutableListOf<GitCommitHistoryEntry>()
        var currentId = repository.headRevision?.takeIf { SHA_PATTERN.matches(it) }
        var traversed = 0
        while (!currentId.isNullOrBlank() && traversed < maxCommits) {
            val commit = readCommit(reader, currentId!!) ?: break
            val changedFiles = changedFilesForCommit(reader, commit, MAX_CHANGED_FILES)
            commits += commit.toHistoryEntry(changedFiles)
            currentId = commit.parents.firstOrNull()
            traversed++
        }
        return GitHistorySnapshot(commits = commits, truncated = !currentId.isNullOrBlank())
    }

    suspend fun changedFiles(repository: GitRepositoryState, commitId: String, maxFiles: Int = MAX_CHANGED_FILES): List<GitFileHistoryEntry> {
        val reader = GitObjectReader(resolver, repository.gitDirectoryUri)
        val commit = readCommit(reader, commitId) ?: return emptyList()
        return changedFilesForCommit(reader, commit, maxFiles)
    }

    suspend fun fileHistory(repository: GitRepositoryState, path: String, maxCommits: Int = MAX_FILE_HISTORY_COMMITS): List<GitFileHistoryEntry> {
        val normalized = normalizePath(path) ?: return emptyList()
        val reader = GitObjectReader(resolver, repository.gitDirectoryUri)
        val result = mutableListOf<GitFileHistoryEntry>()
        var currentId = repository.headRevision?.takeIf { SHA_PATTERN.matches(it) }
        var count = 0
        while (!currentId.isNullOrBlank() && count < maxCommits) {
            val commit = readCommit(reader, currentId!!) ?: break
            val currentFiles = flattenTree(reader, commit.treeId, MAX_CHANGED_FILES)
            val currentObject = currentFiles[normalized]
            val parent = commit.parents.firstOrNull()
            val previousObject = parent?.let { parentId ->
                readCommit(reader, parentId)?.let { parentCommit -> flattenTree(reader, parentCommit.treeId, MAX_CHANGED_FILES)[normalized] }
            }
            if (currentObject != previousObject) {
                result += GitFileHistoryEntry(
                    commitId = commit.id,
                    path = normalized,
                    subject = commit.subject,
                    author = commit.author,
                    authoredAtEpochMs = commit.authoredAtEpochMs,
                    changeType = when {
                        previousObject == null && currentObject != null -> GitFileChangeType.ADDED
                        previousObject != null && currentObject == null -> GitFileChangeType.DELETED
                        else -> GitFileChangeType.MODIFIED
                    },
                )
            }
            currentId = parent
            count++
        }
        return result
    }

    private fun changedFilesForCommit(reader: GitObjectReader, commit: ParsedCommit, maxFiles: Int): List<GitFileHistoryEntry> {
        val currentTree = flattenTree(reader, commit.treeId, maxFiles)
        val parentTree = commit.parents.firstOrNull()?.let { parentId -> readCommit(reader, parentId)?.let { flattenTree(reader, it.treeId, maxFiles) } }.orEmpty()
        val paths = (currentTree.keys + parentTree.keys).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        return paths.asSequence().filter { currentTree[it] != parentTree[it] }.take(maxFiles).map { path ->
            val change = when {
                parentTree[path] == null -> GitFileChangeType.ADDED
                currentTree[path] == null -> GitFileChangeType.DELETED
                else -> GitFileChangeType.MODIFIED
            }
            GitFileHistoryEntry(
                commitId = commit.id,
                path = path,
                subject = commit.subject,
                author = commit.author,
                authoredAtEpochMs = commit.authoredAtEpochMs,
                changeType = change,
            )
        }.toList()
    }

    private fun readCommit(reader: GitObjectReader, id: String): ParsedCommit? {
        if (id.isBlank() || !SHA_PATTERN.matches(id)) return null
        val gitObject = reader.read(id, MAX_COMMIT_BYTES)
        val content = (gitObject as? GitObjectResult.Success)?.takeIf { it.type == "commit" }?.content ?: return null
        val text = content.toString(Charsets.UTF_8)
        val separator = text.indexOf("\n\n")
        if (separator < 0) return null
        val headers = text.substring(0, separator).lineSequence().toList()
        val body = text.substring(separator + 2)
        val treeId = headers.firstOrNull { it.startsWith("tree ") }?.removePrefix("tree ")?.takeIf { SHA_PATTERN.matches(it) } ?: return null
        val parents = headers.filter { it.startsWith("parent ") }.mapNotNull { value -> value.removePrefix("parent ").takeIf { SHA_PATTERN.matches(it) } }
        val authorHeader = headers.firstOrNull { it.startsWith("author ") }?.removePrefix("author ").orEmpty()
        val (author, authoredAt) = parseAuthor(authorHeader)
        return ParsedCommit(id, treeId, parents, author, authoredAt, body.lineSequence().firstOrNull().orEmpty().trim().ifBlank { "(no commit message)" })
    }

    private fun parseAuthor(header: String): Pair<String, Long?> {
        val match = Regex("^(.*) <([^>]+)> (-?\\d+) [+-]\\d{4}$").find(header) ?: return header to null
        return match.groupValues[1] to match.groupValues[3].toLongOrNull()?.times(1000L)
    }

    private fun ParsedCommit.toHistoryEntry(changed: List<GitFileHistoryEntry>): GitCommitHistoryEntry = GitCommitHistoryEntry(
        commitId = id,
        shortId = id.take(12),
        subject = subject,
        author = author,
        authoredAtEpochMs = authoredAtEpochMs,
        parents = parents,
        changedFileCount = changed.size,
        changedFilesTruncated = changed.size >= MAX_CHANGED_FILES,
    )

    private fun flattenTree(reader: GitObjectReader, treeId: String, maxFiles: Int): Map<String, String> {
        val result = linkedMapOf<String, String>()
        walkTree(reader, treeId, "", result, 0, maxFiles)
        return result
    }

    private fun walkTree(reader: GitObjectReader, treeId: String, prefix: String, result: MutableMap<String, String>, depth: Int, maxFiles: Int) {
        if (depth > MAX_TREE_DEPTH || result.size >= maxFiles) return
        when (val tree = reader.readTree(treeId, MAX_TREE_ENTRIES)) {
            is GitObjectResultWithEntries.Unavailable -> return
            is GitObjectResultWithEntries.Success -> tree.entries.forEach { entry ->
                if (result.size >= maxFiles) return@forEach
                val path = if (prefix.isBlank()) entry.name else "$prefix/${entry.name}"
                if (entry.mode.startsWith("04")) walkTree(reader, entry.objectId, path, result, depth + 1, maxFiles) else result[path] = entry.objectId
            }
        }
    }

    private fun normalizePath(path: String): String? {
        val value = path.trim().replace('\\', '/')
        if (value.isBlank() || value.startsWith('/') || value.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return value.removePrefix("./").takeIf { it.isNotBlank() }
    }

    private data class ParsedCommit(
        val id: String,
        val treeId: String,
        val parents: List<String>,
        val author: String,
        val authoredAtEpochMs: Long?,
        val subject: String,
    )

    companion object {
        private const val MAX_COMMITS = 50
        private const val MAX_CHANGED_FILES = 500
        private const val MAX_FILE_HISTORY_COMMITS = 40
        private const val MAX_COMMIT_BYTES = 128 * 1024
        private const val MAX_TREE_ENTRIES = 20_000
        private const val MAX_TREE_DEPTH = 16
        private val SHA_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}

data class GitHistorySnapshot(val commits: List<GitCommitHistoryEntry>, val truncated: Boolean)

data class GitCommitHistoryEntry(
    val commitId: String,
    val shortId: String,
    val subject: String,
    val author: String,
    val authoredAtEpochMs: Long?,
    val parents: List<String>,
    val changedFileCount: Int,
    val changedFilesTruncated: Boolean,
)

data class GitFileHistoryEntry(
    val commitId: String,
    val path: String,
    val subject: String,
    val author: String,
    val authoredAtEpochMs: Long?,
    val changeType: GitFileChangeType,
)

enum class GitFileChangeType { ADDED, MODIFIED, DELETED }
