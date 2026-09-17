package com.mrredhood.devforge.core.automation

import android.content.Context
import com.mrredhood.devforge.core.storage.AutomationTriggerStateEntity
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import java.util.UUID
import org.json.JSONObject

/** Matches durable event triggers, deduplicates them and enters the existing WorkManager path. */
object AutomationEventDispatcher {
    suspend fun dispatch(context: Context, event: AutomationEvent): Int {
        val durable = DurableStateRepository(DevForgeDatabase.get(context.applicationContext))
        var triggered = 0
        durable.listAutomations().filter { it.status == AutomationStatus.ENABLED.name }.forEach { automation ->
            if (!AutomationTriggerCodec.matches(automation, event)) return@forEach
            val state = durable.getAutomationTriggerState(automation.automationId)
            val shouldTrigger = when (event) {
                is AutomationEvent.RepositoryChanged -> state?.lastRepositoryFingerprint?.let { it != event.fingerprint } ?: false
                is AutomationEvent.BuildCompleted -> state?.lastBuildRunId?.let { it != event.runId } ?: false
            }
            if (state == null) {
                durable.saveAutomationTriggerState(
                    AutomationTriggerStateEntity(
                        automationId = automation.automationId,
                        lastRepositoryFingerprint = (event as? AutomationEvent.RepositoryChanged)?.fingerprint,
                        lastBuildRunId = (event as? AutomationEvent.BuildCompleted)?.runId,
                        lastEvaluatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                return@forEach
            }
            if (!shouldTrigger) return@forEach

            val payload = eventPayload(event)
            if (AutomationScheduler.triggerEvent(context, automation.automationId, AutomationTriggerCodec.eventKey(event), payload)) {
                durable.saveAutomationTriggerState(
                    state.copy(
                        lastRepositoryFingerprint = (event as? AutomationEvent.RepositoryChanged)?.fingerprint ?: state.lastRepositoryFingerprint,
                        lastBuildRunId = (event as? AutomationEvent.BuildCompleted)?.runId ?: state.lastBuildRunId,
                        lastEvaluatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                durable.recordAudit(
                    AuditEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        workspaceId = automation.workspaceId,
                        actionId = "automation-trigger:${automation.automationId}",
                        capability = null,
                        risk = null,
                        eventType = "AUTOMATION_TRIGGER_MATCHED",
                        summary = "Automation '${automation.name}' matched an external event and was queued.",
                        metadataJson = payload.take(32_000),
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                triggered++
            }
        }
        return triggered
    }

    private fun eventPayload(event: AutomationEvent): String = when (event) {
        is AutomationEvent.RepositoryChanged -> JSONObject()
            .put("type", "repository_change")
            .put("workspaceId", event.workspaceId)
            .put("branch", event.branch)
            .put("fingerprint", event.fingerprint)
            .put("paths", event.changedPaths.take(200).joinToString("\n"))
            .toString()
        is AutomationEvent.BuildCompleted -> JSONObject()
            .put("type", "build_completion")
            .put("owner", event.owner)
            .put("repository", event.repository)
            .put("branch", event.branch)
            .put("runId", event.runId)
            .put("conclusion", event.conclusion)
            .put("target", event.target)
            .toString()
    }
}
