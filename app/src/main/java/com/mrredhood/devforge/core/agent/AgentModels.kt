package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.json.JSONArray
import org.json.JSONObject

/** Durable execution states for the bounded agent runner. */
enum class AgentTaskStatus {
    QUEUED,
    PLANNING,
    RUNNING,
    PAUSED,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class AgentToolId(val wireName: String) {
    READ_FILE("read_file"),
    LIST_FILES("list_files"),
    SEARCH_WORKSPACE("search_workspace"),
    FIND_FILES("find_files"),
    FILE_INFO("file_info"),
    COUNT_LINES("count_lines"),
    HASH_FILE("hash_file"),
    SEARCH_CONTENT("search_content"),
    DIRECTORY_TREE("directory_tree"),
    READ_SHARED_MEMORY("read_shared_memory"),
    WRITE_SHARED_MEMORY("write_shared_memory"),
    LIST_HANDOFFS("list_handoffs"),
    CREATE_HANDOFF("create_handoff"),
    CLAIM_HANDOFF("claim_handoff"),
    COMPLETE_HANDOFF("complete_handoff"),
    PATCH_FILE("patch_file"),
    WRITE_FILE("write_file"),
    CREATE_FILE("create_file"),
    CREATE_FOLDER("create_folder"),
    DELETE_PATH("delete_path"),
    WEB_SEARCH("web_search"),
    SCRAPE_URL("scrape_url"),
    FETCH_URL("fetch_url"),
    EXTRACT_LINKS("extract_links"),
    CALCULATE("calculate"),
    CURRENT_TIME("current_time"),
    GET_WORKSPACE_CONTEXT("get_workspace_context"),
    RETRIEVE_RELEVANT_CONTEXT("retrieve_relevant_context"),
    RUN_COMMAND("run_command"),
    GET_GIT_LOG("get_git_log"),
}

data class AgentModelBinding(
    val provider: com.mrredhood.devforge.core.ai.AIProvider,
    val modelId: String,
    val modelName: String = modelId,
)

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
    val pathScope: WorkspacePathScope = WorkspacePathScope(),
    val access: Set<AgentAccess> = AgentAccess.DEFAULT,
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
    val allowedPrefixes: List<String>,
    val affectedPaths: List<String>,
    val summary: String,
    val approvalId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

sealed interface AgentToolResult {
    data class Success(
        val summary: String,
        val output: String = "",
        val affectedPaths: List<String> = emptyList(),
        val receiptJson: String? = null,
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

data class AgentTaskPlan(
    val steps: List<AgentTaskStep>,
    val pathScope: WorkspacePathScope = WorkspacePathScope(),
    val access: Set<AgentAccess> = AgentAccess.CODING_DEFAULT,
) {
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
        val scope = JSONArray()
        plan.pathScope.canonicalPrefixes().forEach(scope::put)
        root.put("version", 3)
        root.put("scope", scope)
        root.put("access", JSONArray(plan.access.map(AgentAccess::name).sorted()))
        root.put("steps", steps)
        return root.toString()
    }

    fun decode(payload: String?): AgentTaskPlan {
        require(!payload.isNullOrBlank()) { "Agent task plan is missing." }
        val root = JSONObject(payload)
        val version = root.optInt("version", 1)
        require(version in 1..3) { "Unsupported agent task plan version." }
        val scope = if (version >= 2) {
            val scopeArray = root.optJSONArray("scope")
            if (scopeArray == null || scopeArray.length() == 0) {
                WorkspacePathScope()
            } else {
                require(scopeArray.length() <= WorkspacePathScope.MAX_PREFIXES) { "Agent path scope exceeds the prefix limit." }
                WorkspacePathScope(buildList(scopeArray.length()) {
                    for (index in 0 until scopeArray.length()) add(scopeArray.optString(index, ""))
                })
            }
        } else {
            WorkspacePathScope()
        }
        val access = if (version >= 3) {
            AgentAccess.decode(root.optJSONArray("access")?.toString(), emptySet())
        } else {
            AgentAccess.CODING_DEFAULT
        }
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
        return AgentTaskPlan(steps, scope, access)
    }

    const val MAX_TOOL_ARGUMENT_BYTES = 64 * 1024
}

data class AgentFilePatch(
    val path: String,
    val content: String,
    val summary: String = "",
    val expectedContentHash: String? = null,
)

object AgentFilePatchCodec {
    const val MAX_PATCH_CONTENT_BYTES = 128 * 1024
    const val MAX_PATCH_PATH_CHARS = 500
    const val MAX_PATCH_SUMMARY_CHARS = 500

    fun decode(argumentsJson: String): AgentFilePatch {
        val json = JSONObject(argumentsJson)
        val path = json.optString("path").trim()
        require(path.isNotBlank() && path.length <= MAX_PATCH_PATH_CHARS) { "Patch path is missing or too long." }
        val content = json.optString("content", "")
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_PATCH_CONTENT_BYTES) {
            "Patch content exceeds the 128 KiB safety limit."
        }
        val summary = json.optString("summary", "").take(MAX_PATCH_SUMMARY_CHARS)
        val expected = json.optString("expectedContentHash", "").trim().takeIf(String::isNotBlank)
        if (expected != null) require(expected.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Patch content hash is invalid." }
        return AgentFilePatch(path, content, summary, expected)
    }

    fun encode(patch: AgentFilePatch): String = JSONObject()
        .put("path", patch.path)
        .put("content", patch.content)
        .put("summary", patch.summary.take(MAX_PATCH_SUMMARY_CHARS))
        .apply { patch.expectedContentHash?.let { put("expectedContentHash", it) } }
        .toString()
}

const val MAX_AGENT_RECEIPT_BYTES = 64 * 1024
