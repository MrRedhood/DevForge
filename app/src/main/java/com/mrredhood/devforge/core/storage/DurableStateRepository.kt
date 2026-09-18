package com.mrredhood.devforge.core.storage

import android.net.Uri
import com.mrredhood.devforge.core.editor.ContentHasher
import com.mrredhood.devforge.core.editor.ContentSnapshot
import com.mrredhood.devforge.core.editor.EditorTab
import com.mrredhood.devforge.core.editor.SnapshotReason
import com.mrredhood.devforge.core.recovery.RecoveryPolicy
import com.mrredhood.devforge.core.security.SecretRedactor
import java.util.UUID

/** Bounded Room-backed persistence shared by editor recovery and agent/automation execution. */
class DurableStateRepository(
    private val db: DevForgeDatabase,
) {
    private val tabs = db.editorTabDao()
    private val snapshots = db.editorSnapshotDao()
    private val agentTasks = db.agentTaskDao()
    private val automations = db.automationDao()
    private val triggerStates = db.automationTriggerStateDao()
    private val audit = db.auditEventDao()

    suspend fun loadEditorTabs(limit: Int = MAX_TABS): List<EditorTab> =
        tabs.list(limit).map { entity ->
            EditorTab(
                uri = Uri.parse(entity.uri),
                name = entity.name,
                content = entity.content,
                savedContent = entity.savedContent,
                updatedAt = entity.updatedAtEpochMs,
            )
        }

    suspend fun saveEditorTab(tab: EditorTab, active: Boolean) {
        require(tab.content.toByteArray(Charsets.UTF_8).size <= MAX_EDITOR_CONTENT_BYTES) { "Editor tab content exceeds the durable persistence limit." }
        if (active) tabs.clearActive()
        tabs.upsert(
            EditorTabEntity(
                uri = tab.uri.toString(),
                name = tab.name.take(MAX_NAME_LENGTH),
                content = tab.content,
                savedContent = tab.savedContent,
                isActive = active,
                updatedAtEpochMs = tab.updatedAt,
            ),
        )
    }

    suspend fun deleteEditorTab(uri: Uri) = tabs.delete(uri.toString())

    suspend fun clearEditorTabs() = tabs.clear()

    suspend fun saveSnapshot(snapshot: ContentSnapshot) {
        require(snapshot.content.toByteArray(Charsets.UTF_8).size <= MAX_SNAPSHOT_BYTES) { "Snapshot exceeds the durable persistence limit." }
        snapshots.upsert(
            EditorSnapshotEntity(
                snapshotId = snapshot.id,
                uri = snapshot.uri.toString(),
                name = snapshot.name.take(MAX_NAME_LENGTH),
                content = snapshot.content,
                contentHash = snapshot.contentHash,
                reason = snapshot.reason.name,
                createdAtEpochMs = snapshot.createdAt,
            ),
        )
        snapshots.prune(snapshot.uri.toString(), MAX_SNAPSHOTS_PER_FILE)
    }

    suspend fun latestSnapshot(uri: Uri): ContentSnapshot? = snapshots.latest(uri.toString())?.toDomain()

    suspend fun listSnapshots(uri: Uri, limit: Int = MAX_SNAPSHOTS_PER_FILE): List<ContentSnapshot> =
        snapshots.list(uri.toString(), limit).map { it.toDomain() }

    suspend fun clearSnapshots(uri: Uri) = snapshots.clear(uri.toString())

    suspend fun createSnapshot(uri: Uri, name: String, content: String, reason: SnapshotReason): ContentSnapshot =
        ContentSnapshot(
            id = UUID.randomUUID().toString(),
            uri = uri,
            name = name,
            content = content,
            contentHash = ContentHasher.sha256(content),
            createdAt = System.currentTimeMillis(),
            reason = reason,
        )

    suspend fun saveAgentTask(task: AgentTaskEntity) {
        require(task.instruction.toByteArray(Charsets.UTF_8).size <= MAX_AGENT_INSTRUCTION_BYTES) { "Agent task instruction exceeds the persistence limit." }
        require(!SecretRedactor.containsLikelySecret(task.instruction)) { "Agent task instruction contains secret-like material." }
        require(!SecretRedactor.containsLikelySecret(task.payload.orEmpty())) { "Agent task payload contains secret-like material." }
        require((task.payload ?: "").toByteArray(Charsets.UTF_8).size <= MAX_TASK_PAYLOAD_BYTES) { "Agent task payload exceeds the persistence limit." }
        require((task.result ?: "").toByteArray(Charsets.UTF_8).size <= MAX_AGENT_RESULT_BYTES) { "Agent task result exceeds the durable persistence limit." }
        require(task.currentStep in 0..MAX_AGENT_STEPS) { "Agent task step pointer exceeds the persistence limit." }
        require(task.stepCount in 0..MAX_AGENT_STEPS) { "Agent task step count exceeds the persistence limit." }
        agentTasks.upsert(
            task.copy(
                title = SecretRedactor.redact(task.title.take(MAX_NAME_LENGTH), MAX_NAME_LENGTH),
                errorMessage = task.errorMessage?.let { SecretRedactor.redact(it, MAX_ERROR_LENGTH) },
                approvalId = task.approvalId?.take(MAX_NAME_LENGTH),
                lastToolId = task.lastToolId?.take(MAX_NAME_LENGTH),
            ),
        )
    }

    suspend fun getAgentTask(taskId: String): AgentTaskEntity? = agentTasks.get(taskId)

    suspend fun trySaveAgentTask(task: AgentTaskEntity, maxNonTerminal: Int): Boolean {
        require(maxNonTerminal > 0) { "Agent task limit must be positive." }
        require(task.instruction.toByteArray(Charsets.UTF_8).size <= MAX_AGENT_INSTRUCTION_BYTES) {
            "Agent task instruction exceeds the persistence limit."
        }
        require((task.payload ?: "").toByteArray(Charsets.UTF_8).size <= MAX_TASK_PAYLOAD_BYTES) {
            "Agent task payload exceeds the persistence limit."
        }
        require((task.result ?: "").toByteArray(Charsets.UTF_8).size <= MAX_AGENT_RESULT_BYTES) {
            "Agent task result exceeds the durable persistence limit."
        }
        require(task.currentStep in 0..MAX_AGENT_STEPS) { "Agent task step pointer exceeds the persistence limit." }
        require(task.stepCount in 0..MAX_AGENT_STEPS) { "Agent task step count exceeds the persistence limit." }
        return agentTasks.insertIfBelowLimit(
            task.copy(
                title = task.title.take(MAX_NAME_LENGTH),
                errorMessage = task.errorMessage?.let { SecretRedactor.redact(it, MAX_ERROR_LENGTH) },
                approvalId = task.approvalId?.take(MAX_NAME_LENGTH),
                lastToolId = task.lastToolId?.take(MAX_NAME_LENGTH),
            ),
            maxNonTerminal,
        )
    }

    suspend fun listAgentTasks(workspaceId: String, limit: Int = MAX_AGENT_TASKS): List<AgentTaskEntity> =
        agentTasks.list(workspaceId, limit)

    suspend fun deleteAgentTask(taskId: String) = agentTasks.delete(taskId)

    suspend fun pauseAgentTask(taskId: String): Boolean =
        agentTasks.pause(taskId, System.currentTimeMillis()) > 0

    suspend fun resumeAgentTask(taskId: String): Boolean =
        agentTasks.resume(taskId, System.currentTimeMillis()) > 0

    suspend fun recoverRunningAgentTasks(workspaceId: String): Int =
        agentTasks.recoverRunning(
            workspaceId = workspaceId,
            updatedAt = System.currentTimeMillis(),
            message = RecoveryPolicy.AGENT_RECOVERY_MESSAGE,
        )

    suspend fun failWaitingAgentApproval(taskId: String, approvalId: String, message: String): Boolean =
        agentTasks.failWaitingApproval(
            taskId = taskId,
            approvalId = approvalId,
            updatedAt = System.currentTimeMillis(),
            message = message.take(MAX_ERROR_LENGTH),
        ) > 0

    suspend fun reconcileWaitingAgentApprovals(message: String = "Approval is no longer available."): Int =
        agentTasks.reconcileWaitingApprovals(
            updatedAt = System.currentTimeMillis(),
            message = message.take(MAX_ERROR_LENGTH),
        )

    fun observeAgentTasks(workspaceId: String, limit: Int = MAX_AGENT_TASKS) = agentTasks.observe(workspaceId, limit)

    suspend fun saveAutomation(automation: AutomationEntity) {
        require(automation.actionGraph.toByteArray(Charsets.UTF_8).size <= MAX_AUTOMATION_GRAPH_BYTES) { "Automation definition exceeds the persistence limit." }
        require(!SecretRedactor.containsLikelySecret(automation.actionGraph)) {
            "Automation action graph contains secret-like material and cannot be persisted."
        }
        require((automation.schedule ?: "").toByteArray(Charsets.UTF_8).size <= MAX_SCHEDULE_LENGTH) { "Automation trigger configuration exceeds the limit." }
        automations.upsert(
            automation.copy(
                name = SecretRedactor.redact(automation.name.take(MAX_NAME_LENGTH), MAX_NAME_LENGTH),
                schedule = automation.schedule?.take(MAX_SCHEDULE_LENGTH),
            ),
        )
    }

    suspend fun getAutomation(automationId: String): AutomationEntity? = automations.get(automationId)

    suspend fun listAutomations(limit: Int = MAX_AUTOMATIONS): List<AutomationEntity> = automations.list(limit)

    suspend fun deleteAutomation(automationId: String) {
        automations.delete(automationId)
        triggerStates.delete(automationId)
    }

    fun observeAutomations(limit: Int = MAX_AUTOMATIONS) = automations.observeAll(limit)

    suspend fun getAutomationTriggerState(automationId: String): AutomationTriggerStateEntity? = triggerStates.get(automationId)

    suspend fun saveAutomationTriggerState(state: AutomationTriggerStateEntity) = triggerStates.upsert(state)

    suspend fun resetAutomationTriggerState(automationId: String) = triggerStates.delete(automationId)

    suspend fun saveAutomationRun(run: AutomationRunEntity) {
        require((run.receiptJson ?: "").toByteArray(Charsets.UTF_8).size <= MAX_RECEIPT_BYTES) { "Automation receipt exceeds the persistence limit." }
        automations.upsertRun(
            run.copy(
                errorMessage = run.errorMessage?.let { SecretRedactor.redact(it, MAX_ERROR_LENGTH) },
                receiptJson = run.receiptJson?.let { SecretRedactor.redact(it, MAX_RECEIPT_BYTES) },
            ),
        )
    }

    suspend fun getAutomationRun(runId: String): AutomationRunEntity? = automations.getRun(runId)

    suspend fun tryStartAutomationRun(run: AutomationRunEntity): Boolean {
        require(run.status == "RUNNING") { "Only RUNNING automation runs may be started atomically." }
        require((run.receiptJson ?: "").toByteArray(Charsets.UTF_8).size <= MAX_RECEIPT_BYTES) {
            "Automation receipt exceeds the persistence limit."
        }
        return automations.insertRunIfNoActive(
            run.copy(
                errorMessage = run.errorMessage?.let { SecretRedactor.redact(it, MAX_ERROR_LENGTH) },
                receiptJson = run.receiptJson?.let { SecretRedactor.redact(it, MAX_RECEIPT_BYTES) },
            ),
        )
    }

    suspend fun activeAutomationRun(automationId: String): AutomationRunEntity? =
        automations.activeRun(automationId)

    suspend fun recentAutomationRuns(automationId: String, limit: Int = MAX_AUTOMATION_RUNS): List<AutomationRunEntity> =
        automations.recentRuns(automationId, limit)

    fun observeRecentAudit(limit: Int = MAX_AUDIT_EVENTS) = audit.observeRecent(limit)

    fun observeWorkspaceAudit(workspaceId: String, limit: Int = MAX_AUDIT_EVENTS) = audit.observeForWorkspace(workspaceId, limit)

    suspend fun recordAudit(event: AuditEventEntity) {
        require(event.summary.length <= MAX_SUMMARY_LENGTH) { "Audit summary exceeds the limit." }
        require((event.metadataJson ?: "").toByteArray(Charsets.UTF_8).size <= MAX_AUDIT_METADATA_BYTES) { "Audit metadata exceeds the limit." }
        audit.insert(event.copy(summary = event.summary.take(MAX_SUMMARY_LENGTH)))
    }

    suspend fun pruneAudit(retentionDays: Long = 30): Int =
        audit.prune(System.currentTimeMillis() - retentionDays * DAY_MS)

    private fun EditorSnapshotEntity.toDomain(): ContentSnapshot = ContentSnapshot(
        id = snapshotId,
        uri = Uri.parse(uri),
        name = name,
        content = content,
        contentHash = contentHash,
        createdAt = createdAtEpochMs,
        reason = runCatching { SnapshotReason.valueOf(reason) }.getOrDefault(SnapshotReason.MANUAL),
    )

    companion object {
        const val MAX_TABS = 24
        const val MAX_EDITOR_CONTENT_BYTES = 8 * 1024 * 1024
        const val MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024
        const val MAX_SNAPSHOTS_PER_FILE = 12
        const val MAX_AGENT_TASKS = 50
        const val MAX_AGENT_STEPS = 12
        const val MAX_AUTOMATIONS = 50
        const val MAX_AUTOMATION_RUNS = 50
        const val MAX_AUDIT_EVENTS = 200
        const val MAX_AGENT_INSTRUCTION_BYTES = 64 * 1024
        const val MAX_TASK_PAYLOAD_BYTES = 256 * 1024
        const val MAX_AGENT_RESULT_BYTES = 64 * 1024
        const val MAX_AUTOMATION_GRAPH_BYTES = 256 * 1024
        const val MAX_RECEIPT_BYTES = 64 * 1024
        const val MAX_AUDIT_METADATA_BYTES = 32 * 1024
        const val MAX_NAME_LENGTH = 200
        const val MAX_SUMMARY_LENGTH = 500
        const val MAX_ERROR_LENGTH = 600
        const val MAX_SCHEDULE_LENGTH = 500
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
