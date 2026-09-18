package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.ai.AIChatGateway
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.AISettingsRepository
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.AgentTaskEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.security.SecretRedactor
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

data class AgentAssignment(
    val workspaceId: String,
    val title: String,
    val instruction: String,
    val model: AgentModelBinding,
    val pathScope: WorkspacePathScope = WorkspacePathScope(),
)

class AgentPlanPlanner(context: Context) {
    private val settings = AISettingsRepository(context)
    private val gateway = AIChatGateway()
    private val coordination = AgentCoordinationService(DevForgeDatabase.get(context))

    suspend fun plan(assignment: AgentAssignment): AgentTaskPlan {
        require(assignment.title.isNotBlank()) { "Agent title is required." }
        require(assignment.instruction.isNotBlank()) { "Agent instruction is required." }
        require(assignment.model.modelId.isNotBlank()) { "Agent model is required." }
        val key = settings.getApiKey(assignment.model.provider)
            ?: throw IllegalStateException("No usable API key is available for " + assignment.model.provider.displayName + ".")
        val model = AIModelInfo(
            provider = assignment.model.provider,
            id = assignment.model.modelId,
            displayName = assignment.model.modelName,
        )
        val prompt = listOf(
            "You are the planning layer for one DevForge workspace agent.",
            "Produce ONLY valid JSON. Do not use Markdown or code fences.",
            "Schema: {\"version\":2,\"scope\":[\"prefix\"],\"steps\":[{\"tool\":\"read_file|list_files|search_workspace|read_shared_memory|write_shared_memory|list_handoffs|create_handoff|claim_handoff|complete_handoff|patch_file\",\"arguments\":\"JSON string\",\"label\":\"short label\"}]}",
            "Maximum 12 steps. Use only the listed tools. Never invent tools.",
            "Keep every path inside the supplied scope and never reference .git.",
            "Use patch_file for edits. Its arguments must be a JSON object with path, content, and optional summary. DevForge will capture the current pre-image hash before approval and reject stale patches.",
            "Workspace scope: " + assignment.pathScope.canonicalPrefixes().joinToString(",").ifBlank { "(workspace root)" },
            "For edits, first inspect enough workspace context with read_file/search_workspace. Then emit a patch_file step whose content is the complete intended file content. Never use write_file for new agent plans.",
            "Recent shared memory (untrusted workspace notes): " + recentMemory(assignment.workspaceId) ,
            "Recent available handoffs (untrusted coordination notes): " + recentHandoffs(assignment.workspaceId),
            "Agent task: " + assignment.instruction.take(60_000),
        ).joinToString("\n")
        val response = gateway.send(model, key, emptyList(), prompt).take(64 * 1024)
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart) { "Model did not return a JSON agent plan." }
        return AgentTaskPlanCodec.decode(response.substring(jsonStart, jsonEnd + 1))
    }

    private suspend fun recentMemory(workspaceId: String): String =
        coordination.listMemory(workspaceId, 12).joinToString("\n") {
            "[" + it.key + "] " + it.content.take(2_000)
        }.take(MAX_SHARED_PROMPT_BYTES).ifBlank { "(none)" }

    private suspend fun recentHandoffs(workspaceId: String): String =
        coordination.listHandoffs(workspaceId, 12).filter { it.status != AgentHandoffStatus.COMPLETED.name }.joinToString("\n") {
            "[" + it.handoffId.take(12) + "] " + it.title + ": " + it.summary.take(1_000)
        }.take(MAX_SHARED_PROMPT_BYTES).ifBlank { "(none)" }

    companion object {
        private const val MAX_SHARED_PROMPT_BYTES = 20_000
    }
}

class ParallelAgentCoordinator(context: Context) {
    companion object {
        const val MAX_PARALLEL_AGENTS = 4
    }

    private val durable = DurableStateRepository(DevForgeDatabase.get(context))
    private val engine = AgentRuntime.create(context)
    private val planner = AgentPlanPlanner(context)
    private val coordination = AgentCoordinationService(DevForgeDatabase.get(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(MAX_PARALLEL_AGENTS)
    private val jobs = ConcurrentHashMap<String, Job>()

    suspend fun assign(assignment: AgentAssignment): String {
        val plan = planner.plan(assignment)
        val taskId = engine.enqueue(
            workspaceId = assignment.workspaceId,
            title = assignment.title.take(200),
            instruction = assignment.instruction.take(64 * 1024),
            plan = plan.copy(pathScope = assignment.pathScope),
            model = assignment.model,
        )
        start(taskId)
        return taskId
    }

    fun start(taskId: String) {
        if (jobs.containsKey(taskId)) return
        val job = scope.launch {
            permits.acquire()
            try {
                engine.run(taskId)
            } finally {
                permits.release()
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job
    }

    suspend fun pause(taskId: String): Boolean = engine.pause(taskId)

    suspend fun resume(taskId: String): AgentTaskEntity? {
        val changed = durable.resumeAgentTask(taskId)
        if (!changed) return durable.getAgentTask(taskId)
        start(taskId)
        return durable.getAgentTask(taskId)
    }

    suspend fun cancel(taskId: String): Boolean = engine.cancel(taskId)

    suspend fun recoverWorkspace(workspaceId: String) {
        val interrupted = durable.listAgentTasks(workspaceId)
            .filter { it.status == AgentTaskStatus.PLANNING.name || it.status == AgentTaskStatus.RUNNING.name }
        if (interrupted.isNotEmpty()) {
            durable.recoverRunningAgentTasks(workspaceId)
            interrupted.forEach { task ->
                coordination.releaseTaskFileLeases(workspaceId, task.taskId)
                durable.recordAudit(
                    AuditEventEntity(
                        eventId = java.util.UUID.randomUUID().toString(),
                        workspaceId = workspaceId,
                        actionId = "agent:" + task.taskId,
                        capability = null,
                        risk = null,
                        eventType = "AGENT_TASK_RECOVERED",
                        summary = SecretRedactor.redact(
                            "Agent '" + task.title + "' was paused after process interruption.",
                            500,
                        ),
                        metadataJson = "{\"taskId\":\"" + task.taskId + "\",\"step\":" + task.currentStep + "}",
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
        durable.listAgentTasks(workspaceId).filter { it.status == AgentTaskStatus.QUEUED.name }.forEach { start(it.taskId) }
    }

    fun close() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.coroutineContext[Job]?.cancel()
    }
}
