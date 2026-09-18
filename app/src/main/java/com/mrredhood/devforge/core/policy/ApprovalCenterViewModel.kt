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
import com.mrredhood.devforge.DevForgeApplication
import com.mrredhood.devforge.core.security.WorkspacePathScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class ApprovalCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DevForgeDatabase.get(application)
    private val repository = ApprovalRepository(database.approvalDao())
    private val grantRepository = CapabilityGrantRepository(database.capabilityGrantDao())
    private val workspaces = WorkspaceDatabaseRepository(application)
    private val agentRuntime = (application as DevForgeApplication).agentRuntime
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
            if (resolved) {
                val taskId = runCatching { JSONObject(action.payload).optString("taskId") }.getOrNull().orEmpty()
                if (taskId.isNotBlank() && action.actionId.startsWith("agent:$taskId:step:")) {
                    agentRuntime.resumeAfterApproval(taskId)
                }
                actionMessage = "Approved: ${action.summary}"
            } else {
                actionMessage = "This approval is no longer pending."
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
            val result = grantRepository.grant(workspaceId, capability, RiskLevel.R2, pathScope = pathScope)
            actionMessage = if (result == null) {
                "${capability.name} cannot be persistently granted."
            } else {
                "Persistent ${capability.name} grant enabled up to R2 for " +
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
