package com.mrredhood.devforge.core.automation

import android.content.Context
import com.mrredhood.devforge.core.agent.AgentTaskEngine
import com.mrredhood.devforge.core.agent.AgentTaskPlanCodec
import com.mrredhood.devforge.core.agent.AgentTaskStatus
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.AutomationEntity
import com.mrredhood.devforge.core.storage.AutomationRunEntity
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.recovery.RecoveryPolicy
import java.util.UUID
import org.json.JSONObject

/** Runs persisted automation definitions through the same typed agent gateway used by interactive AI. */
class AutomationRunEngine(
    private val context: Context,
    private val durable: DurableStateRepository,
    private val agent: AgentTaskEngine,
) {
    suspend fun execute(automationId: String, attempt: Int, triggerPayload: String? = null): AutomationExecutionOutcome {
        val automation = durable.getAutomation(automationId)
            ?: return AutomationExecutionOutcome(AutomationRunStatus.FAILED, false, 0L)
        if (automation.status != AutomationStatus.ENABLED.name) {
            return AutomationExecutionOutcome(AutomationRunStatus.CANCELLED, false, 0L)
        }

        val now = System.currentTimeMillis()
        val active = durable.recentAutomationRuns(automationId, 10).firstOrNull {
            RecoveryPolicy.isActiveAutomation(it.status)
        }
        if (active != null) {
            if (RecoveryPolicy.shouldRecoverStaleAutomation(active.status, active.startedAtEpochMs, now)) {
                durable.saveAutomationRun(
                    active.copy(
                        status = AutomationRunStatus.FAILED.name,
                        completedAtEpochMs = now,
                        errorMessage = RecoveryPolicy.AUTOMATION_RECOVERY_MESSAGE,
                    ),
                )
            } else {
                val skipped = createRun(automation, AutomationRunStatus.SKIPPED, attempt, error = "An earlier automation run is still active.", triggerPayload = triggerPayload)
                durable.saveAutomationRun(skipped)
                return AutomationExecutionOutcome(AutomationRunStatus.SKIPPED, false, 0L)
            }
        }

        val startedAt = now
        var run = createRun(automation, AutomationRunStatus.RUNNING, attempt, startedAt, triggerPayload = triggerPayload)
        durable.saveAutomationRun(run)
        audit(automation, run, "AUTOMATION_STARTED", "Automation '${automation.name}' started.")

        val plan = runCatching { AgentTaskPlanCodec.decode(automation.actionGraph) }.getOrElse { error ->
            run = finish(run, AutomationRunStatus.FAILED, error.message ?: "Invalid automation action graph.")
            durable.saveAutomationRun(run)
            audit(automation, run, "AUTOMATION_FAILED", run.errorMessage ?: "Invalid action graph.")
            return outcomeForFailure(attempt)
        }

        val taskId = runCatching {
            agent.enqueue(
                workspaceId = automation.workspaceId ?: "global",
                title = automation.name,
                instruction = "Automation: ${automation.name}",
                plan = plan,
            )
        }.getOrElse { error ->
            run = finish(run, AutomationRunStatus.FAILED, error.message ?: "Unable to enqueue agent task.")
            durable.saveAutomationRun(run)
            audit(automation, run, "AUTOMATION_FAILED", run.errorMessage ?: "Unable to enqueue agent task.")
            return outcomeForFailure(attempt)
        }

        val task = agent.run(taskId)
        val taskStatus = task?.status?.let { runCatching { AgentTaskStatus.valueOf(it) }.getOrNull() }
        val completedAt = System.currentTimeMillis()
        run = when (taskStatus) {
            AgentTaskStatus.COMPLETED -> finish(run, AutomationRunStatus.COMPLETED, null, receipt(run, taskId, task?.approvalId, task?.result), completedAt)
            AgentTaskStatus.WAITING_APPROVAL -> run.copy(
                status = AutomationRunStatus.WAITING_APPROVAL.name,
                completedAtEpochMs = null,
                errorMessage = null,
                receiptJson = receipt(run, taskId, task?.approvalId, task?.result),
            )
            AgentTaskStatus.CANCELLED -> finish(run, AutomationRunStatus.CANCELLED, "Agent task was cancelled.", receipt(run, taskId, null, task?.result), completedAt)
            AgentTaskStatus.FAILED, null -> finish(run, AutomationRunStatus.FAILED, task?.errorMessage ?: "Automation agent task failed.", receipt(run, taskId, task?.approvalId, task?.result), completedAt)
            else -> run.copy(receiptJson = receipt(run, taskId, task?.approvalId, task?.result))
        }
        durable.saveAutomationRun(run)
        audit(automation, run, "AUTOMATION_${run.status}", run.errorMessage ?: "Automation ${run.status.lowercase()}.")
        return when (run.status) {
            AutomationRunStatus.FAILED.name -> outcomeForFailure(attempt)
            AutomationRunStatus.WAITING_APPROVAL.name -> AutomationExecutionOutcome(AutomationRunStatus.WAITING_APPROVAL, false, 0L)
            else -> AutomationExecutionOutcome(AutomationRunStatus.valueOf(run.status), false, 0L)
        }
    }

    suspend fun resumeAfterApproval(automationRunId: String): AutomationExecutionOutcome {
        val run = durable.getAutomationRun(automationRunId) ?: return AutomationExecutionOutcome(AutomationRunStatus.FAILED, false, 0L)
        if (run.status != AutomationRunStatus.WAITING_APPROVAL.name) return AutomationExecutionOutcome(AutomationRunStatus.valueOf(run.status), false, 0L)
        val receipt = run.receiptJson?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return AutomationExecutionOutcome(AutomationRunStatus.FAILED, false, 0L)
        val taskId = receipt.optString("taskId").takeIf { it.isNotBlank() }
            ?: return AutomationExecutionOutcome(AutomationRunStatus.FAILED, false, 0L)
        val task = agent.resumeAfterApproval(taskId)
        val automation = durable.getAutomation(run.automationId)
        val now = System.currentTimeMillis()
        val updated = when (task?.status) {
            AgentTaskStatus.COMPLETED.name -> finish(run, AutomationRunStatus.COMPLETED, null, receipt(taskId, task.approvalId, task.result, receipt.optString("triggerPayload")), now)
            AgentTaskStatus.WAITING_APPROVAL.name -> run.copy(receiptJson = receipt(taskId, task.approvalId, task.result, receipt.optString("triggerPayload")))
            AgentTaskStatus.CANCELLED.name -> finish(run, AutomationRunStatus.CANCELLED, "Agent task was cancelled.", receipt(taskId, null, task.result, receipt.optString("triggerPayload")), now)
            else -> finish(run, AutomationRunStatus.FAILED, task?.errorMessage ?: "Approved automation task failed.", receipt(taskId, task?.approvalId, task?.result, receipt.optString("triggerPayload")), now)
        }
        durable.saveAutomationRun(updated)
        automation?.let {
            audit(it, updated, "AUTOMATION_${updated.status}", updated.errorMessage ?: "Automation ${updated.status.lowercase()} after approval.")
            if (updated.status == AutomationRunStatus.COMPLETED.name && it.status == AutomationStatus.ENABLED.name) {
                AutomationScheduler.scheduleNext(context, it)
            }
        }
        return AutomationExecutionOutcome(AutomationRunStatus.valueOf(updated.status), false, 0L)
    }

    suspend fun cancel(automationRunId: String): Boolean {
        val run = durable.getAutomationRun(automationRunId) ?: return false
        if (run.status !in setOf(AutomationRunStatus.RUNNING.name, AutomationRunStatus.WAITING_APPROVAL.name)) return false
        val taskId = run.receiptJson?.let { runCatching { JSONObject(it).optString("taskId") }.getOrNull() }.orEmpty()
        if (taskId.isNotBlank()) agent.cancel(taskId)
        durable.saveAutomationRun(run.copy(status = AutomationRunStatus.CANCELLED.name, completedAtEpochMs = System.currentTimeMillis(), errorMessage = "Cancelled by user."))
        return true
    }

    private fun outcomeForFailure(attempt: Int): AutomationExecutionOutcome =
        if (attempt < AutomationScheduler.MAX_ATTEMPTS) {
            val multiplier = 1L shl (attempt - 1).coerceIn(0, 3)
            AutomationExecutionOutcome(AutomationRunStatus.FAILED, true, (60_000L * multiplier).coerceAtMost(15L * 60L * 1000L))
        } else AutomationExecutionOutcome(AutomationRunStatus.FAILED, false, 0L)

    private fun createRun(
        automation: AutomationEntity,
        status: AutomationRunStatus,
        attempt: Int,
        startedAt: Long = System.currentTimeMillis(),
        error: String? = null,
        triggerPayload: String? = null,
    ) = AutomationRunEntity(
        runId = UUID.randomUUID().toString(),
        automationId = automation.automationId,
        status = status.name,
        startedAtEpochMs = startedAt,
        completedAtEpochMs = if (status == AutomationRunStatus.RUNNING) null else startedAt,
        errorMessage = error,
        receiptJson = JSONObject()
            .put("attempt", attempt)
            .put("automationId", automation.automationId)
            .put("triggerPayload", triggerPayload?.take(AutomationScheduler.MAX_EVENT_PAYLOAD_BYTES))
            .toString(),
    )

    private fun finish(run: AutomationRunEntity, status: AutomationRunStatus, error: String?, receipt: String? = run.receiptJson, completedAt: Long = System.currentTimeMillis()) =
        run.copy(status = status.name, errorMessage = error?.take(600), completedAtEpochMs = completedAt, receiptJson = receipt)

    private fun receipt(run: AutomationRunEntity, taskId: String, approvalId: String?, result: String?): String =
        receipt(taskId, approvalId, result, JSONObject(run.receiptJson ?: "{}").optString("triggerPayload").takeIf(String::isNotBlank))

    private fun receipt(taskId: String, approvalId: String?, result: String?, triggerPayload: String?): String = JSONObject()
        .put("taskId", taskId)
        .put("approvalId", approvalId)
        .put("result", result?.take(60_000))
        .put("triggerPayload", triggerPayload?.take(AutomationScheduler.MAX_EVENT_PAYLOAD_BYTES))
        .toString()
        .take(60_000)

    private suspend fun audit(automation: AutomationEntity, run: AutomationRunEntity, eventType: String, summary: String) {
        durable.recordAudit(
            AuditEventEntity(
                eventId = UUID.randomUUID().toString(),
                workspaceId = automation.workspaceId,
                actionId = "automation:${run.runId}",
                capability = null,
                risk = null,
                eventType = eventType.take(80),
                summary = summary.take(500),
                metadataJson = run.receiptJson?.take(32_000),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

}

data class AutomationExecutionOutcome(
    val status: AutomationRunStatus,
    val shouldRetry: Boolean,
    val retryDelayMs: Long,
)
