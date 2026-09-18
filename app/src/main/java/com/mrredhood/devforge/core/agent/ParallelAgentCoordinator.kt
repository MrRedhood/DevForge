package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.ai.AIChatGateway
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.AISettingsRepository
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.AgentTaskEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
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
            "Schema: {\"version\":2,\"scope\":[\"prefix\"],\"steps\":[{\"tool\":\"read_file|list_files|search_workspace|write_file\",\"arguments\":\"JSON string\",\"label\":\"short label\"}]}",
            "Maximum 12 steps. Use only the listed tools. Never invent tools.",
            "Keep every path inside the supplied scope and never reference .git.",
            "write_file is allowed when the user's instruction asks for edits; actual execution is separately capability-gated.",
            "Workspace scope: " + assignment.pathScope.canonicalPrefixes().joinToString(",").ifBlank { "(workspace root)" },
            "Agent task: " + assignment.instruction.take(60_000),
        ).joinToString("\n")
        val response = gateway.send(model, key, emptyList(), prompt).take(64 * 1024)
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart) { "Model did not return a JSON agent plan." }
        return AgentTaskPlanCodec.decode(response.substring(jsonStart, jsonEnd + 1))
    }
}

class ParallelAgentCoordinator(context: Context) {
    companion object {
        const val MAX_PARALLEL_AGENTS = 4
    }

    private val durable = DurableStateRepository(DevForgeDatabase.get(context))
    private val engine = AgentRuntime.create(context)
    private val planner = AgentPlanPlanner(context)
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
        val result = engine.resume(taskId)
        if (result?.status == AgentTaskStatus.QUEUED.name || result?.status == AgentTaskStatus.RUNNING.name) start(taskId)
        return result
    }

    suspend fun cancel(taskId: String): Boolean = engine.cancel(taskId)

    suspend fun recoverWorkspace(workspaceId: String) {
        durable.recoverRunningAgentTasks(workspaceId)
        durable.listAgentTasks(workspaceId).filter { it.status == AgentTaskStatus.QUEUED.name }.forEach { start(it.taskId) }
    }

    fun close() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.coroutineContext[Job]?.cancel()
    }
}
