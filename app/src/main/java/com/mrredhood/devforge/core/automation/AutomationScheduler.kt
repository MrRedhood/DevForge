package com.mrredhood.devforge.core.automation

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mrredhood.devforge.core.storage.AutomationEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Durable WorkManager-backed scheduling facade and event-trigger bootstrap. */
object AutomationScheduler {
    private const val TAG_PREFIX = "devforge-automation:"
    private const val WORK_PREFIX = "devforge-automation"
    private const val EVENT_MONITOR_WORK = "devforge-automation-event-monitor"

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repository = DurableStateRepository(DevForgeDatabase.get(appContext))
            repository.listAutomations().forEach { automation ->
                if (automation.status == AutomationStatus.ENABLED.name) scheduleNext(appContext, automation)
            }
        }
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            EVENT_MONITOR_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutomationEventMonitorWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    suspend fun triggerNow(context: Context, automationId: String): Boolean {
        val automation = DurableStateRepository(DevForgeDatabase.get(context.applicationContext)).getAutomation(automationId) ?: return false
        if (automation.status != AutomationStatus.ENABLED.name) return false
        enqueueAt(context.applicationContext, automationId, attempt = 1, dueEpochMs = System.currentTimeMillis(), triggerKey = "manual", triggerPayload = null)
        return true
    }

    suspend fun triggerEvent(context: Context, automationId: String, eventKey: String, payload: String?): Boolean {
        val automation = DurableStateRepository(DevForgeDatabase.get(context.applicationContext)).getAutomation(automationId) ?: return false
        if (automation.status != AutomationStatus.ENABLED.name) return false
        enqueueAt(
            context = context.applicationContext,
            automationId = automationId,
            attempt = 1,
            dueEpochMs = System.currentTimeMillis(),
            triggerKey = eventKey.take(180),
            triggerPayload = payload?.take(MAX_EVENT_PAYLOAD_BYTES),
        )
        return true
    }

    suspend fun scheduleNext(context: Context, automation: AutomationEntity) {
        if (automation.status != AutomationStatus.ENABLED.name) return
        val due = AutomationScheduleParser.nextEpochMs(automation.schedule) ?: return
        enqueueAt(context.applicationContext, automation.automationId, attempt = 1, dueEpochMs = due, triggerKey = null, triggerPayload = null)
    }

    fun scheduleRetry(context: Context, automationId: String, attempt: Int, delayMs: Long) {
        val due = System.currentTimeMillis() + delayMs.coerceIn(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
        enqueueAt(context, automationId, attempt, due, triggerKey = null, triggerPayload = null)
    }

    fun cancel(context: Context, automationId: String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(tag(automationId))
    }

    private fun enqueueAt(
        context: Context,
        automationId: String,
        attempt: Int,
        dueEpochMs: Long,
        triggerKey: String?,
        triggerPayload: String?,
    ) {
        val delay = (dueEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val input = workDataOf(
            AutomationWorker.KEY_AUTOMATION_ID to automationId,
            AutomationWorker.KEY_ATTEMPT to attempt.coerceIn(1, MAX_ATTEMPTS),
            AutomationWorker.KEY_TRIGGER_KEY to triggerKey,
            AutomationWorker.KEY_TRIGGER_PAYLOAD to triggerPayload,
        )
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(tag(automationId))
            .build()
        val uniqueName = if (triggerKey.isNullOrBlank()) {
            "$WORK_PREFIX:$automationId:$dueEpochMs"
        } else {
            "$WORK_PREFIX:event:$automationId:$triggerKey"
        }
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    private fun tag(automationId: String) = "$TAG_PREFIX$automationId"

    const val MAX_ATTEMPTS = 3
    const val MAX_EVENT_PAYLOAD_BYTES = 16 * 1024
    private const val MIN_RETRY_DELAY_MS = 60_000L
    private const val MAX_RETRY_DELAY_MS = 15L * 60L * 1000L
}
