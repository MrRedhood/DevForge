package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.workspace.WorkspaceContextScope
import com.mrredhood.devforge.core.workspace.WorkspaceContextService
import org.json.JSONObject

/** Internal, always-available workspace state lookup. It is intentionally not a user-toggleable tool. */
class WorkspaceContextToolProvider(context: Context) {
    private val service = WorkspaceContextService(context.applicationContext)

    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry =
        registry.register(GetWorkspaceContextTool())

    private inner class GetWorkspaceContextTool : AgentTool {
        override val definition = AgentToolDefinition(
            id = AgentToolId.GET_WORKSPACE_CONTEXT,
            description = "Read compact authoritative state for the active workspace. Use summary/files/git/build/editor/all scope as needed.",
            capability = Capability.READ_WORKSPACE,
            risk = RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(
            context: AgentToolContext,
            request: AgentToolRequest,
        ): AgentToolResult {
            val json = runCatching { JSONObject(request.argumentsJson) }.getOrElse {
                return AgentToolResult.Failure("Workspace context arguments are not valid JSON.")
            }
            val scope = runCatching {
                WorkspaceContextScope.valueOf(json.optString("scope", "SUMMARY").trim().uppercase())
            }.getOrElse {
                return AgentToolResult.Failure("Unsupported workspace context scope. Use summary, files, git, build, editor, or all.")
            }
            val limit = json.optInt("limit", 40).coerceIn(1, 120)
            val snapshot = service.snapshot(context.workspaceId, scope, limit)
                ?: return AgentToolResult.Failure("The active workspace is unavailable.")
            return AgentToolResult.Success(
                summary = "Workspace context read (" + scope.name.lowercase() + ").",
                output = snapshot.text,
            )
        }
    }
}
