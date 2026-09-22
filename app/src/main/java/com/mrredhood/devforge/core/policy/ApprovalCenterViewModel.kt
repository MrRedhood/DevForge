package com.mrredhood.devforge.core.policy

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.CapabilityGrantEntity
import com.mrredhood.devforge.core.storage.CapabilityGrantRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import com.mrredhood.devforge.core.agent.AgentAccess
import com.mrredhood.devforge.core.agent.AgentRuntime
import com.mrredhood.devforge.core.agent.AgentToolContext
import com.mrredhood.devforge.core.agent.AgentToolId
import com.mrredhood.devforge.core.agent.AgentToolRequest
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.DevForgeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ApprovalCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DevForgeDatabase.get(application)
    private val repository = ApprovalRepository(database.approvalDao())
    private val grantRepository = CapabilityGrantRepository(database.capabilityGrantDao())
    private val workspaces = WorkspaceDatabaseRepository(application)
    private val agentRuntime = (application as DevForgeApplication).agentRuntime
    private val toolRuntime = AgentRuntime.createToolRuntime(application, PermissionMode.SOME)
    private val durableState = com.mrredhood.devforge.core.storage.DurableStateRepository(database)
    private var pendingJob: Job? = null
    private var auditJob: Job? = null
    private var grantsJob: Job? = null

    var pending by mutableStateOf<List<ApprovalEntity>>(emptyList())
        private set

    var auditHistory by mutableStateOf<List<AuditEventEntity>>(emptyList())
        private set

    var grants by mutableStateOf<List<CapabilityGrantEntity>>(emptyList())
        private set

    var activeWorkspaceId by mutableStateOf<String?>(null)
        private set

    var actionMessage by mutableStateOf<String?>(null)
        private set

    val grantableCapabilities: List<Capability> = Capability.values().filter(CapabilityGrantRepository::isGrantable)

    init {
        pendingJob = viewModelScope.launch {
            repository.observePending().collectLatest { actions -> pending = actions }
        }
        auditJob = viewModelScope.launch {
            database.auditEventDao().observeRecent(MAX_AUDIT_HISTORY).collectLatest { events -> auditHistory = events }
        }
        viewModelScope.launch {
            workspaces.activeWorkspace.collectLatest { workspace ->
                grantsJob?.cancel()
                activeWorkspaceId = workspace?.id
                grants = emptyList()
                if (workspace != null) {
                    grantsJob = viewModelScope.launch {
                        grantRepository.observe(workspace.id).collectLatest { values -> grants = values }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.expireDue()
            val reconciled = com.mrredhood.devforge.core.storage.DurableStateRepository(database)
                .reconcileWaitingAgentApprovals()
            if (reconciled > 0) {
                actionMessage = "Reconciled $reconciled agent approval wait(s) that were no longer valid."
            }
        }
        viewModelScope.launch { grantRepository.pruneExpired() }
    }

    fun approve(action: ApprovalEntity) {
        viewModelScope.launch {
            val resolved = repository.approve(action.approvalId)
            if (!resolved) {
                actionMessage = "This approval is no longer pending."
                return@launch
            }
            val payload = runCatching { JSONObject(action.payload) }.getOrNull()
            if (payload?.optString("executionMode").equals("CHAT", ignoreCase = true)) {
                actionMessage = "Approved: " + action.summary + " — the waiting AI chat will continue."
                return@launch
            }
            val taskId = payload?.optString("taskId").orEmpty()
            val durableTask = taskId.takeIf { it.isNotBlank() }?.let { durableState.getAgentTask(it) }
            if (durableTask?.status == com.mrredhood.devforge.core.agent.AgentTaskStatus.WAITING_APPROVAL.name) {
                runCatching { agentRuntime.resumeAfterApproval(taskId) }
                    .onFailure { actionMessage = "Approved, but the agent could not resume: " + (it.message ?: "unknown error") }
                    .onSuccess { actionMessage = "Approved: " + action.summary }
                return@launch
            }

            val isAgentToolApproval =
                action.actionId.startsWith("agent:") &&
                    payload?.optString("tool").orEmpty().isNotBlank()
            if (!isAgentToolApproval) {
                actionMessage = "Approved: " + action.summary
                return@launch
            }
            val result = executeApprovedTool(action, payload)
            actionMessage = result ?: ("Approved: " + action.summary)
        }
    }

    private suspend fun executeApprovedTool(action: ApprovalEntity, payload: JSONObject?): String? {
        val safePayload = payload ?: return "Approved, but the approval payload is missing."
        val toolName = safePayload.optString("tool").orEmpty()
        val toolId = AgentToolId.entries.firstOrNull { it.wireName == toolName }
            ?: return "Approved, but the tool definition is no longer available."
        val workspaceId = safePayload.optString("workspaceId").orEmpty()
        val taskId = safePayload.optString("taskId").orEmpty()
        val stepIndex = safePayload.optInt("stepIndex", -1)
        if (workspaceId.isBlank() || taskId.isBlank() || stepIndex < 0) {
            return "Approved, but the approval payload is incomplete."
        }
        val arguments = safePayload.opt("arguments")
        val argumentsJson = when (arguments) {
            is JSONObject -> arguments.toString()
            is String -> runCatching { JSONObject(arguments).toString() }.getOrElse { arguments }
            else -> "{}"
        }
        val prefixes = buildList {
            val array = safePayload.optJSONArray("allowedPrefixes")
            if (array != null) {
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }
        val access = AgentAccess.decode(safePayload.optJSONArray("access")?.toString(), AgentAccess.entries.toSet())
        val context = AgentToolContext(
            workspaceId = workspaceId,
            taskId = taskId,
            stepIndex = stepIndex,
            pathScope = WorkspacePathScope(if (prefixes.isEmpty()) listOf("") else prefixes),
            access = access,
        )
        val request = AgentToolRequest(
            toolId = toolId,
            workspaceId = workspaceId,
            argumentsJson = argumentsJson,
            taskId = taskId,
            stepIndex = stepIndex,
        )
        return withContext(Dispatchers.IO) {
            when (val result = toolRuntime.gateway.executeApproved(context, request, action.approvalId)) {
                is com.mrredhood.devforge.core.agent.AgentToolResult.Success ->
                    "Approved and executed: " + result.summary
                is com.mrredhood.devforge.core.agent.AgentToolResult.Failure ->
                    "Approved, but execution failed: " + result.message
                is com.mrredhood.devforge.core.agent.AgentToolResult.ApprovalRequired ->
                    "Approved, but the tool requested approval again."
            }
        }
    }

    fun reject(action: ApprovalEntity) {
        viewModelScope.launch {
            val resolved = repository.reject(action.approvalId)
            if (resolved) {
                val taskId = runCatching { JSONObject(action.payload).optString("taskId") }.getOrNull().orEmpty()
                if (taskId.isNotBlank() && action.actionId.startsWith("agent:$taskId:step:")) {
                    com.mrredhood.devforge.core.storage.DurableStateRepository(database)
                        .failWaitingAgentApproval(taskId, action.approvalId, "Approval was rejected.")
                }
                actionMessage = "Rejected: ${action.summary}"
            } else {
                actionMessage = "This approval is no longer pending."
            }
        }
    }

    fun grant(capability: Capability, rawPathScope: String = "") {
        val workspaceId = activeWorkspaceId ?: run {
            actionMessage = "Open a workspace before creating a persistent capability grant."
            return
        }
        val pathScope = runCatching {
            val entries = rawPathScope.split(',', '\n').map(String::trim).filter(String::isNotBlank)
            WorkspacePathScope(if (entries.isEmpty()) listOf("") else entries)
        }.getOrElse { error ->
            actionMessage = error.message ?: "Invalid path scope."
            return
        }
        viewModelScope.launch {
            val result = grantRepository.grant(workspaceId, capability, RiskLevel.R3, pathScope = pathScope)
            actionMessage = if (result == null) {
                "${capability.name} cannot be persistently granted."
            } else {
                "Persistent ${capability.name} grant enabled up to R3 for " +
                    if (pathScope.canonicalPrefixes().any { it.isEmpty() }) "the entire workspace." else pathScope.canonicalPrefixes().joinToString(", ") + "."
            }
        }
    }

    fun revoke(grant: CapabilityGrantEntity) {
        viewModelScope.launch {
            val capability = grant.capabilityOrNull() ?: return@launch
            val workspace = activeWorkspaceId ?: return@launch
            val changed = grantRepository.revoke(workspace, capability)
            actionMessage = if (changed) "Persistent ${capability.name} grant revoked." else "Grant was already inactive."
        }
    }

    fun remind(action: ApprovalEntity) {
        actionMessage = "This approval is already visible in the in-app approval card."
    }

    fun remindAll() {
        if (pending.isNotEmpty()) actionMessage = "Pending approvals are visible in the in-app approval card."
    }

    fun expireDue() {
        viewModelScope.launch { repository.expireDue() }
    }

    fun clearMessage() {
        actionMessage = null
    }

    override fun onCleared() {
        pendingJob?.cancel()
        auditJob?.cancel()
        grantsJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_AUDIT_HISTORY = 100
    }
}
