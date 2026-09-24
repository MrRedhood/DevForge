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
        require(commandLine.isNotBlank()) { "Terminal command is empty." }
        require(commandLine.length <= MAX_COMMAND_CHARS) { "Terminal command exceeds the 8 KiB limit." }
        require('\u0000' !in commandLine && '\r' !in commandLine && '\n' !in commandLine) {
            "Control characters are not allowed."
        }
        return executeShell(remote, workingDirectory, commandLine.trim())
    }

    private suspend fun executeShell(
        remote: GitHubWorkspaceRemote,
        cwd: String,
        commandLine: String,
    ): String {
        val segments = splitShell(commandLine)
        var previousExit = 0
        var previousOutput = ""
        val output = StringBuilder()

        for (index in segments.indices) {
            val segment = segments[index]
            val shouldRun = when (segment.operator) {
                null, ";" -> true
                "&&" -> previousExit == 0
                "||" -> previousExit != 0
                "|" -> true
                else -> true
            }
            if (!shouldRun) continue

            val input = if (segment.operator == "|") previousOutput else ""
            val result = executeSimple(remote, cwd, segment.command, input)
            previousOutput = result
            previousExit = 0

            if (segment.operator != "|" || index == segments.lastIndex) {
                if (output.isNotEmpty() && result.isNotBlank()) output.append('\n')
                if (result.isNotBlank()) output.append(result)
            }
        }

        return if (output.isNotEmpty()) output.toString().trimEnd() else previousOutput
    }

    private suspend fun executeSimple(
        remote: GitHubWorkspaceRemote,
        cwd: String,
        commandLine: String,
        stdin: String,
    ): String {
        val tokens = tokenize(commandLine)
        if (tokens.isEmpty()) return stdin
        val command = tokens.first().lowercase()
        val args = tokens.drop(1)
        val input = stdin.takeIf { it.isNotBlank() }

        return when (command) {
            "pwd" -> "/workspace" + if (cwd.isBlank()) "" else "/" + cwd
            "ls" -> list(remote, cwd, args)
            "cat" -> if (args.none { !it.startsWith("-") } && input != null) input else cat(remote, cwd, args)
            "head" -> headTail(remote, cwd, args, true, input)
            "tail" -> headTail(remote, cwd, args, false, input)
            "wc" -> wordCount(remote, cwd, args, input)
            "grep" -> grep(remote, cwd, args, input)
            "find" -> find(remote, cwd, args)
            "echo" -> args.joinToString(" ")
            "printf" -> printf(args)
            "sort" -> sortText(remote, cwd, args, input)
            "uniq" -> uniqText(remote, cwd, args, input)
            "cut" -> cutText(args, input ?: cat(remote, cwd, args))
            "tr" -> trText(args, input ?: cat(remote, cwd, args))
            "sed" -> sedText(args, input ?: cat(remote, cwd, args))
            "env", "printenv" -> environment(remote, cwd, args)
            "which" -> which(args)
            "true" -> ""
            "false" -> "false"
            "sleep" -> {
                kotlinx.coroutines.delay((args.firstOrNull()?.toLongOrNull()?.coerceIn(0L, 10L) ?: 0L) * 1000L)
                ""
            }
            "ps" -> "PID CMD\n1 devforge-github-virtual-terminal"
            "basename" -> basename(resolvePath(cwd, args.lastOrNull() ?: throw IllegalArgumentException("basename: missing operand")))
            "dirname" -> dirname(resolvePath(cwd, args.lastOrNull() ?: throw IllegalArgumentException("dirname: missing operand")))
            "realpath" -> "/workspace/" + resolvePath(cwd, args.lastOrNull() ?: ".")
            "readlink" -> throw IllegalArgumentException("readlink: symbolic links are not available in a GitHub-backed workspace.")
            "sha256sum" -> sha256(remote, cwd, args)
            "stat" -> stat(remote, cwd, args)
            "cmp" -> compare(remote, cwd, args, false)
            "diff" -> compare(remote, cwd, args, true)
            "date" -> java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date())
            "uname" -> "Linux DevForge GitHub virtual workspace"
            "whoami", "id" -> "github:" + remote.owner
            "git" -> git(remote, args)
            else -> throw IllegalArgumentException(
                "Command '$command' is not available in the GitHub virtual terminal. " +
                    "Use the DevForge file/folder tools for workspace mutations.",
            )
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

    private suspend fun headTail(
        remote: GitHubWorkspaceRemote,
        cwd: String,
        args: List<String>,
        head: Boolean,
        stdin: String? = null,
    ): String {
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
        val path = positional.lastOrNull()
        val source = if (path == null && stdin != null) stdin else cat(remote, cwd, listOf(path ?: throw IllegalArgumentException("missing file operand")))
        val lines = source.lineSequence().toList()
        return if (head) lines.take(count).joinToString("\n") else lines.takeLast(count).joinToString("\n")
    }

    private suspend fun wordCount(
        remote: GitHubWorkspaceRemote,
        cwd: String,
        args: List<String>,
        stdin: String? = null,
    ): String {
        val path = args.lastOrNull { !it.startsWith("-") }
        val text = if (path == null && stdin != null) stdin else cat(remote, cwd, listOf(path ?: throw IllegalArgumentException("wc: missing file operand")))
        val lines = text.count { it == '\n' }
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val bytes = text.toByteArray(Charsets.UTF_8).size
        return lines.toString() + " " + words + " " + bytes + " " + resolvePath(cwd, path.orEmpty())
    }

    private suspend fun grep(
        remote: GitHubWorkspaceRemote,
        cwd: String,
        args: List<String>,
        stdin: String? = null,
    ): String {
        val values = args.filterNot { it == "-n" || it == "-i" }
        val pattern = values.firstOrNull() ?: throw IllegalArgumentException("grep: missing pattern")
        val path = values.drop(1).lastOrNull()
        val text = if (path == null && stdin != null) stdin else cat(remote, cwd, listOf(path ?: throw IllegalArgumentException("grep: missing file operand")))
        val ignoreCase = "-i" in args
        val regex = runCatching { Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }
            .getOrElse { Regex(Regex.escape(pattern), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }
        return text.lineSequence().mapIndexedNotNull { lineIndex, line ->
            if (regex.containsMatchIn(line)) {
                if ("-n" in args) (lineIndex + 1).toString() + ":" + line else line
            } else null
        }.take(MAX_LINES).joinToString("\n")
    }

    private fun printf(args: List<String>): String {
        if (args.isEmpty()) return ""
        val format = args.first()
        val values = args.drop(1)
        var valueIndex = 0
        return buildString {
            var index = 0
            while (index < format.length) {
                if (format[index] == '%' && index + 1 < format.length) {
                    when (format[index + 1]) {
                        's' -> {
                            append(values.getOrNull(valueIndex).orEmpty())
                            valueIndex++
                            index += 2
                        }
                        '%' -> {
                            append('%')
                            index += 2
                        }
                        else -> {
                            append(format[index])
                            index++
                        }
                    }
                } else {
                    append(format[index])
                    index++
                }
            }
        }
    }

    private suspend fun sortText(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>, stdin: String?): String {
        val path = args.lastOrNull { !it.startsWith("-") }
        val source = if (path == null && stdin != null) stdin else cat(remote, cwd, listOf(path ?: throw IllegalArgumentException("sort: missing file operand")))
        return source.lineSequence()
            .sortedWith(if ("-r" in args) compareByDescending<String> { it } else compareBy<String> { it })
            .joinToString("\n")
    }

    private suspend fun uniqText(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>, stdin: String?): String {
        val path = args.lastOrNull { !it.startsWith("-") }
        val source = if (path == null && stdin != null) stdin else cat(remote, cwd, listOf(path ?: throw IllegalArgumentException("uniq: missing file operand")))
        val output = mutableListOf<String>()
        source.lineSequence().forEach { line ->
            if (output.lastOrNull() != line) output += line
        }
        return output.joinToString("\n")
    }

    private fun cutText(args: List<String>, input: String): String {
        val delimiter = args.windowed(2).firstOrNull { it[0] == "-d" }?.getOrNull(1)?.firstOrNull() ?: '\t'
        val fieldSpec = args.windowed(2).firstOrNull { it[0] == "-f" }?.getOrNull(1) ?: "1"
        val fields = fieldSpec.split(',').mapNotNull { it.toIntOrNull() }.map { it - 1 }
        return input.lineSequence().map { line ->
            fields.mapNotNull { line.split(delimiter).getOrNull(it) }.joinToString(delimiter.toString())
        }.joinToString("\n")
    }

    private fun trText(args: List<String>, input: String): String {
        val from = args.firstOrNull().orEmpty()
        val to = args.getOrNull(1).orEmpty()
        if (from.isEmpty()) return input
        val map = from.mapIndexed { index, char ->
            char to (to.getOrNull(index) ?: to.lastOrNull() ?: char)
        }.toMap()
        return input.map { map[it] ?: it }.joinToString("")
    }

    private fun sedText(args: List<String>, input: String): String {
        val expression = args.firstOrNull { it.startsWith("s/") && it.count { char -> char == '/' } >= 3 }
            ?: throw IllegalArgumentException("sed: only simple s/from/to/[g] expressions are supported in the GitHub virtual terminal.")
        val parts = expression.removePrefix("s/").split('/')
        val from = parts.getOrNull(0).orEmpty()
        val to = parts.getOrNull(1).orEmpty()
        val global = parts.getOrNull(2).orEmpty().contains('g')
        require(from.isNotEmpty()) { "sed: empty search pattern." }
        return if (global) input.replace(from, to) else input.replaceFirst(from, to)
    }

    private fun environment(remote: GitHubWorkspaceRemote, cwd: String, args: List<String>): String {
        val values = linkedMapOf(
            "PWD" to "/workspace" + if (cwd.isBlank()) "" else "/" + cwd,
            "GITHUB_REPOSITORY" to remote.owner + "/" + remote.repository,
            "GITHUB_REF_NAME" to remote.branch,
        )
        val key = args.firstOrNull { !it.startsWith("-") }
        return if (key != null) values[key].orEmpty() else values.entries.joinToString("\n") { it.key + "=" + it.value }
    }

    private fun which(args: List<String>): String {
        val command = args.firstOrNull() ?: throw IllegalArgumentException("which: missing command")
        val supported = setOf(
            "pwd","ls","cat","head","tail","wc","grep","find","echo","printf","sort","uniq","cut","tr","sed",
            "env","printenv","which","true","false","sleep","ps","basename","dirname","realpath","readlink",
            "sha256sum","stat","cmp","diff","date","uname","whoami","id","git",
        )
        return if (command in supported) "/usr/bin/$command" else ""
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

    private fun tokenize(commandLine: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var single = false
        var double = false
        var escaped = false
        commandLine.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' && !single -> escaped = true
                ch == '\'' && !double -> single = !single
                ch == '"' && !single -> double = !double
                ch.isWhitespace() && !single && !double -> {
                    if (current.isNotEmpty()) {
                        result += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        require(!single && !double && !escaped) { "Unterminated shell quote or escape." }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private data class ShellSegment(
        val command: String,
        val operator: String?,
    )

    private fun splitShell(commandLine: String): List<ShellSegment> {
        val result = mutableListOf<ShellSegment>()
        val current = StringBuilder()
        var single = false
        var double = false
        var escaped = false
        var pendingOperator: String? = null
        var index = 0

        fun flush() {
            if (current.isNotEmpty()) {
                result += ShellSegment(current.toString().trim(), pendingOperator)
                current.clear()
                pendingOperator = null
            }
        }

        while (index < commandLine.length) {
            val ch = commandLine[index]
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                    index++
                }
                ch == '\\' && !single -> {
                    current.append(ch)
                    escaped = true
                    index++
                }
                ch == '\'' && !double -> {
                    current.append(ch)
                    single = !single
                    index++
                }
                ch == '"' && !single -> {
                    current.append(ch)
                    double = !double
                    index++
                }
                !single && !double && ch == '>' -> {
                    throw IllegalArgumentException("Output redirection is not available in a GitHub-backed workspace. Use create_file or write_file for file writes.")
                }
                !single && !double && ch == '<' -> {
                    throw IllegalArgumentException("Input redirection is not available in a GitHub-backed workspace.")
                }
                !single && !double && ch == '|' -> {
                    flush()
                    if (index + 1 < commandLine.length && commandLine[index + 1] == '|') {
                        pendingOperator = "||"
                        index += 2
                    } else {
                        pendingOperator = "|"
                        index++
                    }
                }
                !single && !double && ch == '&' && index + 1 < commandLine.length && commandLine[index + 1] == '&' -> {
                    flush()
                    pendingOperator = "&&"
                    index += 2
                }
                !single && !double && ch == ';' -> {
                    flush()
                    pendingOperator = ";"
                    index++
                }
                else -> {
                    current.append(ch)
                    index++
                }
            }
        }

        require(!single && !double && !escaped) { "Unterminated shell quote or escape." }
        flush()
        return result
    }



    companion object {
        private const val MAX_COMMAND_CHARS = 8 * 1024
        private const val MAX_FILE_CHARS = 256 * 1024
        private const val MAX_LINES = 200
        private const val MAX_FIND_DEPTH = 12
        private const val MAX_FIND_RESULTS = 500
        private const val MAX_PATH_CHARS = 1024
    }
}