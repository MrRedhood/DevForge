package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import org.json.JSONArray
import org.json.JSONObject

/** Typed coordination tools; no coordination tool can directly mutate workspace files. */
class AgentCoordinationToolProvider(
    private val coordination: AgentCoordinationService,
) {
    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry = registry
        .register(ReadSharedMemoryTool())
        .register(WriteSharedMemoryTool())
        .register(ListHandoffsTool())
        .register(CreateHandoffTool())
        .register(ClaimHandoffTool())
        .register(CompleteHandoffTool())

    private inner class ReadSharedMemoryTool : AgentTool {
        override val definition = AgentToolDefinition(AgentToolId.READ_SHARED_MEMORY, "Read recent shared workspace memory available to collaborating agents.", Capability.READ_WORKSPACE, RiskLevel.R0, false)
        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = runCatching {
            val args = JSONObject(request.argumentsJson)
            val key = args.optString("key").trim()
            val values = if (key.isBlank()) coordination.listMemory(context.workspaceId, MAX_READ_MEMORY) else listOfNotNull(coordination.getMemory(context.workspaceId, key))
            val json = JSONArray()
            values.take(MAX_READ_MEMORY).forEach { value ->
                json.put(JSONObject().put("key", value.key).put("content", value.content).put("sourceTaskId", value.sourceTaskId).put("updatedAtEpochMs", value.updatedAtEpochMs))
            }
            AgentToolResult.Success("Read " + values.size.coerceAtMost(MAX_READ_MEMORY) + " shared memory entries.", json.toString())
        }.getOrElse { AgentToolResult.Failure(it.message ?: "Unable to read shared memory.") }
    }

    private inner class WriteSharedMemoryTool : AgentTool {
        override val definition = AgentToolDefinition(AgentToolId.WRITE_SHARED_MEMORY, "Write one bounded shared workspace note for collaborating agents.", Capability.EDIT_FILES, RiskLevel.R1, true)
        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = runCatching {
            val args = JSONObject(request.argumentsJson)
            val entry = coordination.putMemory(context.workspaceId, args.optString("key"), args.optString("content"), context.taskId)
            AgentToolResult.Success("Updated shared memory '" + entry.key + "'.", JSONObject().put("key", entry.key).toString())
        }.getOrElse { AgentToolResult.Failure(it.message ?: "Unable to write shared memory.") }
    }

    private inner class ListHandoffsTool : AgentTool {
        override val definition = AgentToolDefinition(AgentToolId.LIST_HANDOFFS, "List pending or claimed handoffs for collaborating agents.", Capability.READ_WORKSPACE, RiskLevel.R0, false)
        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = runCatching {
            val values = coordination.availableHandoffs(context.workspaceId, context.taskId, MAX_READ_HANDOFFS)
            val json = JSONArray()
            values.forEach { handoff ->
                json.put(JSONObject().put("handoffId", handoff.handoffId).put("fromTaskId", handoff.fromTaskId).put("toTaskId", handoff.toTaskId).put("title", handoff.title).put("summary", handoff.summary).put("contextJson", handoff.contextJson).put("status", handoff.status))
            }
            AgentToolResult.Success("Found " + values.size + " available handoffs.", json.toString())
        }.getOrElse { AgentToolResult.Failure(it.message ?: "Unable to list handoffs.") }
    }

    private inner class CreateHandoffTool : AgentTool {
        override val definition = AgentToolDefinition(AgentToolId.CREATE_HANDOFF, "Create a bounded durable handoff for another agent or the next available collaborator.", Capability.EDIT_FILES, RiskLevel.R1, true)
        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = runCatching {
            val args = JSONObject(request.argumentsJson)
            val handoff = coordination.createHandoff(AgentHandoffDraft(
                workspaceId = context.workspaceId,
                fromTaskId = context.taskId,
                toTaskId = args.optString("toTaskId", "").takeIf(String::isNotBlank),
                title = args.optString("title"),
                summary = args.optString("summary"),
                contextJson = args.optString("contextJson", "{}"),
            ))
            AgentToolResult.Success("Created handoff '" + handoff.title + "'.", JSONObject().put("handoffId", handoff.handoffId).toString())
        }.getOrElse { AgentToolResult.Failure(it.message ?: "Unable to create handoff.") }
    }

    private inner class ClaimHandoffTool : AgentTool {
        override val definition = AgentToolDefinition(AgentToolId.CLAIM_HANDOFF, "Claim one available handoff for the current agent.", Capability.EDIT_FILES, RiskLevel.R1, true)
        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = runCatching {
            val id = JSONObject(request.argumentsJson).optString("handoffId").trim()
            require(id.isNotBlank()) { "Handoff ID is required." }
            require(coordination.claimHandoff(id, context.taskId)) { "Handoff is no longer available for this agent." }
            AgentToolResult.Success("Claimed handoff $id.")
        }.getOrElse { AgentToolResult.Failure(it.message ?: "Unable to claim handoff.") }
    }

    private inner class CompleteHandoffTool : AgentTool {
        override val definition = AgentToolDefinition(AgentToolId.COMPLETE_HANDOFF, "Mark a handoff completed after the receiving agent consumes it.", Capability.EDIT_FILES, RiskLevel.R1, true)
        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = runCatching {
            val id = JSONObject(request.argumentsJson).optString("handoffId").trim()
            require(id.isNotBlank()) { "Handoff ID is required." }
            require(coordination.completeHandoff(id, context.taskId)) { "Handoff is not claimed by this agent." }
            AgentToolResult.Success("Completed handoff $id.")
        }.getOrElse { AgentToolResult.Failure(it.message ?: "Unable to complete handoff.") }
    }

    companion object {
        private const val MAX_READ_MEMORY = 20
        private const val MAX_READ_HANDOFFS = 20
    }
}
