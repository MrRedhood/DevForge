package com.mrredhood.devforge.core.storage

import android.net.Uri
import com.mrredhood.devforge.core.editor.ContentHasher
import com.mrredhood.devforge.core.editor.ContentSnapshot
import com.mrredhood.devforge.core.editor.EditorTab
import com.mrredhood.devforge.core.editor.SnapshotReason
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
        require((task.payload ?: "").toByteArray(Charsets.UTF_8).size <= MAX_TASK_PAYLOAD_BYTES) { "Agent task payload exceeds the persistence limit." }
        require((task.result ?: "").toByteArray(Charsets.UTF_8).size <= MAX_AGENT_RESULT_BYTES) { "Agent task result exceeds the durable persistence limit." }
        require(task.currentStep in 0..MAX_AGENT_STEPS) { "Agent task step pointer exceeds the persistence limit." }
        require(task.stepCount in 0..MAX_AGENT_STEPS) { "Agent task step count exceeds the persistence limit." }
        agentTasks.upsert(
            task.copy(
                title = task.title.take(MAX_NAME_LENGTH),
                errorMessage = task.errorMessage?.take(MAX_ERROR_LENGTH),
                approvalId = task.approvalId?.take(MAX_NAME_LENGTH),
                lastToolId = task.lastToolId?.take(MAX_NAME_LENGTH),
            ),
        )
    }

    suspend fun getAgentTask(taskId: String): AgentTaskEntity? = agentTasks.get(taskId)

    suspend fun deleteAgentTask(taskId: String) = agentTasks.delete(taskId)

    fun observeAgentTasks(workspaceId: String, limit: Int = MAX_AGENT_TASKS) = agentTasks.observe(workspaceId, limit)

    suspend fun saveAutomation(automation: AutomationEntity) {
        require(automation.actionGraph.toByteArray(Charsets.UTF_8).size <= MAX_AUTOMATION_GRAPH_BYTES) { "Automation definition exceeds the persistence limit." }
        require((automation.schedule ?: "").toByteArray(Charsets.UTF_8).size <= MAX_SCHEDULE_LENGTH) { "Automation trigger configuration exceeds the limit." }
        automations.upsert(
            automation.copy(
                name = automation.name.take(MAX_NAME_LENGTH),
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

    suspend fun saveAutomationRun(run: AutomationRunEntity) {
        require((run.receiptJson ?: "").toByteArray(Charsets.UTF_8).size <= MAX_RECEIPT_BYTES) { "Automation receipt exceeds the persistence limit." }
        automations.upsertRun(run.copy(errorMessage = run.errorMessage?.take(MAX_ERROR_LENGTH)))
    }

    suspend fun getAutomationRun(runId: String): AutomationRunEntity? = automations.getRun(runId)

    suspend fun recentAutomationRuns(automationId: String, limit: Int = MAX_AUTOMATION_RUNS): List<AutomationRunEntity> =
        automations.recentRuns(automationId, limit)

    fun observeRecentAudit(limit: Int = MAX_AUDIT_EVENTS) = audit.observeRecent(limit)

    fun observeWorkspaceAudit(workspaceId: String, limit: Int = MAX_AUDIT_EVENTS) = audit.observeForWorkspace(workspaceId, limit)

    suspend fun recordAudit(event: AuditEventEntity) {
        require(event.summary.length <= MAX_SUMMARY_LENGTH) { "Audit summary exceeds the persistence limit." }
        require((event.metadataJson ?: "").toByteArray(Charsets.UTF_8).size <= MAX_AUDIT_METADATA_BYTES) { "Audit metadata exceeds the persistence limit." }
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
