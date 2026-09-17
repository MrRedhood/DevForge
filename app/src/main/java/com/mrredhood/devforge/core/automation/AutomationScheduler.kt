package com.mrredhood.devforge.core.automation

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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

/** Durable WorkManager-backed scheduling facade. */
object AutomationScheduler {
    private const val TAG_PREFIX = "devforge-automation:"
    private const val WORK_PREFIX = "devforge-automation"

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repository = DurableStateRepository(DevForgeDatabase.get(appContext))
            repository.listAutomations().forEach { automation ->
                if (automation.status == AutomationStatus.ENABLED.name) scheduleNext(appContext, automation)
            }
        }
    }

    suspend fun triggerNow(context: Context, automationId: String): Boolean {
        val automation = DurableStateRepository(DevForgeDatabase.get(context.applicationContext)).getAutomation(automationId) ?: return false
        if (automation.status != AutomationStatus.ENABLED.name) return false
        enqueueAt(context.applicationContext, automationId, attempt = 1, dueEpochMs = System.currentTimeMillis())
        return true
    }

    suspend fun scheduleNext(context: Context, automation: AutomationEntity) {
        if (automation.status != AutomationStatus.ENABLED.name) return
        val due = AutomationScheduleParser.nextEpochMs(automation.schedule) ?: return
        enqueueAt(context.applicationContext, automation.automationId, attempt = 1, dueEpochMs = due)
    }

    fun scheduleRetry(context: Context, automationId: String, attempt: Int, delayMs: Long) {
        val due = System.currentTimeMillis() + delayMs.coerceIn(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
        enqueueAt(context.applicationContext, automationId, attempt, due)
    }

    fun cancel(context: Context, automationId: String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(tag(automationId))
    }

    private fun enqueueAt(context: Context, automationId: String, attempt: Int, dueEpochMs: Long) {
        val delay = (dueEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                AutomationWorker.KEY_AUTOMATION_ID to automationId,
                AutomationWorker.KEY_ATTEMPT to attempt.coerceIn(1, MAX_ATTEMPTS),
            ))
            .addTag(tag(automationId))
            .build()
        val uniqueName = "$WORK_PREFIX:$automationId:$dueEpochMs"
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    private fun tag(automationId: String) = "$TAG_PREFIX$automationId"

    const val MAX_ATTEMPTS = 3
    private const val MIN_RETRY_DELAY_MS = 60_000L
    private const val MAX_RETRY_DELAY_MS = 15L * 60L * 1000L
}
