package com.mrredhood.devforge.core.ai.workflow

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mrredhood.devforge.core.security.SecretRedactor
import java.util.UUID

class AiWorkflowEngine(context: Context) {
    private val appContext = context.applicationContext
    private val store = AiWorkflowStore(appContext)
    private val verifier = AiVerificationEngine(appContext)

    fun start(
        request: String,
        workspaceId: String?,
        workspaceName: String?,
    ): AiWorkflowSnapshot {
        val now = System.currentTimeMillis()
        val snapshot = AiWorkflowSnapshot(
            workflowId = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            workspaceName = workspaceName,
            request = SecretRedactor.redact(request.take(MAX_REQUEST_CHARS), MAX_REQUEST_CHARS),
            phase = AiWorkflowPhase.UNDERSTAND,
            status = AiWorkflowSnapshot.Status.RUNNING,
            currentStep = "Understanding request",
            activities = listOf(
                AiActivity(
                    id = UUID.randomUUID().toString(),
                    kind = AiActivityKind.INFO,
                    status = AiActivityStatus.RUNNING,
                    title = "Understanding request",
                    detail = "Preparing workspace context and a safe execution plan.",
                    createdAtEpochMs = now,
                ),
            ),
            verification = null,
            summary = null,
            startedAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        store.save(snapshot)
        scheduleRecovery()
        return snapshot
    }

    fun setPlan(
        snapshot: AiWorkflowSnapshot,
        steps: List<AiPlanStep>,
    ): AiWorkflowSnapshot {
        val bounded = steps.take(8).mapIndexed { index, step ->
            step.copy(
                id = step.id.ifBlank { "plan-" + index },
                title = step.title.trim().take(180).ifBlank { "Implementation step " + (index + 1) },
                detail = step.detail.trim().take(500),
                status = if (index == 0) AiPlanStepStatus.RUNNING else AiPlanStepStatus.PENDING,
            )
        }
        val updated = snapshot.copy(
            phase = if (bounded.isNotEmpty()) AiWorkflowPhase.PLAN else snapshot.phase,
            currentStep = bounded.firstOrNull()?.title ?: snapshot.currentStep,
            generatedPlan = bounded,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        store.save(updated)
        return updated
    }

    fun updatePlanStep(
        snapshot: AiWorkflowSnapshot,
        index: Int,
        status: AiPlanStepStatus,
        detail: String? = null,
    ): AiWorkflowSnapshot {
        if (snapshot.generatedPlan.isEmpty() || index !in snapshot.generatedPlan.indices) return snapshot
        val updatedSteps = snapshot.generatedPlan.toMutableList()
        val current = updatedSteps[index]
        updatedSteps[index] = current.copy(
            status = status,
            detail = detail?.trim()?.take(500)?.ifBlank { current.detail } ?: current.detail,
        )
        if (status == AiPlanStepStatus.COMPLETED && index + 1 < updatedSteps.size) {
            val next = updatedSteps[index + 1]
            if (next.status == AiPlanStepStatus.PENDING) {
                updatedSteps[index + 1] = next.copy(status = AiPlanStepStatus.RUNNING)
            }
        }
        val updated = snapshot.copy(
            currentStep = updatedSteps[index].title,
            generatedPlan = updatedSteps,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        store.save(updated)
        return updated
    }

    fun phase(
        snapshot: AiWorkflowSnapshot,
        phase: AiWorkflowPhase,
        step: String,
    ): AiWorkflowSnapshot {
        val updated = snapshot.copy(
            phase = phase,
            status = AiWorkflowSnapshot.Status.RUNNING,
            currentStep = step.take(240),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        store.save(updated)
        return updated
    }

    fun activity(
        snapshot: AiWorkflowSnapshot,
        activity: AiActivity,
    ): AiWorkflowSnapshot {
        val updated = snapshot.copy(
            activities = snapshot.activities
                .filterNot { it.id == activity.id }
                .plus(activity)
                .takeLast(MAX_ACTIVITIES),
            currentStep = activity.detail.takeIf { it.isNotBlank() }?.take(240) ?: snapshot.currentStep,
            status = if (activity.status == AiActivityStatus.WAITING) {
                AiWorkflowSnapshot.Status.WAITING
            } else {
                snapshot.status
            },
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        store.save(updated)
        return updated
    }

    suspend fun verifyAndComplete(
        snapshot: AiWorkflowSnapshot,
        workspaceRoot: Uri?,
        changedPaths: List<String>,
        summary: String,
        requestedBuild: Boolean = false,
        requestedTests: Boolean = false,
        requestedLint: Boolean = false,
    ): AiWorkflowSnapshot {
        val observedPaths = (changedPaths + snapshot.activities.flatMap { it.paths })
            .filter(String::isNotBlank)
            .distinct()
            .take(40)
        val verification = verifier.verify(
            workspaceId = snapshot.workspaceId,
            workspaceRoot = workspaceRoot,
            changedPaths = observedPaths,
            missionStartedAtEpochMs = snapshot.startedAtEpochMs,
            requestedBuild = requestedBuild,
            requestedTests = requestedTests,
            requestedLint = requestedLint,
        )
        val status = if (verification.failedCount > 0) {
            AiWorkflowSnapshot.Status.FAILED
        } else {
            AiWorkflowSnapshot.Status.COMPLETED
        }
        val updated = snapshot.copy(
            phase = AiWorkflowPhase.REVIEW,
            status = status,
            currentStep = null,
            verification = verification,
            summary = SecretRedactor.redact(summary.take(MAX_SUMMARY_CHARS), MAX_SUMMARY_CHARS),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        store.save(updated)
        store.prune()
        store.clearActive()
        return updated
    }

    fun fail(snapshot: AiWorkflowSnapshot, message: String): AiWorkflowSnapshot {
        val updated = snapshot.copy(
            status = AiWorkflowSnapshot.Status.FAILED,
            currentStep = null,
            summary = SecretRedactor.redact(message.take(MAX_SUMMARY_CHARS), MAX_SUMMARY_CHARS),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        store.save(updated)
        store.clearActive()
        return updated
    }

    fun active(): AiWorkflowSnapshot? = store.active()

    fun scheduleRecovery() {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "devforge-ai-recovery",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AiBackgroundTaskWorker>()
                .setConstraints(Constraints.Builder().build())
                .build(),
        )
    }

    companion object {
        private const val MAX_REQUEST_CHARS = 12_000
        private const val MAX_SUMMARY_CHARS = 6_000
        private const val MAX_ACTIVITIES = 180
    }
}
