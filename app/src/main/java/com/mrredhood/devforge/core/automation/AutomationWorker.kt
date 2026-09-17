package com.mrredhood.devforge.core.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrredhood.devforge.core.agent.AgentRuntime
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import kotlinx.coroutines.CancellationException

class AutomationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val automationId = inputData.getString(KEY_AUTOMATION_ID) ?: return Result.failure()
        val attempt = inputData.getInt(KEY_ATTEMPT, 1).coerceIn(1, AutomationScheduler.MAX_ATTEMPTS)
        val triggerPayload = inputData.getString(KEY_TRIGGER_PAYLOAD)?.take(AutomationScheduler.MAX_EVENT_PAYLOAD_BYTES)
        val database = DevForgeDatabase.get(applicationContext)
        val durable = DurableStateRepository(database)
        val engine = AutomationRunEngine(applicationContext, durable, AgentRuntime.create(applicationContext))

        return try {
            val outcome = engine.execute(automationId, attempt, triggerPayload)
            val automation = durable.getAutomation(automationId)
            if (automation?.status == AutomationStatus.ENABLED.name) {
                when {
                    outcome.status == AutomationRunStatus.WAITING_APPROVAL -> Unit
                    outcome.shouldRetry -> AutomationScheduler.scheduleRetry(applicationContext, automationId, attempt + 1, outcome.retryDelayMs)
                    else -> AutomationScheduler.scheduleNext(applicationContext, automation)
                }
            }
            Result.success()
        } catch (_: CancellationException) {
            Result.failure()
        } catch (_: Throwable) {
            // The durable run record remains the source of truth; do not let WorkManager
            // duplicate side effects automatically.
            Result.success()
        }
    }

    companion object {
        const val KEY_AUTOMATION_ID = "automation_id"
        const val KEY_ATTEMPT = "attempt"
        const val KEY_TRIGGER_KEY = "trigger_key"
        const val KEY_TRIGGER_PAYLOAD = "trigger_payload"
    }
}
