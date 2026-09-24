package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.security.SecretRedactor
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.workspace.WorkspaceContextVersion
import com.mrredhood.devforge.core.workspace.WorkspaceContextLedgerRepository
import com.mrredhood.devforge.core.workspace.WorkspaceChangeBus
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

interface AgentTool {
    val definition: AgentToolDefinition

    suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult

    /** Optional exact pre-image hash used to bind approvals to the content reviewed. */
    suspend fun preconditionHash(context: AgentToolContext, request: AgentToolRequest): String? = null

    /** Workspace-relative paths that will be mutated by this action. */
    suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> = emptyList()
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
    private val coordination: AgentCoordinationService,
    private val toolSettings: ToolSettingsStore,
    private val permissionMode: PermissionMode = PermissionMode.SOME,
    private val contextLedger: WorkspaceContextLedgerRepository,
) {
    private val cancelledTasks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val activeTaskJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /** Cancels the active execution job for a task and prevents any later tool from starting. */
    fun cancelTask(taskId: String) {
        if (taskId.isBlank()) return
        cancelledTasks += taskId
        activeTaskJobs[taskId]?.cancel(CancellationException("AI task paused by user"))
    }

    fun clearTaskCancellation(taskId: String) {
        cancelledTasks.remove(taskId)
    }

    private fun ensureTaskActive(taskId: String) {
        if (cancelledTasks.contains(taskId)) {
            throw CancellationException("AI task paused by user")
        }
        currentCoroutineContext().ensureActive()
    }

    suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult {
        ensureTaskActive(request.taskId)
        activeTaskJobs[request.taskId] = currentCoroutineContext()[Job] ?: return AgentToolResult.Failure("AI tool execution is not attached to a cancellable task.")
        try {
            return executeInternal(context, request)
        } finally {
            activeTaskJobs.remove(request.taskId, currentCoroutineContext()[Job])
        }
    }

    private suspend fun executeInternal(context: AgentToolContext, request: AgentToolRequest): AgentToolResult {
        val tool = registry.get(request.toolId) ?: return AgentToolResult.Failure("Tool '" + request.toolId.wireName + "' is not registered.")
        if (DevForgeToolCatalog.isUserTool(request.toolId) && !toolSettings.isEnabled(request.toolId)) {
            return AgentToolResult.Failure("Tool '" + request.toolId.wireName + "' is disabled. Enable it in More → AI Tools.")
        }
        val contextFailure = validateContext(context, request)
        if (contextFailure != null) return contextFailure
        val argsBytes = request.argumentsJson.toByteArray(Charsets.UTF_8)
        if (argsBytes.size > AgentTaskPlanCodec.MAX_TOOL_ARGUMENT_BYTES) {
            return AgentToolResult.Failure("Tool arguments exceed the 64 KiB safety limit.")
        }

        val action = runCatching { actionRequest(context, request, tool) }.getOrElse {
            return AgentToolResult.Failure(it.message ?: "Unable to validate the agent action preconditions.")
        }
        val needsApproval = when {
            permissionMode == PermissionMode.NEVER -> true
            permissionMode == PermissionMode.AUTONOMOUS &&
                action.capability == com.mrredhood.devforge.core.policy.Capability.EDIT_FILES -> false
            else -> DefaultPolicy.requiresApproval(action, permissionMode)
        }
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
                expiresAtEpochMs = System.currentTimeMillis() + com.mrredhood.devforge.core.storage.ApprovalRepository.APPROVAL_WINDOW_MS,
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

        return executeTool(context, request, tool, approvalId = null, expectedPreconditionHash = action.preconditionHash)
    }

    suspend fun executeApproved(
        context: AgentToolContext,
        request: AgentToolRequest,
        approvalId: String,
    ): AgentToolResult {
        ensureTaskActive(request.taskId)
        activeTaskJobs[request.taskId] = currentCoroutineContext()[Job] ?: return AgentToolResult.Failure("AI tool execution is not attached to a cancellable task.")
        try {
            return executeApprovedInternal(context, request, approvalId)
        } finally {
            activeTaskJobs.remove(request.taskId, currentCoroutineContext()[Job])
        }
    }

    private suspend fun executeApprovedInternal(
        context: AgentToolContext,
        request: AgentToolRequest,
        approvalId: String,
    ): AgentToolResult {
        val tool = registry.get(request.toolId) ?: return AgentToolResult.Failure("Tool '" + request.toolId.wireName + "' is not registered.")
        if (DevForgeToolCatalog.isUserTool(request.toolId) && !toolSettings.isEnabled(request.toolId)) {
            return AgentToolResult.Failure("Tool '" + request.toolId.wireName + "' is disabled. Enable it in More → AI Tools.")
        }
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

        return executeTool(context, request, tool, approvalId, action.preconditionHash)
    }

    private fun validateContext(context: AgentToolContext, request: AgentToolRequest): AgentToolResult.Failure? {
        if (request.workspaceId != context.workspaceId || request.taskId != context.taskId || request.stepIndex != context.stepIndex) {
            return AgentToolResult.Failure("Agent tool context does not match the requested task step.")
        }
        if (!AgentAccessRules.canUse(request.toolId, context.access)) {
            val required = AgentAccessRules.requiredFor(request.toolId).joinToString(", ") { it.label }
            return AgentToolResult.Failure("Agent access does not allow '${request.toolId.wireName}'. Required access: $required.")
        }
        return null
    }

    private suspend fun executeTool(
        context: AgentToolContext,
        request: AgentToolRequest,
        tool: AgentTool,
        approvalId: String?,
        expectedPreconditionHash: String?,
    ): AgentToolResult {
        val mutationPaths = runCatching { tool.mutationPaths(context, request).map { context.pathScope.requireAllowed(it) }.distinct() }
            .getOrElse { return AgentToolResult.Failure(it.message ?: "Unable to determine mutation paths.") }
        val acquired = mutableListOf<String>()
        for (path in mutationPaths) {
            if (!coordination.acquireFileLease(context.workspaceId, context.taskId, path)) {
                acquired.forEach { runCatching { coordination.releaseFileLease(context.workspaceId, context.taskId, it) } }
                return AgentToolResult.Failure("File '$path' is currently reserved by another agent. Retry after it finishes or is released.")
            }
            acquired += path
        }
        if (expectedPreconditionHash != null) {
            val currentPrecondition = runCatching { tool.preconditionHash(context, request) }.getOrElse {
                acquired.forEach { runCatching { coordination.releaseFileLease(context.workspaceId, context.taskId, it) } }
                return AgentToolResult.Failure(it.message ?: "Unable to revalidate the file precondition.")
            }
            if (currentPrecondition != expectedPreconditionHash) {
                acquired.forEach { runCatching { coordination.releaseFileLease(context.workspaceId, context.taskId, it) } }
                return AgentToolResult.Failure("The file changed while the action was waiting for its mutation lease. The patch must be regenerated.")
            }
        }
        ensureTaskActive(context.taskId)
        val result = try {
            try {
                tool.execute(context, request)
            } catch (cancelled: CancellationException) {
                if (approvalId != null) {
                    runCatching { approvals.finishFailure(approvalId) }
                }
                throw cancelled
            } catch (error: Throwable) {
                AgentToolResult.Failure(error.message ?: "Agent tool execution failed.")
            }
        } finally {
            acquired.forEach { runCatching { coordination.releaseFileLease(context.workspaceId, context.taskId, it) } }
        }
        ensureTaskActive(context.taskId)
        val receipt = buildReceipt(context, request, tool.definition, result, approvalId)
        val finalResult = when (result) {
            is AgentToolResult.Success -> result.copy(receiptJson = receipt)
            else -> result
        }
        if (result is AgentToolResult.Success) {
            if (result.affectedPaths.isNotEmpty()) {
                contextLedger.recordAccess(
                    workspaceId = context.workspaceId,
                    contextVersion = WorkspaceContextVersion.current(context.workspaceId),
                    paths = result.affectedPaths,
                )
            }
            if (tool.definition.sideEffecting) {
                WorkspaceContextVersion.invalidate(context.workspaceId)
                WorkspaceChangeBus.emit(context.workspaceId, result.affectedPaths)
            }
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
        val summary = if (definition.id == AgentToolId.PATCH_FILE) {
            runCatching {
                val patch = AgentFilePatchCodec.decode(request.argumentsJson)
                "Apply patch to " + patch.path + ": " + patch.summary.ifBlank { "reviewed file content change" }
            }.getOrDefault("Apply structured file patch.")
        } else if (definition.id == AgentToolId.DELETE_PATH) {
            runCatching {
                val path = JSONObject(request.argumentsJson).optString("path").trim()
                "Delete workspace path: " + path.ifBlank { "(path not provided)" }
            }.getOrDefault("Delete one workspace file or folder.")
        } else if (definition.id == AgentToolId.RUN_COMMAND) {
            runCatching {
                val command = JSONObject(request.argumentsJson).optString("command").trim()
                "Run terminal command: " + command.ifBlank { "(command not provided)" }
            }.getOrDefault("Run a bounded terminal command.")
        } else {
            definition.id.wireName + ": " + definition.description
        }
        return ActionRequest(
            actionId = actionId,
            capability = definition.capability,
            risk = definition.risk,
            workspaceId = request.workspaceId,
            summary = summary.take(500),
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
            append(context.pathScope.canonicalPrefixes().joinToString("|")).append('\n')
            append(context.access.map(AgentAccess::name).sorted().joinToString("|"))
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
            .put("access", JSONArray(context.access.map(AgentAccess::name).sorted()))
            .put("preconditionHash", preconditionHash)
            .put("executionMode", if (request.taskId.startsWith("chat-")) "CHAT" else "AGENT")
            .toString()
            .let { SecretRedactor.redact(it, MAX_APPROVAL_PAYLOAD_CHARS) }

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
            .put("summary", SecretRedactor.redact(summary, 500))
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
                summary = SecretRedactor.redact(summary, 500),
                metadataJson = SecretRedactor.redact(metadata, 32 * 1024),
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

    companion object {        private const val MAX_APPROVAL_PAYLOAD_CHARS = 64 * 1024
        private const val MAX_RECEIPT_CHARS = 64 * 1024
        private const val MAX_RECEIPT_PATHS = 16
    }
}
