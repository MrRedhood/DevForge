package com.mrredhood.devforge.core.terminal

import com.mrredhood.devforge.core.github.GitHubContentsResult
import com.mrredhood.devforge.core.github.GitHubFileResult
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceRemote

class GitHubRemoteTerminal(
    private val gateway: GitHubRepositoryGateway,
) {
    suspend fun execute(
        remote: GitHubWorkspaceRemote,
        workingDirectory: String,
        commandLine: String,
    ): String {
        val tokens = tokenize(commandLine)
        if (tokens.isEmpty()) return ""
        return when (tokens.first()) {
            "pwd" -> "/workspace" + if (workingDirectory.isBlank()) "" else "/" + workingDirectory
            "ls" -> list(remote, workingDirectory, tokens.drop(1))
            "cat" -> cat(remote, workingDirectory, tokens.drop(1))
            "head" -> headTail(remote, workingDirectory, tokens.drop(1), true)
            "tail" -> headTail(remote, workingDirectory, tokens.drop(1), false)
            "wc" -> wordCount(remote, workingDirectory, tokens.drop(1))
            "grep" -> grep(remote, workingDirectory, tokens.drop(1))
            "find" -> find(remote, workingDirectory, tokens.drop(1))
            "echo" -> tokens.drop(1).joinToString(" ")
            "basename" -> basename(resolvePath(workingDirectory, tokens.lastOrNull() ?: throw IllegalArgumentException("basename: missing operand")))
            "dirname" -> dirname(resolvePath(workingDirectory, tokens.lastOrNull() ?: throw IllegalArgumentException("dirname: missing operand")))
            "realpath" -> "/workspace/" + resolvePath(workingDirectory, tokens.lastOrNull() ?: ".")
            "readlink" -> throw IllegalArgumentException("readlink: symbolic links are not available in a GitHub-backed workspace.")
            "sha256sum" -> sha256(remote, workingDirectory, tokens.drop(1))
            "stat" -> stat(remote, workingDirectory, tokens.drop(1))
            "cmp" -> compare(remote, workingDirectory, tokens.drop(1), false)
            "diff" -> compare(remote, workingDirectory, tokens.drop(1), true)
            "date" -> java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date())
            "uname" -> "DevForge virtual GitHub workspace"
            "whoami" -> "github:" + remote.owner
            "id" -> "github:" + remote.owner
            "git" -> git(remote, tokens.drop(1))
            else -> throw IllegalArgumentException("GitHub workspace terminal supports: pwd, ls, cat, head, tail, wc, grep, find, echo, basename, dirname, realpath, sha256sum, stat, cmp, diff, date, uname, whoami, id and git log/status/branch/show.")
        }
    }

    private suspend fun list(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        var path = cwd
        val positional = args.filterNot { it.startsWith("-") }
        if (positional.isNotEmpty()) path = resolvePath(cwd, positional.last())
        return when (val result = gateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
            is GitHubContentsResult.Failure -> error(result.message)
            is GitHubContentsResult.Success -> result.entries
                .sortedWith(compareBy<com.mrredhood.devforge.core.github.GitHubContentEntry> { it.type != "dir" }.thenBy { it.name.lowercase() })
                .joinToString("\n") { entry -> entry.name + if (entry.type == "dir") "/" else "" }
        }
    }

    private suspend fun cat(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        val path = args.lastOrNull { !it.startsWith("-") } ?: throw IllegalArgumentException("cat: missing file operand")
        val resolved = resolvePath(cwd, path)
        return when (val result = gateway.readFile(remote.owner, remote.repository, resolved, remote.branch)) {
            is GitHubFileResult.Failure -> error(result.message)
            is GitHubFileResult.Success -> result.content.take(MAX_FILE_CHARS)
        }
    }

    private suspend fun headTail(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>, head: Boolean): String {
        var count = 10
        val positional = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            val value = args[index]
            when {
                value == "-n" && index + 1 < args.size -> { count = args[index + 1].toIntOrNull()?.coerceIn(1, MAX_LINES) ?: 10; index += 2 }
                value.startsWith("-n") -> { count = value.removePrefix("-n").toIntOrNull()?.coerceIn(1, MAX_LINES) ?: 10; index++ }
                !value.startsWith("-") -> { positional += value; index++ }
                else -> index++
            }
        }
        val path = positional.lastOrNull() ?: throw IllegalArgumentException("missing file operand")
        val lines = cat(remote, cwd, listOf(path)).lineSequence().toList()
        return if (head) lines.take(count).joinToString("\n") else lines.takeLast(count).joinToString("\n")
    }

    private suspend fun wordCount(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        val path = args.lastOrNull { !it.startsWith("-") } ?: throw IllegalArgumentException("wc: missing file operand")
        val text = cat(remote, cwd, listOf(path))
        val lines = text.count { it == '\n' }
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val bytes = text.toByteArray(Charsets.UTF_8).size
        return lines.toString() + " " + words + " " + bytes + " " + resolvePath(cwd, path)
    }

    private suspend fun grep(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        val values = args.filterNot { it == "-n" || it == "-i" }
        val pattern = values.firstOrNull() ?: throw IllegalArgumentException("grep: missing pattern")
        val path = values.drop(1).lastOrNull() ?: throw IllegalArgumentException("grep: missing file operand")
        val text = cat(remote, cwd, listOf(path))
        val ignoreCase = "-i" in args
        val regex = runCatching { Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }
            .getOrElse { Regex(Regex.escape(pattern), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }
        return text.lineSequence().mapIndexedNotNull { lineIndex, line ->
            if (regex.containsMatchIn(line)) {
                if ("-n" in args) (lineIndex + 1).toString() + ":" + line else line
            } else null
        }.take(MAX_LINES).joinToString("\n")
    }

    private suspend fun find(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        val pathArg = args.firstOrNull { !it.startsWith("-") }.orEmpty()
        val rootPath = resolvePath(cwd, pathArg.ifBlank { "." })
        val output = mutableListOf<String>()
        suspend fun visit(path: String, depth: Int) {
            if (depth > MAX_FIND_DEPTH || output.size >= MAX_FIND_RESULTS) return
            when (val result = gateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                is GitHubContentsResult.Failure -> return
                is GitHubContentsResult.Success -> result.entries.sortedBy { it.path }.forEach { entry ->
                    if (output.size >= MAX_FIND_RESULTS) return@forEach
                    output += entry.path
                    if (entry.type == "dir") visit(entry.path, depth + 1)
                }
            }
        }
        if (rootPath.isBlank()) visit("", 0) else { output += rootPath; visit(rootPath, 0) }
        return output.joinToString("\n")
    }

    private suspend fun sha256(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        val path = args.lastOrNull { !it.startsWith("-") } ?: throw IllegalArgumentException("sha256sum: missing file operand")
        val content = cat(remote, cwd, listOf(path))
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest + "  " + resolvePath(cwd, path)
    }

    private suspend fun stat(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        val raw = args.lastOrNull { !it.startsWith("-") } ?: throw IllegalArgumentException("stat: missing file operand")
        val path = resolvePath(cwd, raw)
        val parent = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val entry = when (val result = gateway.listContents(remote.owner, remote.repository, parent, remote.branch)) {
            is GitHubContentsResult.Failure -> error(result.message)
            is GitHubContentsResult.Success -> result.entries.firstOrNull { it.name == name }
        } ?: throw IllegalArgumentException("stat: '$raw' not found")
        return buildString {
            append("File: ").append(path).append('\n')
            append("Type: ").append(if (entry.type == "dir") "directory" else "file").append('\n')
            append("Size: ").append(entry.sizeBytes ?: -1).append('\n')
            append("SHA: ").append(entry.sha.orEmpty())
        }
    }

    private suspend fun compare(
        remote: GitHubWorkspaceRemote,
        cwd: String,
        args: List<String>,
        unifiedDiff: Boolean,
    ): String {
        val files = args.filterNot { it.startsWith("-") }
        require(files.size >= 2) {
            if (unifiedDiff) "diff: need two file operands" else "cmp: need two file operands"
        }
        val left = cat(remote, cwd, listOf(files[0]))
        val right = cat(remote, cwd, listOf(files[1]))
        if (!unifiedDiff) return if (left == right) "" else "Files differ."
        val leftLines = left.lines()
        val rightLines = right.lines()
        val max = maxOf(leftLines.size, rightLines.size)
        val output = StringBuilder()
        for (index in 0 until minOf(max, MAX_LINES)) {
            val leftLine = leftLines.getOrNull(index)
            val rightLine = rightLines.getOrNull(index)
            if (leftLine == rightLine) continue
            if (leftLine != null) output.append("-").append(leftLine).append('\n')
            if (rightLine != null) output.append("+").append(rightLine).append('\n')
        }
        return output.toString().trimEnd()
    }

    private suspend fun git(remote: GitHubWorkspaceRemote, args: List<String>): String {
        val subcommand = args.firstOrNull()?.lowercase()
            ?: throw IllegalArgumentException("git: missing subcommand")
        return when (subcommand) {
            "log" -> {
                var limit = 10
                var oneline = false
                val rest = args.drop(1)
                var index = 0
                while (index < rest.size) {
                    when {
                        rest[index] == "--oneline" -> {
                            oneline = true
                            index++
                        }
                        rest[index] == "-n" && index + 1 < rest.size -> {
                            limit = rest[index + 1].toIntOrNull()?.coerceIn(1, 50) ?: limit
                            index += 2
                        }
                        rest[index].startsWith("-n") -> {
                            limit = rest[index].removePrefix("-n").toIntOrNull()?.coerceIn(1, 50) ?: limit
                            index++
                        }
                        else -> index++
                    }
                }
                when (val result = gateway.listCommits(remote.owner, remote.repository, remote.branch, limit)) {
                    is com.mrredhood.devforge.core.github.GitHubCommitHistoryResult.Failure -> error(result.message)
                    is com.mrredhood.devforge.core.github.GitHubCommitHistoryResult.Success ->
                        result.commits.joinToString("\n") {
                            if (oneline) it.sha.take(12) + " " + it.subject
                            else it.sha + " " + it.author + " " + (it.authoredAt ?: "") + " " + it.subject
                        }
                }
            }
            "status" ->
                "On branch " + remote.branch +
                    ". GitHub-backed terminal exposes repository state; local worktree status is not available for a virtual workspace."
            "branch" -> when (val result = gateway.listBranches(remote.owner, remote.repository)) {
                is com.mrredhood.devforge.core.github.GitHubBranchesResult.Failure -> error(result.message)
                is com.mrredhood.devforge.core.github.GitHubBranchesResult.Success ->
                    result.branches.joinToString("\n") { branch ->
                        if (branch == remote.branch) "* " + branch else "  " + branch
                    }
            }
            "show" -> {
                val sha = args.drop(1).firstOrNull { it.matches(Regex("^[0-9a-fA-F]{40}$")) }
                    ?: throw IllegalArgumentException("git show: provide a full commit SHA")
                when (val result = gateway.getCommitDetails(remote.owner, remote.repository, sha)) {
                    is com.mrredhood.devforge.core.github.GitHubCommitDetailsResult.Failure -> error(result.message)
                    is com.mrredhood.devforge.core.github.GitHubCommitDetailsResult.Success -> {
                        val value = result.value
                        buildString {
                            append(value.sha).append('\n')
                            append("Author: ").append(value.author).append('\n')
                            append("Date: ").append(value.authoredAt ?: "unknown").append('\n')
                            append('\n').append(value.fullMessage).append('\n').append('\n')
                            append("Files: ").append(value.changedFiles.size)
                                .append("  +").append(value.additions)
                                .append(" -").append(value.deletions).append('\n')
                            value.changedFiles.forEach { file ->
                                append(file.status).append('\t').append(file.path).append('\n')
                            }
                        }.trimEnd()
                    }
                }
            }
            else -> throw IllegalArgumentException("git: supported subcommands are log, status, branch and show.")
        }
    }

    private fun basename(path: String): String = path.substringAfterLast('/').ifBlank { "/" }
    private fun dirname(path: String): String = path.substringBeforeLast('/', "").ifBlank { "." }

    private fun resolvePath(cwd: String, raw: String): String {
        val normalized = raw.trim().replace('\\', '/')
        val pieces = if (normalized.startsWith("/")) normalized.removePrefix("/").split('/')
        else cwd.trim('/').split('/').filter(String::isNotBlank) + normalized.split('/')
        val result = ArrayDeque<String>()
        pieces.forEach { piece ->
            when {
                piece.isBlank() || piece == "." -> Unit
                piece == ".." -> if (result.isNotEmpty()) result.removeLast()
                else -> result.addLast(piece)
            }
        }
        return result.joinToString("/").take(MAX_PATH_CHARS)
    }

    private fun tokenize(commandLine: String): List<String> = commandLine.trim().split(Regex("\\s+")).filter(String::isNotBlank)

    companion object {
        private const val MAX_FILE_CHARS = 256 * 1024
        private const val MAX_LINES = 200
        private const val MAX_FIND_DEPTH = 12
        private const val MAX_FIND_RESULTS = 500
        private const val MAX_PATH_CHARS = 1024
    }
}