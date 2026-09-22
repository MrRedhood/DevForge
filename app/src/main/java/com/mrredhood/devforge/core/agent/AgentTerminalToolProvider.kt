package com.mrredhood.devforge.core.agent

import android.content.Context
import android.net.Uri
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.terminal.TerminalCapability
import com.mrredhood.devforge.core.terminal.TerminalCapabilityResult
import com.mrredhood.devforge.core.terminal.TerminalCommandParser
import com.mrredhood.devforge.core.terminal.TerminalCommandPolicy
import com.mrredhood.devforge.core.terminal.TerminalExecution
import org.json.JSONObject

/** AI-facing terminal tool. The outer agent gateway owns authorization and approval. */
class AgentTerminalToolProvider(context: Context) {
    private val appContext = context.applicationContext
    private val database = DevForgeDatabase.get(appContext)
    private val approvals = ApprovalRepository(database.approvalDao())
    private val durableState = DurableStateRepository(database)
    private val capability = TerminalCapability(
        context = appContext,
        approvals = approvals,
        durableState = durableState,
        permissionMode = PermissionMode.AUTONOMOUS,
    )

    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry = registry.register(RunCommandTool())

    private inner class RunCommandTool : AgentTool {
        override val definition = AgentToolDefinition(
            id = AgentToolId.RUN_COMMAND,
            description = "Run one bounded allowlisted terminal command in the selected workspace and return bounded output, exit code, status and duration. Shell operators are not accepted by this AI tool; direct user terminal input remains a real shell.",
            capability = Capability.RUN_TERMINAL,
            risk = RiskLevel.R2,
            sideEffecting = true,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val commandLine = args.optString("command").trim()
            require(commandLine.isNotBlank()) { "The AI terminal command cannot be empty." }
            require(commandLine.length <= TerminalCommandPolicy.MAX_COMMAND_BYTES) { "The AI terminal command exceeds the 8 KiB limit." }

            val workingDirectory = args.optString("workingDirectory").trim().removePrefix("./").trim('/')
            if (workingDirectory.isNotBlank()) context.pathScope.requireAllowed(workingDirectory)

            val timeoutMs = args.optLong("timeoutMs", TerminalCommandPolicy.DEFAULT_TIMEOUT_MS)
                .coerceIn(250L, TerminalCommandPolicy.MAX_TIMEOUT_MS)

            val workspace = database.workspaceDao().findById(context.workspaceId)
                ?: return AgentToolResult.Failure("The selected workspace is unavailable.")

            val command = TerminalCommandParser.parseToolCommand(
                line = commandLine,
                workingDirectory = workingDirectory,
                timeoutMs = timeoutMs,
                sessionId = context.taskId + ":" + context.stepIndex,
            )

            capability.prepareWorkspace(
                workspaceId = workspace.id,
                workspaceRoot = Uri.parse(workspace.treeUri),
            ).getOrElse { return AgentToolResult.Failure(it.message ?: "Unable to prepare the terminal workspace.") }

            val output = StringBuilder()
            val result = capability.executeAuthorizedStreaming(
                workspaceId = workspace.id,
                command = command,
            ) { chunk ->
                val remaining = TerminalCommandPolicy.MAX_OUTPUT_BYTES * 4 - output.length
                if (remaining > 0) output.append(chunk.take(remaining))
            }

            when (result) {
                is TerminalCapabilityResult.Completed -> {
                    val execution = result.execution
                    AgentToolResult.Success(
                        summary = "Command finished with " + execution.status.name.lowercase() +
                            ", exit " + (execution.exitCode ?: -1) + ", " + execution.durationMs + " ms.",
                        output = JSONObject()
                            .put("command", commandLine)
                            .put("workingDirectory", workingDirectory)
                            .put("status", execution.status.name.lowercase())
                            .put("exitCode", execution.exitCode ?: JSONObject.NULL)
                            .put("durationMs", execution.durationMs)
                            .put("output", output.toString())
                            .toString(),
                    )
                }
                is TerminalCapabilityResult.ApprovalRequired ->
                    AgentToolResult.Failure("Terminal execution unexpectedly requested a nested approval.")
                is TerminalCapabilityResult.Failure -> AgentToolResult.Failure(result.message)
            }
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "AI terminal command failed.")
        }
    }
}
