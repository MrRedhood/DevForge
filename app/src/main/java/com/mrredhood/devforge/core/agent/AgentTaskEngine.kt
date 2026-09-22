package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.storage.AgentTaskEntity
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.security.SecretRedactor
import com.mrredhood.devforge.core.security.WorkspacePathScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/** Executes a persisted agent plan as a small, resumable sequence of typed tool calls. */
class AgentTaskEngine(
    private val durableState: DurableStateRepository,
    private val gateway: AgentToolGateway,
    private val coordination: AgentCoordinationService? = null,
    private val approvals: ApprovalRepository? = null,
) {
    suspend fun enqueue(
        workspaceId: String,
        title: String,
        instruction: String,
        plan: AgentTaskPlan,
        model: AgentModelBinding? = null,
    ): String = withContext(Dispatchers.IO) {
        require(!SecretRedactor.containsLikelySecret(instruction)) {
            "Agent instruction contains secret-like material and cannot be persisted."
        }
        require(plan.steps.all { !SecretRedactor.containsLikelySecret(it.argumentsJson) }) {
            "Agent plan contains secret-like tool arguments and cannot be persisted."
        }
        val taskId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payload = AgentTaskPlanCodec.encode(plan)
        val task = AgentTaskEntity(
            taskId = taskId,
            workspaceId = workspaceId,
            title = title,
            instruction = instruction,
            status = AgentTaskStatus.QUEUED.name,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            payload = payload,
            errorMessage = null,
            currentStep = 0,
            stepCount = plan.steps.size,
            result = null,
            approvalId = null,
            lastToolId = null,
            startedAtEpochMs = null,
            completedAtEpochMs = null,
            modelProviderId = model?.provider?.id,
            modelId = model?.modelId?.take(300),
            modelName = model?.modelName?.take(200),
        )
        if (!durableState.trySaveAgentTask(task, MAX_NON_TERMINAL_TASKS)) {
            throw IllegalStateException(
                "The workspace already has the maximum of $MAX_NON_TERMINAL_TASKS active or queued agents.",
            )
        }
        taskId
    }

    suspend fun run(taskId: String): AgentTaskEntity? = execute(taskId, approvalId = null)

    suspend fun pause(taskId: String): Boolean {
        val task = durableState.getAgentTask(taskId) ?: return false
        val changed = durableState.pauseAgentTask(taskId)
        if (changed) auditTask(task, "AGENT_TASK_PAUSED", "Agent task paused by user.")
        return changed
    }

    suspend fun resume(taskId: String): AgentTaskEntity? {
        val task = durableState.getAgentTask(taskId) ?: return null
        val changed = durableState.resumeAgentTask(taskId)
        if (!changed) return task
        auditTask(task, "AGENT_TASK_RESUMED", "Agent task resumed.")
        return execute(taskId, approvalId = null)
    }

    suspend fun resumeAfterApproval(taskId: String): AgentTaskEntity? {
        val task = durableState.getAgentTask(taskId) ?: return null
        require(task.status == AgentTaskStatus.WAITING_APPROVAL.name) { "Task is not waiting for approval." }
        val approvalId = task.approvalId ?: throw IllegalStateException("Waiting task has no approval reference.")
        return execute(taskId, approvalId)
    }

    suspend fun cancel(taskId: String): Boolean {
        val task = durableState.getAgentTask(taskId) ?: return false
        if (task.status in TERMINAL_STATUSES) return false
        val changed = durableState.cancelAgentTask(taskId, "Cancelled by user.")
        if (!changed) return false
        val cancelledTask = durableState.getAgentTask(taskId) ?: task.copy(
            status = AgentTaskStatus.CANCELLED.name,
            errorMessage = "Cancelled by user.",
            completedAtEpochMs = System.currentTimeMillis(),
        )
        cancelledTask.approvalId?.let { approvalId ->
            runCatching { approvals?.reject(approvalId) }
        }
        coordination?.releaseTaskFileLeases(cancelledTask.workspaceId, taskId)
        auditTask(cancelledTask, "AGENT_TASK_CANCELLED", "Agent task cancelled by user.")
        return true
    }

    private suspend fun execute(taskId: String, approvalId: String?): AgentTaskEntity? = withContext(Dispatchers.IO) {
        var task = durableState.getAgentTask(taskId) ?: return@withContext null
        if (task.status in TERMINAL_STATUSES) return@withContext task

        // A waiting approval is a hard execution boundary. Only resumeAfterApproval may
        // cross it with the exact persisted approval id.
        if (task.status == AgentTaskStatus.WAITING_APPROVAL.name) {
            if (approvalId == null || approvalId != task.approvalId) return@withContext task
        }
        if (task.status == AgentTaskStatus.PAUSED.name && approvalId == null) {
            return@withContext task
        }

        val decodedPlan = runCatching { AgentTaskPlanCodec.decode(task.payload) }
            .getOrElse { error ->
                durableState.failAgentTask(
                    taskId,
                    (error.message ?: "Invalid agent task plan.").take(600),
                    System.currentTimeMillis(),
                )
                return@withContext durableState.getAgentTask(taskId)
            }
        // Workspace paths are selected from the active workspace, never from a user- or
        // model-supplied folder prefix. Older queued tasks may still contain the former
        // display-name scope (for example "Nexus/GitHub"), which caused every mutation to
        // be rejected as outside the authorized workspace. The gateway still validates each
        // normalized path and protects .git/traversal; this only repairs the stale boundary
        // and establishes the active workspace root as the single authorization boundary.
        val plan = decodedPlan.copy(pathScope = WorkspacePathScope())
        if (plan.steps.size != task.stepCount) {
            durableState.failAgentTask(taskId, "Persisted task step count does not match its plan.", System.currentTimeMillis())
            return@withContext durableState.getAgentTask(taskId)
        }
        if (task.currentStep !in 0..plan.steps.size) {
            durableState.failAgentTask(taskId, "Persisted task step pointer is invalid.", System.currentTimeMillis())
            return@withContext durableState.getAgentTask(taskId)
        }

        val now = System.currentTimeMillis()
        val targetStatus = if (task.currentStep == 0) AgentTaskStatus.PLANNING.name else AgentTaskStatus.RUNNING.name
        val started = if (approvalId == null) {
            durableState.startQueuedAgentTask(taskId, targetStatus, now)
        } else {
            durableState.startApprovedAgentTask(taskId, approvalId, targetStatus, now)
        }
        if (!started) return@withContext durableState.getAgentTask(taskId)
        task = durableState.getAgentTask(taskId) ?: return@withContext null
        auditTask(task, "AGENT_TASK_STARTED", "Agent task execution started.")

        val execution = try {
            runCatching {
                withTimeout(MAX_EXECUTION_MS) {
                    for (index in task.currentStep until plan.steps.size) {
                    currentCoroutineContext().ensureActive()
                    val persistedBeforeStep = durableState.getAgentTask(taskId) ?: return@withTimeout
                    if (persistedBeforeStep.status == AgentTaskStatus.PAUSED.name ||
                        persistedBeforeStep.status == AgentTaskStatus.CANCELLED.name
                    ) {
                        task = persistedBeforeStep
                        return@withTimeout
                    }
                    val step = plan.steps[index]
                    val stepStarted = durableState.beginAgentStep(taskId, index, step.toolId.wireName, System.currentTimeMillis())
                    if (!stepStarted) {
                        task = durableState.getAgentTask(taskId) ?: return@withTimeout
                        return@withTimeout
                    }
                    task = durableState.getAgentTask(taskId) ?: return@withTimeout

                    val request = step.toRequest(task.workspaceId, task.taskId, index)
                    val toolContext = AgentToolContext(
                        workspaceId = task.workspaceId,
                        taskId = task.taskId,
                        stepIndex = index,
                        pathScope = plan.pathScope,
                        access = plan.access,
                    )
                    val result = if (approvalId != null && index == task.currentStep) {
                        gateway.executeApproved(toolContext, request, approvalId)
                    } else {
                        gateway.execute(toolContext, request)
                    }

                    when (result) {
                        is AgentToolResult.Success -> {
                            val nextStep = index + 1
                            val updatedResult = appendResult(task.result, step.label, result.summary, result.output, result.receiptJson)
                            if (!durableState.advanceAgentStep(taskId, index, nextStep, updatedResult, System.currentTimeMillis())) {
                                task = durableState.getAgentTask(taskId) ?: return@withTimeout
                                return@withTimeout
                            }
                            task = durableState.getAgentTask(taskId) ?: return@withTimeout
                            if (task.status == AgentTaskStatus.PAUSED.name || task.status == AgentTaskStatus.CANCELLED.name) {
                                return@withTimeout
                            }
                        }
                        is AgentToolResult.ApprovalRequired -> {
                            if (!durableState.waitForAgentApproval(taskId, index, result.approvalId, System.currentTimeMillis())) {
                                task = durableState.getAgentTask(taskId) ?: return@withTimeout
                                return@withTimeout
                            }
                            task = durableState.getAgentTask(taskId) ?: return@withTimeout
                            auditTask(task, "AGENT_TASK_WAITING_APPROVAL", "Agent task is waiting for approval.")
                            return@withTimeout
                        }
                        is AgentToolResult.Failure -> {
                            durableState.failAgentTask(taskId, result.message.take(600), System.currentTimeMillis())
                            task = durableState.getAgentTask(taskId) ?: return@withTimeout
                            auditTask(task, "AGENT_TASK_FAILED", task.errorMessage ?: "Agent task failed.")
                            return@withTimeout
                        }
                    }
                }
                val completedAt = System.currentTimeMillis()
                val completedResult = appendResult(task.result, "complete", "Agent task completed.", "", null)
                if (!durableState.completeAgentTask(taskId, plan.steps.size, completedResult, completedAt)) {
                    task = durableState.getAgentTask(taskId) ?: return@withTimeout
                    return@withTimeout
                }
                task = durableState.getAgentTask(taskId) ?: return@withTimeout
                auditTask(task, "AGENT_TASK_COMPLETED", "Agent task completed.")
                }
            }
        } catch (cancelled: CancellationException) {
            val persisted = durableState.getAgentTask(taskId)
            if (persisted?.status == AgentTaskStatus.PAUSED.name ||
                persisted?.status == AgentTaskStatus.CANCELLED.name
            ) {
                return@withContext persisted
            }
            throw cancelled
        }

        if (execution.isFailure) {
            val failure = execution.exceptionOrNull()
            when (failure) {
                is TimeoutCancellationException -> {
                    val failedAt = System.currentTimeMillis()
                    val message = "Agent task exceeded the ${MAX_EXECUTION_MS / 1000L}-second execution limit."
                    durableState.failAgentTask(taskId, message, failedAt)
                    task = durableState.getAgentTask(taskId) ?: task.copy(
                        status = AgentTaskStatus.FAILED.name,
                        errorMessage = message,
                        updatedAtEpochMs = failedAt,
                        completedAtEpochMs = failedAt,
                    )
                    auditTask(task, "AGENT_TASK_FAILED", message)
                }
                is CancellationException -> throw failure
                else -> {
                    val failedAt = System.currentTimeMillis()
                    val message = (failure?.message ?: "Agent task execution failed.").take(600)
                    durableState.failAgentTask(taskId, message, failedAt)
                    task = durableState.getAgentTask(taskId) ?: task
                    auditTask(task, "AGENT_TASK_FAILED", message)
                }
            }
        }
        task
    }

    private suspend fun auditTask(task: AgentTaskEntity, eventType: String, summary: String) {
        durableState.recordAudit(
            AuditEventEntity(
                eventId = UUID.randomUUID().toString(),
                workspaceId = task.workspaceId,
                actionId = "agent:" + task.taskId,
                capability = null,
                risk = null,
                eventType = eventType,
                summary = SecretRedactor.redact(summary, 500),
                metadataJson = "{\"taskId\":\"" + task.taskId + "\",\"step\":" + task.currentStep + "}",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun appendResult(
        existing: String?,
        label: String,
        summary: String,
        output: String,
        receiptJson: String?,
    ): String {
        val entry = buildString {
            append(label.take(100)).append(": ").append(summary.take(500))
            if (output.isNotBlank()) append("\n").append(SecretRedactor.redact(output, 3000))
            if (!receiptJson.isNullOrBlank()) append("\nreceipt=").append(SecretRedactor.redact(receiptJson, MAX_RECEIPT_BYTES))
        }
        val combined = listOfNotNull(existing?.take(MAX_RESULT_BYTES), entry).joinToString("\n\n")
        return combined.take(MAX_RESULT_BYTES)
    }

    companion object {
        private const val MAX_EXECUTION_MS = 60_000L
        private const val MAX_NON_TERMINAL_TASKS = 10
        private const val MAX_RESULT_BYTES = 64 * 1024
        private const val MAX_RECEIPT_BYTES = 4 * 1024
        private val TERMINAL_STATUSES = setOf(
            AgentTaskStatus.COMPLETED.name,
            AgentTaskStatus.FAILED.name,
            AgentTaskStatus.CANCELLED.name,
        )
    }
}
