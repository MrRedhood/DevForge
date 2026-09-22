package com.mrredhood.devforge.core.automation

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitFileStatus
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.git.GitWorkspaceStatusService
import com.mrredhood.devforge.core.storage.AutomationEntity
import com.mrredhood.devforge.core.storage.AutomationTriggerStateEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import kotlinx.coroutines.flow.first

/**
 * Bounded background event monitor. It bootstraps the current state without firing historical
 * events, then dispatches only newly observed repository/build changes.
 */
class AutomationEventMonitorWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val database = DevForgeDatabase.get(context)
        val durable = DurableStateRepository(database)
        val automations = durable.listAutomations().filter { it.status == AutomationStatus.ENABLED.name }

        monitorRepositories(context, durable, automations)
        monitorBuilds(context, durable, automations)
        return Result.success()
    }

    private suspend fun monitorRepositories(
        context: Context,
        durable: DurableStateRepository,
        automations: List<AutomationEntity>,
    ) {
        val workspaceIds = automations
            .filter { it.triggerType == AutomationTriggerType.REPOSITORY_CHANGE.name || it.triggerType == AutomationTriggerType.CONDITION.name }
            .mapNotNull { it.workspaceId }
            .distinct()
            .take(MAX_WORKSPACES)
        val workspaceDao = DevForgeDatabase.get(context).workspaceDao()
        val repositoryService = GitRepositoryService(context.contentResolver)
        val statusService = GitWorkspaceStatusService(context.contentResolver)

        for (workspaceId in workspaceIds) {
            val workspace = workspaceDao.findById(workspaceId) ?: continue
            val root = Uri.parse(workspace.treeUri)
            val detected = repositoryService.detect(root) as? GitDetectionState.Detected ?: continue
            val status = statusService.inspect(detected.repository.rootUri, detected.repository.gitDirectoryUri, detected.repository.headRevision)
            if (status.truncated) continue

            val stateParts = buildList {
                add("head:${detected.repository.headRevision.orEmpty()}")
                addAll(status.files.take(MAX_FILES).map { "${it.path}:${it.gitStatus.name}" })
            }
            val changedPaths = status.files
                .filter { it.gitStatus != GitFileStatus.Clean && it.gitStatus != GitFileStatus.Unchecked }
                .map { it.path }
                .take(MAX_FILES)
            val fingerprint = AutomationTriggerCodec.eventFingerprint(workspaceId, detected.repository.branchName, stateParts)
            automations.filter { it.workspaceId == workspaceId && (it.triggerType == AutomationTriggerType.REPOSITORY_CHANGE.name || it.triggerType == AutomationTriggerType.CONDITION.name) }
                .forEach { automation ->
                    val state = durable.getAutomationTriggerState(automation.automationId)
                    if (state == null || state.lastRepositoryFingerprint == null) {
                        durable.saveAutomationTriggerState(
                            (state ?: AutomationTriggerStateEntity(automation.automationId, null, null, 0L)).copy(
                                lastRepositoryFingerprint = fingerprint,
                                lastEvaluatedAtEpochMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }

            AutomationEventDispatcher.dispatch(
                context,
                AutomationEvent.RepositoryChanged(
                    workspaceId = workspaceId,
                    branch = detected.repository.branchName,
                    changedPaths = changedPaths,
                    fingerprint = fingerprint,
                ),
            )
        }
    }

    private suspend fun monitorBuilds(
        context: Context,
        durable: DurableStateRepository,
        automations: List<AutomationEntity>,
    ) {
        if (automations.none { it.triggerType == AutomationTriggerType.BUILD_COMPLETION.name || it.triggerType == AutomationTriggerType.CONDITION.name }) return
        val receipts = DevForgeDatabase.get(context).buildReceiptDao().observeRecent(MAX_BUILD_RECEIPTS).first().sortedBy { it.runId }
        if (receipts.isEmpty()) return

        val buildAutomations = automations.filter {
            it.triggerType == AutomationTriggerType.BUILD_COMPLETION.name || it.triggerType == AutomationTriggerType.CONDITION.name
        }
        val latest = receipts.last()
        for (automation in buildAutomations) {
            val state = durable.getAutomationTriggerState(automation.automationId)
            if (state == null || state.lastBuildRunId == null) {
                durable.saveAutomationTriggerState(
                    (state ?: AutomationTriggerStateEntity(automation.automationId, null, null, 0L)).copy(
                        lastBuildRunId = latest.runId,
                        lastEvaluatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                continue
            }
            receipts.filter { it.runId > state.lastBuildRunId }.forEach { receipt ->
                AutomationEventDispatcher.dispatch(context, receipt.toEvent())
            }
        }
    }

    private fun com.mrredhood.devforge.core.storage.BuildReceiptEntity.toEvent(): AutomationEvent.BuildCompleted =
        AutomationEvent.BuildCompleted(
            owner = githubOwner,
            repository = githubRepository,
            branch = branch,
            runId = runId,
            conclusion = conclusion,
            target = target,
        )

    companion object {
        private const val MAX_WORKSPACES = 24
        private const val MAX_FILES = 500
        private const val MAX_BUILD_RECEIPTS = 25
    }
}
