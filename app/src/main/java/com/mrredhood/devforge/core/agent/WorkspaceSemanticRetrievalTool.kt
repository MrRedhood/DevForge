package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.workspace.WorkspaceSemanticRetrievalService
import org.json.JSONArray
import org.json.JSONObject

class WorkspaceSemanticRetrievalToolProvider(context: Context) {
    private val service = WorkspaceSemanticRetrievalService(context)

    fun registerAll(registry: AgentToolRegistry) {
        registry.register(Tool(service))
    }

    private class Tool(
        private val service: WorkspaceSemanticRetrievalService,
    ) : AgentTool {
        override val definition = AgentToolDefinition(
            AgentToolId.RETRIEVE_RELEVANT_CONTEXT,
            "Rank relevant workspace files and indexed symbols for a coding question without loading whole files.",
            Capability.READ_WORKSPACE,
            RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(
            context: AgentToolContext,
            request: AgentToolRequest,
        ): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val query = args.optString("query").trim()
            require(query.isNotBlank()) { "Retrieval query cannot be empty." }
            val prefix = args.optString("pathPrefix", "").trim()
            val limit = args.optInt("limit", 12).coerceIn(1, 40)
            val matches = service.retrieve(context.workspaceId, query, prefix, limit)
            val array = JSONArray()
            matches.forEach { match ->
                array.put(
                    JSONObject()
                        .put("path", match.path)
                        .put("kind", match.kind)
                        .put("line", match.line ?: JSONObject.NULL)
                        .put("score", match.score),
                )
            }
            AgentToolResult.Success(
                summary = "Retrieved " + matches.size + " relevant workspace matches.",
                output = JSONObject()
                    .put("query", query)
                    .put("matches", array)
                    .toString(),
                affectedPaths = matches.map { it.path }.distinct().take(12),
            )
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to retrieve relevant workspace context.")
        }
    }
}
