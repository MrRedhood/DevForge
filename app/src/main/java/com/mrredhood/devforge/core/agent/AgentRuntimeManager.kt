package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.storage.AgentTaskEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application-scoped owner for agent execution.
 *
 * Screens and ViewModels may observe and control durable agent tasks, but they never own the
 * execution coroutine scope. This keeps active agents alive while the user navigates through
 * DevForge and lets startup recovery run independently of any UI destination.
 */
class AgentRuntimeManager(context: Context) {
    private val appContext = context.applicationContext
    private val database = DevForgeDatabase.get(appContext)
    private val approvals = ApprovalRepository(database.approvalDao())
    private val grants = com.mrredhood.devforge.core.storage.CapabilityGrantRepository(database.capabilityGrantDao())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val coordinator: ParallelAgentCoordinator = ParallelAgentCoordinator(appContext)
    internal val taskEngine: AgentTaskEngine
        get() = coordinator.engine

    init {
        scope.launch {
            runCatching { grants.hydrateRegistry() }
            database.workspaceDao().observeAll().collectLatest { workspaces ->
                workspaces.forEach { workspace ->
                    try {
                        coordinator.recoverWorkspace(workspace.id)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        // Recovery is retried by the next workspace observation/startup pass.
                    }
                }
            }
        }
    }

    suspend fun assign(assignment: AgentAssignment): String =
        coordinator.assign(assignment)

    suspend fun pause(taskId: String): Boolean =
        coordinator.pause(taskId)

    suspend fun resume(taskId: String): AgentTaskEntity? =
        coordinator.resume(taskId)

    suspend fun cancel(taskId: String): Boolean =
        coordinator.cancel(taskId)

    fun resumeAfterApproval(taskId: String) =
        coordinator.resumeAfterApproval(taskId)

    suspend fun recoverWorkspace(workspaceId: String) {
        approvals.expireDue()
        approvals.recoverStaleExecuting()
        coordinator.recoverWorkspace(workspaceId)
        val waiting = database.agentTaskDao().list(workspaceId, 20)
            .filter { it.status == AgentTaskStatus.WAITING_APPROVAL.name && !it.approvalId.isNullOrBlank() }
        waiting.forEach { task ->
            val approval = approvals.observeById(task.approvalId!!).first()
            if (approval?.status == ApprovalRepository.STATUS_APPROVED &&
                approval.expiresAtEpochMs > System.currentTimeMillis()
            ) {
                coordinator.resumeAfterApproval(task.taskId)
            }
        }
    }

    /**
     * Defensive shutdown hook for tests or process owners that explicitly dispose the runtime.
     */
    fun close() {
        coordinator.close()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
