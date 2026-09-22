package com.mrredhood.devforge.core.ai

import android.content.ContentResolver
import android.net.Uri
import com.mrredhood.devforge.core.workspace.WorkspaceSearch
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AICommandRegistry {
    val commands: List<AICommandDefinition> = listOf(
        AICommandDefinition("help", description = "Show available AI commands", usage = "/help" , instruction = "List available DevForge AI commands and explain which are read-only or require approval."),
        AICommandDefinition("explain", description = "Explain code or a concept", usage = "/explain <code or topic>", instruction = "Explain the requested code or concept clearly, preserving relevant implementation details."),
        AICommandDefinition("debug", aliases = listOf("diagnose"), description = "Diagnose an error or failing behavior", usage = "/debug <problem>", instruction = "Diagnose the problem, identify likely causes, inspect relevant context, and propose a verification path."),
        AICommandDefinition("fix", description = "Propose a concrete fix", usage = "/fix <problem>", safety = AgentCommandSafety.PROPOSE_ACTION, instruction = "Analyze the problem and propose a minimal, reviewable fix. Never apply changes without an explicit action authorization."),
        AICommandDefinition("refactor", description = "Suggest a safe refactor", usage = "/refactor <target>", safety = AgentCommandSafety.PROPOSE_ACTION, instruction = "Design a behavior-preserving refactor, list affected files, and produce a diff-oriented plan before changes."),
        AICommandDefinition("optimize", description = "Find performance or size improvements", usage = "/optimize <target>", instruction = "Inspect the target for measurable performance, memory, I/O, build, or size improvements and rank practical changes."),
        AICommandDefinition("test", description = "Create or improve tests", usage = "/test <target>", safety = AgentCommandSafety.PROPOSE_ACTION, instruction = "Design or propose focused tests for the target, including edge cases and regression coverage."),
        AICommandDefinition("review", description = "Review code for correctness and risk", usage = "/review <target>", instruction = "Review the target for correctness, security, reliability, maintainability, and likely regressions. Do not modify files."),
        AICommandDefinition("summarize", aliases = listOf("summary"), description = "Summarize code, file, or discussion", usage = "/summarize <target>", instruction = "Produce a compact but technically useful summary of the requested target."),
        AICommandDefinition("docs", aliases = listOf("document"), description = "Draft documentation", usage = "/docs <target>", instruction = "Draft concise developer-facing documentation for the requested target."),
        AICommandDefinition("search", aliases = listOf("find"), description = "Search the workspace", usage = "/search <query>", instruction = "Search the workspace for relevant files, symbols, and references. Return paths and a concise explanation of matches."),
        AICommandDefinition("find", description = "Find a file, symbol, or reference", usage = "/find <query>", instruction = "Locate the best matching files or symbols in the workspace and explain where they are used."),
        AICommandDefinition("plan", aliases = listOf("design"), description = "Create an implementation plan", usage = "/plan <goal>", instruction = "Create a staged implementation plan with dependencies, affected areas, validation, and rollback considerations."),
        AICommandDefinition("implement", aliases = listOf("build-change"), description = "Design an implementation change", usage = "/implement <goal>", safety = AgentCommandSafety.PROPOSE_ACTION, instruction = "Translate the goal into concrete implementation steps and a reviewable change set. Do not silently apply side effects."),
        AICommandDefinition("generate", description = "Generate code or content", usage = "/generate <request>", instruction = "Generate the requested code or content while matching the repository's existing conventions."),
        AICommandDefinition("diff", aliases = listOf("changes"), description = "Inspect current changes", usage = "/diff", instruction = "Summarize current workspace changes and focus review on the highest-risk or most relevant differences."),
        AICommandDefinition("build", description = "Prepare a build request", usage = "/build <target>", safety = AgentCommandSafety.SIDE_EFFECT, instruction = "Prepare a typed build request. A real build dispatch must pass DevForge's capability and approval gates."),
        AICommandDefinition("commit", description = "Prepare a commit", usage = "/commit <message>", safety = AgentCommandSafety.SIDE_EFFECT, instruction = "Prepare a Git commit proposal with affected files and a concise commit message. Actual commit execution requires the Git capability and approval policy."),
        AICommandDefinition("run", aliases = listOf("execute"), description = "Prepare a validated execution request", usage = "/run <action>", safety = AgentCommandSafety.SIDE_EFFECT, instruction = "Prepare a typed execution request. Never execute arbitrary shell commands without the terminal capability and policy gate."),
    )

    private val byName = commands.flatMap { definition ->
        sequenceOf(definition.name to definition) + definition.aliases.map { it to definition }
    }.toMap()

    fun suggestions(prefix: String): List<AICommandDefinition> {
        val query = prefix.removePrefix("/").trimStart().lowercase(Locale.US)
        if (query.isBlank()) return commands
        return commands.filter { command ->
            command.name.startsWith(query) || command.aliases.any { it.startsWith(query) }
        }
    }

    fun parse(message: String): AICommandInvocation? {
        val first = message.trimStart().lineSequence().firstOrNull() ?: return null
        if (!first.startsWith('/')) return null
        val pieces = first.removePrefix("/").trim().split(Regex("\\s+"), limit = 2)
        val command = byName[pieces.firstOrNull()?.lowercase(Locale.US)] ?: return null
        val arguments = pieces.getOrNull(1).orEmpty() + message.trimStart().substringAfter('\n', "").let { if (it.isBlank()) "" else "\n$it" }
        return AICommandInvocation(command, arguments.trim())
    }

    suspend fun resolveMentions(
        resolver: ContentResolver,
        workspaceRoot: Uri?,
        message: String,
        maxMentions: Int = 8,
    ): List<FileMention> = withContext(Dispatchers.IO) {
        if (workspaceRoot == null) return@withContext emptyList()
        val tokens = Regex("(?<!\\S)@([A-Za-z0-9_./\\-]+)").findAll(message)
            .map { it.groupValues[1] }
            .distinct()
            .take(maxMentions)
            .toList()
        if (tokens.isEmpty()) return@withContext emptyList()

        val search = WorkspaceSearch(resolver)
        tokens.mapNotNull { query ->
            val result = runCatching { search.search(workspaceRoot, query, 8) }
                .getOrDefault(emptyList())
                .firstOrNull { !it.isDirectory && (it.name.equals(query, true) || it.name.contains(query, true)) }
                ?: return@mapNotNull FileMention("@$query", query, null, query)
            val content = runCatching {
                resolver.openInputStream(result.uri)?.use { input ->
                    readBoundedText(input, MAX_MENTION_BYTES)
                }
            }.getOrNull()
            FileMention("@$query", query, result.uri.toString(), result.name, content)
        }
    }

    private fun readBoundedText(input: java.io.InputStream, maxBytes: Int): String {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private const val MAX_MENTION_BYTES = 32 * 1024
}

object AgentCommandCatalog {
    val commands: List<AICommandDefinition> = AICommandRegistry.commands

    fun systemSummary(): String = buildString {
        append("DevForge command catalog:\n")
        commands.forEach { command ->
            append("/").append(command.name)
                .append(" — ").append(command.description)
                .append(" [").append(command.safety.name).append("]\n")
        }
    }
}
