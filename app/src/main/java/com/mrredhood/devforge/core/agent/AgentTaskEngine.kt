package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.storage.AgentTaskEntity
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.security.SecretRedactor
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
        durableState.saveAgentTask(
            task.copy(
                status = AgentTaskStatus.CANCELLED.name,
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis(),
                errorMessage = "Cancelled by user.",
            ),
        )
        task.approvalId?.let { approvalId ->
            runCatching { approvals?.reject(approvalId) }
        }
        coordination?.releaseTaskFileLeases(task.workspaceId, taskId)
        auditTask(task, "AGENT_TASK_CANCELLED", "Agent task cancelled by user.")
        return true
    }

    private suspend fun execute(taskId: String, approvalId: String?): AgentTaskEntity? = withContext(Dispatchers.IO) {
        var task = durableState.getAgentTask(taskId) ?: return@withContext null
        if (task.status in TERMINAL_STATUSES) return@withContext task
        val plan = runCatching { AgentTaskPlanCodec.decode(task.payload) }
            .getOrElse { error ->
                val failed = task.copy(
                    status = AgentTaskStatus.FAILED.name,
                    errorMessage = (error.message ?: "Invalid agent task plan.").take(600),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    completedAtEpochMs = System.currentTimeMillis(),
                )
                durableState.saveAgentTask(failed)
                return@withContext failed
            }
        if (plan.steps.size != task.stepCount) {
            val failed = task.copy(
                status = AgentTaskStatus.FAILED.name,
                errorMessage = "Persisted task step count does not match its plan.",
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis(),
            )
            durableState.saveAgentTask(failed)
            return@withContext failed
        }
        if (task.currentStep !in 0..plan.steps.size) {
            val failed = task.copy(
                status = AgentTaskStatus.FAILED.name,
                errorMessage = "Persisted task step pointer is invalid.",
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis(),
            )
            durableState.saveAgentTask(failed)
            return@withContext failed
        }

        val startedAt = task.startedAtEpochMs ?: System.currentTimeMillis()
        val previousStatus = task.status
        task = task.copy(
            status = if (task.currentStep == 0) AgentTaskStatus.PLANNING.name else AgentTaskStatus.RUNNING.name,
            updatedAtEpochMs = System.currentTimeMillis(),
            startedAtEpochMs = startedAt,
            errorMessage = null,
        )
        durableState.saveAgentTask(task)
        if (previousStatus != task.status || previousStatus == AgentTaskStatus.QUEUED.name) {
            auditTask(task, "AGENT_TASK_STARTED", "Agent task execution started.")
        }

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
                    val stepTask = task.copy(
                        status = AgentTaskStatus.RUNNING.name,
                        currentStep = index,
                        updatedAtEpochMs = System.currentTimeMillis(),
                        lastToolId = step.toolId.wireName,
                        approvalId = null,
                    )
                    durableState.saveAgentTask(stepTask)
                    task = stepTask

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
                            val latestState = durableState.getAgentTask(taskId)
                            val requestedStatus = latestState?.status
                            task = task.copy(
                                status = when (requestedStatus) {
                                    AgentTaskStatus.PAUSED.name -> AgentTaskStatus.PAUSED.name
                                    AgentTaskStatus.CANCELLED.name -> AgentTaskStatus.CANCELLED.name
                                    else -> AgentTaskStatus.RUNNING.name
                                },
                                currentStep = nextStep,
                                approvalId = null,
                                result = appendResult(task.result, step.label, result.summary, result.output, result.receiptJson),
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                            durableState.saveAgentTask(task)
                            if (task.status == AgentTaskStatus.PAUSED.name || task.status == AgentTaskStatus.CANCELLED.name) {
                                return@withTimeout
                            }
                        }
                        is AgentToolResult.ApprovalRequired -> {
                            task = task.copy(
                                status = AgentTaskStatus.WAITING_APPROVAL.name,
                                currentStep = index,
                                approvalId = result.approvalId,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                            durableState.saveAgentTask(task)
                            auditTask(task, "AGENT_TASK_WAITING_APPROVAL", "Agent task is waiting for approval.")
                            return@withTimeout
                        }
                        is AgentToolResult.Failure -> {
                            task = task.copy(
                                status = AgentTaskStatus.FAILED.name,
                                errorMessage = result.message.take(600),
                                updatedAtEpochMs = System.currentTimeMillis(),
                                completedAtEpochMs = System.currentTimeMillis(),
                            )
                            durableState.saveAgentTask(task)
                            auditTask(task, "AGENT_TASK_FAILED", task.errorMessage ?: "Agent task failed.")
                            return@withTimeout
                        }
                    }
                }
                task = task.copy(
                    status = AgentTaskStatus.COMPLETED.name,
                    currentStep = plan.steps.size,
                    approvalId = null,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    completedAtEpochMs = System.currentTimeMillis(),
                    result = appendResult(task.result, "complete", "Agent task completed.", "", null),
                )
                durableState.saveAgentTask(task)
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
            if (failure is CancellationException) throw failure
            task = task.copy(
                status = AgentTaskStatus.FAILED.name,
                errorMessage = (failure?.message ?: "Agent task execution failed.").take(600),
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis(),
            )
            durableState.saveAgentTask(task)
            auditTask(task, "AGENT_TASK_FAILED", task.errorMessage ?: "Agent task execution failed.")
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
