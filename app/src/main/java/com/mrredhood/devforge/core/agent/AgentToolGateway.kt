package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.DurableStateRepository
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

interface AgentTool {
    val definition: AgentToolDefinition

    suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult

    /** Optional exact pre-image hash used to bind approvals to the content reviewed. */
    suspend fun preconditionHash(context: AgentToolContext, request: AgentToolRequest): String? = null
}

class AgentToolRegistry {
    private val tools = linkedMapOf<AgentToolId, AgentTool>()

    fun register(tool: AgentTool): AgentToolRegistry {
        check(tools.put(tool.definition.id, tool) == null) { "Agent tool '${tool.definition.id.wireName}' is already registered." }
        return this
    }

    fun get(id: AgentToolId): AgentTool? = tools[id]

    fun definitions(): List<AgentToolDefinition> = tools.values.map { it.definition }
}

/** Provider-neutral execution boundary. Providers may propose tool calls, but this gateway owns authorization. */
class AgentToolGateway(
    private val registry: AgentToolRegistry,
    private val approvals: ApprovalRepository,
    private val durableState: DurableStateRepository,
    private val permissionMode: PermissionMode = PermissionMode.SOME,
) {
    suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult {
        val tool = registry.get(request.toolId) ?: return AgentToolResult.Failure("Tool '${request.toolId.wireName}' is not registered.")
        val contextFailure = validateContext(context, request)
        if (contextFailure != null) return contextFailure
        val argsBytes = request.argumentsJson.toByteArray(Charsets.UTF_8)
        if (argsBytes.size > AgentTaskPlanCodec.MAX_TOOL_ARGUMENT_BYTES) {
            return AgentToolResult.Failure("Tool arguments exceed the 64 KiB safety limit.")
        }

        val action = runCatching { actionRequest(context, request, tool) }.getOrElse {
            return AgentToolResult.Failure(it.message ?: "Unable to validate the agent action preconditions.")
        }
        val needsApproval = DefaultPolicy.requiresApproval(action, permissionMode)
        if (needsApproval) {
            val approvalId = UUID.randomUUID().toString()
            approvals.createPending(
                approvalId = approvalId,
                actionId = action.actionId,
                capability = action.capability,
                risk = action.risk,
                workspaceId = action.workspaceId,
                summary = action.summary,
                parametersHash = action.parametersHash,
                preconditionHash = action.preconditionHash,
                payload = approvalPayload(context, request, action.preconditionHash),
                expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_TTL_MS,
            )
            audit(
                context,
                request,
                tool.definition,
                eventType = "AGENT_TOOL_APPROVAL_REQUIRED",
                summary = "Approval requested for ${tool.definition.id.wireName}.",
                metadata = JSONObjectLike.metadata(
                    "approvalId" to approvalId,
                    "allowedPrefixes" to context.pathScope.canonicalPrefixes().joinToString(","),
                ),
            )
            return AgentToolResult.ApprovalRequired(approvalId, action.summary)
        }

        return executeTool(context, request, tool, approvalId = null)
    }

    suspend fun executeApproved(
        context: AgentToolContext,
        request: AgentToolRequest,
        approvalId: String,
    ): AgentToolResult {
        val tool = registry.get(request.toolId) ?: return AgentToolResult.Failure("Tool '${request.toolId.wireName}' is not registered.")
        val contextFailure = validateContext(context, request)
        if (contextFailure != null) return contextFailure
        val action = runCatching { actionRequest(context, request, tool) }.getOrElse {
            return AgentToolResult.Failure(it.message ?: "Unable to validate the approved action preconditions.")
        }
        val approval = approvals.observeById(approvalId).first()
            ?: return AgentToolResult.Failure("Approval '$approvalId' was not found.")
        if (approval.status != ApprovalRepository.STATUS_APPROVED) {
            return AgentToolResult.Failure("Approval '$approvalId' is not approved.")
        }
        if (approval.expiresAtEpochMs <= System.currentTimeMillis()) {
            approvals.expireDue()
            return AgentToolResult.Failure("Approval '$approvalId' has expired.")
        }
        if (approval.workspaceId != request.workspaceId || approval.actionId != action.actionId || approval.parametersHash != action.parametersHash || approval.preconditionHash != action.preconditionHash) {
            return AgentToolResult.Failure("Approval '$approvalId' does not match the current tool request or path scope.")
        }
        if (approval.capability != tool.definition.capability.name || approval.risk != tool.definition.risk.name) {
            return AgentToolResult.Failure("Approval '$approvalId' does not match the current tool capability.")
        }
        if (!approvals.claimApproved(approvalId)) {
            return AgentToolResult.Failure("Approval '$approvalId' is no longer executable.")
        }

        return executeTool(context, request, tool, approvalId)
    }

    private fun validateContext(context: AgentToolContext, request: AgentToolRequest): AgentToolResult.Failure? {
        if (request.workspaceId != context.workspaceId || request.taskId != context.taskId || request.stepIndex != context.stepIndex) {
            return AgentToolResult.Failure("Agent tool context does not match the requested task step.")
        }
        return null
    }

    private suspend fun executeTool(
        context: AgentToolContext,
        request: AgentToolRequest,
        tool: AgentTool,
        approvalId: String?,
    ): AgentToolResult {
        val result = runCatching { tool.execute(context, request) }
            .getOrElse { AgentToolResult.Failure(it.message ?: "Agent tool execution failed.") }
        val receipt = buildReceipt(context, request, tool.definition, result, approvalId)
        val finalResult = when (result) {
            is AgentToolResult.Success -> result.copy(receiptJson = receipt)
            else -> result
        }
        val metadata = JSONObjectLike.metadata(
            "approvalId" to approvalId,
            "step" to request.stepIndex,
            "tool" to request.toolId.wireName,
            "receipt" to receipt,
        )
        audit(
            context,
            request,
            tool.definition,
            eventType = if (result is AgentToolResult.Success) "AGENT_TOOL_COMPLETED" else "AGENT_TOOL_FAILED",
            summary = when (result) {
                is AgentToolResult.Success -> result.summary
                is AgentToolResult.Failure -> result.message
                is AgentToolResult.ApprovalRequired -> "Approval required."
            },
            metadata = metadata,
        )
        if (approvalId != null) {
            when (result) {
                is AgentToolResult.Success -> approvals.finishSuccess(approvalId)
                else -> approvals.finishFailure(approvalId)
            }
        }
        return finalResult
    }

    private suspend fun actionRequest(
        context: AgentToolContext,
        request: AgentToolRequest,
        tool: AgentTool,
    ): ActionRequest {
        val definition = tool.definition
        val actionId = "agent:${request.taskId}:step:${request.stepIndex}:${definition.id.wireName}"
        return ActionRequest(
            actionId = actionId,
            capability = definition.capability,
            risk = definition.risk,
            workspaceId = request.workspaceId,
            summary = "${definition.id.wireName}: ${definition.description}".take(500),
            parametersHash = hash(context, request),
            pathScope = context.pathScope,
            preconditionHash = tool.preconditionHash(context, request),
        )
    }

    private fun hash(context: AgentToolContext, request: AgentToolRequest): String {
        val canonical = buildString {
            append(request.workspaceId).append('\n')
            append(request.taskId).append('\n')
            append(request.stepIndex).append('\n')
            append(request.toolId.wireName).append('\n')
            append(request.argumentsJson).append('\n')
            append(context.pathScope.canonicalPrefixes().joinToString("|"))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun approvalPayload(context: AgentToolContext, request: AgentToolRequest, preconditionHash: String?): String =
        JSONObject()
            .put("tool", request.toolId.wireName)
            .put("workspaceId", request.workspaceId)
            .put("taskId", request.taskId)
            .put("stepIndex", request.stepIndex)
            .put("arguments", runCatching { JSONObject(request.argumentsJson) }.getOrDefault(request.argumentsJson))
            .put("allowedPrefixes", JSONArray(context.pathScope.canonicalPrefixes()))
            .put("preconditionHash", preconditionHash)
            .toString()
            .take(MAX_APPROVAL_PAYLOAD_CHARS)

    private fun buildReceipt(
        context: AgentToolContext,
        request: AgentToolRequest,
        definition: AgentToolDefinition,
        result: AgentToolResult,
        approvalId: String?,
    ): String {
        val affected = if (result is AgentToolResult.Success) result.affectedPaths.take(MAX_RECEIPT_PATHS) else emptyList()
        val summary = when (result) {
            is AgentToolResult.Success -> result.summary
            is AgentToolResult.Failure -> result.message
            is AgentToolResult.ApprovalRequired -> result.summary
        }
        return JSONObject()
            .put("taskId", context.taskId)
            .put("stepIndex", context.stepIndex)
            .put("tool", definition.id.wireName)
            .put("capability", definition.capability.name)
            .put("risk", definition.risk.name)
            .put("workspaceId", context.workspaceId)
            .put("allowedPrefixes", JSONArray(context.pathScope.canonicalPrefixes()))
            .put("affectedPaths", JSONArray(affected.map { context.pathScope.requireAllowed(it) }))
            .put("summary", summary.take(500))
            .put("approvalId", approvalId)
            .put("timestampEpochMs", System.currentTimeMillis())
            .toString()
            .take(MAX_RECEIPT_CHARS)
    }

    private suspend fun audit(
        context: AgentToolContext,
        request: AgentToolRequest,
        definition: AgentToolDefinition,
        eventType: String,
        summary: String,
        metadata: String,
    ) {
        durableState.recordAudit(
            AuditEventEntity(
                eventId = UUID.randomUUID().toString(),
                workspaceId = context.workspaceId,
                actionId = "agent:${request.taskId}:step:${request.stepIndex}:${definition.id.wireName}",
                capability = definition.capability.name,
                risk = definition.risk.name,
                eventType = eventType,
                summary = summary.take(500),
                metadataJson = metadata.take(32 * 1024),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private object JSONObjectLike {
        fun metadata(vararg entries: Pair<String, Any?>): String = buildString {
            append('{')
            entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                append('\"').append(key).append('\"').append(':')
                when (value) {
                    null -> append("null")
                    is Number, is Boolean -> append(value)
                    else -> append('\"').append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('\"')
                }
            }
            append('}')
        }
    }

    companion object {
        private const val APPROVAL_TTL_MS = 10L * 60L * 1000L
        private const val MAX_APPROVAL_PAYLOAD_CHARS = 64 * 1024
        private const val MAX_RECEIPT_CHARS = 64 * 1024
        private const val MAX_RECEIPT_PATHS = 16
    }
}
