package com.mrredhood.devforge.core.agent

import org.json.JSONArray
import org.json.JSONObject

/** Durable execution states for the bounded agent runner. */
enum class AgentTaskStatus {
    QUEUED,
    PLANNING,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class AgentToolId(val wireName: String) {
    READ_FILE("read_file"),
    LIST_FILES("list_files"),
    SEARCH_WORKSPACE("search_workspace"),
    WRITE_FILE("write_file"),
}

data class AgentToolDefinition(
    val id: AgentToolId,
    val description: String,
    val capability: com.mrredhood.devforge.core.policy.Capability,
    val risk: com.mrredhood.devforge.core.policy.RiskLevel,
    val sideEffecting: Boolean,
)

data class AgentToolContext(
    val workspaceId: String,
    val taskId: String,
    val stepIndex: Int,
)

data class AgentToolRequest(
    val toolId: AgentToolId,
    val workspaceId: String,
    val argumentsJson: String,
    val taskId: String,
    val stepIndex: Int,
)

data class AgentToolReceipt(
    val taskId: String,
    val stepIndex: Int,
    val toolId: String,
    val capability: String,
    val risk: String,
    val workspaceId: String,
    val affectedPaths: List<String>,
    val summary: String,
    val output: String,
    val approvalId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

sealed interface AgentToolResult {
    data class Success(
        val summary: String,
        val output: String = "",
        val affectedPaths: List<String> = emptyList(),
    ) : AgentToolResult

    data class ApprovalRequired(
        val approvalId: String,
        val summary: String,
    ) : AgentToolResult

    data class Failure(val message: String) : AgentToolResult
}

data class AgentTaskStep(
    val toolId: AgentToolId,
    val argumentsJson: String,
    val label: String,
) {
    fun toRequest(workspaceId: String, taskId: String, index: Int): AgentToolRequest =
        AgentToolRequest(toolId, workspaceId, argumentsJson, taskId, index)
}

data class AgentTaskPlan(val steps: List<AgentTaskStep>) {
    init {
        require(steps.size <= MAX_STEPS) { "Agent plans may contain at most $MAX_STEPS steps." }
    }

    companion object {
        const val MAX_STEPS = 12
    }
}

object AgentTaskPlanCodec {
    fun encode(plan: AgentTaskPlan): String {
        val root = JSONObject()
        val steps = JSONArray()
        plan.steps.forEach { step ->
            steps.put(
                JSONObject()
                    .put("tool", step.toolId.wireName)
                    .put("arguments", step.argumentsJson)
                    .put("label", step.label.take(200)),
            )
        }
        root.put("version", 1)
        root.put("steps", steps)
        return root.toString()
    }

    fun decode(payload: String?): AgentTaskPlan {
        require(!payload.isNullOrBlank()) { "Agent task plan is missing." }
        val root = JSONObject(payload)
        require(root.optInt("version", 1) == 1) { "Unsupported agent task plan version." }
        val array = root.optJSONArray("steps") ?: JSONArray()
        require(array.length() <= AgentTaskPlan.MAX_STEPS) { "Agent plan exceeds the step limit." }
        val steps = buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: throw IllegalArgumentException("Invalid agent step $index.")
                val tool = AgentToolId.entries.firstOrNull { it.wireName == item.optString("tool") }
                    ?: throw IllegalArgumentException("Unknown agent tool at step $index.")
                val arguments = item.optString("arguments", "{}")
                require(arguments.toByteArray(Charsets.UTF_8).size <= MAX_TOOL_ARGUMENT_BYTES) { "Agent tool arguments exceed the limit." }
                add(AgentTaskStep(tool, arguments, item.optString("label", tool.wireName)))
            }
        }
        return AgentTaskPlan(steps)
    }

    const val MAX_TOOL_ARGUMENT_BYTES = 64 * 1024
}

const val MAX_AGENT_RECEIPT_BYTES = 64 * 1024
