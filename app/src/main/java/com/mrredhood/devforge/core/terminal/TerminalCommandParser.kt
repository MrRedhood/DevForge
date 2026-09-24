package com.mrredhood.devforge.core.terminal

sealed interface TerminalParsedCommand {
    data class External(val command: TerminalCommand) : TerminalParsedCommand
    data class ChangeDirectory(val path: String) : TerminalParsedCommand
    data object Clear : TerminalParsedCommand
    data object History : TerminalParsedCommand
    data object Help : TerminalParsedCommand
    data class Shell(val commandLine: String) : TerminalParsedCommand
}

object TerminalCommandParser {
    fun parse(line: String, workingDirectory: String, timeoutMs: Long, sessionId: String?): TerminalParsedCommand {
        val normalized = line.trim()
        require(normalized.isNotEmpty()) { "Enter a command." }
        val tokens = runCatching { tokenize(normalized) }.getOrElse {
            return TerminalParsedCommand.Shell(normalized)
        }
        require(tokens.isNotEmpty()) { "Enter a command." }
        return when (val name = tokens.first().lowercase()) {
            "cd" -> {
                require(tokens.size <= 2) { "Usage: cd [directory]" }
                TerminalParsedCommand.ChangeDirectory(tokens.getOrElse(1) { "" }.ifBlank { "." })
            }
            "clear", "cls" -> TerminalParsedCommand.Clear
            "history" -> TerminalParsedCommand.History
            "help" -> TerminalParsedCommand.Help
            else -> TerminalParsedCommand.Shell(normalized)
        }
    }

    fun parseToolCommand(
        line: String,
        workingDirectory: String,
        timeoutMs: Long,
        sessionId: String?,
    ): TerminalCommand {
        val normalized = line.trim()
        require(normalized.isNotBlank()) { "Enter a command." }
        require('\u0000' !in normalized && '\r' !in normalized && '\n' !in normalized) {
            "Control characters are not allowed."
        }
        return TerminalCommand(
            executable = TerminalExecutable.SHELL,
            args = listOf("-c", normalized),
            workingDirectory = workingDirectory.trim().removePrefix("./").trim('/'),
            timeoutMs = timeoutMs,
            sessionId = sessionId,
        ).also(TerminalCommandPolicy::validate)
    }

    private fun tokenize(line: String): List<String> {
        require(line.length <= TerminalCommandPolicy.MAX_COMMAND_BYTES) { "command is too long" }
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        line.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> escaped = true
                quote != null -> if (ch == quote) quote = null else current.append(ch)
                ch == '\'' || ch == '"' -> quote = ch
                ch == ' ' || ch == '\t' -> if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
                ch == '|' || ch == '&' || ch == ';' || ch == '>' || ch == '<' ->
                    throw IllegalArgumentException("shell operators are not supported")
                ch == '\u0000' || ch == '\r' || ch == '\n' ->
                    throw IllegalArgumentException("control characters are not allowed")
                else -> current.append(ch)
            }
        }
        require(!escaped && quote == null) { "unclosed quote or escape" }
        if (current.isNotEmpty()) tokens += current.toString()
        require(tokens.size <= TerminalCommandPolicy.MAX_ARGS + 1) { "too many arguments" }
        return tokens
    }
}

