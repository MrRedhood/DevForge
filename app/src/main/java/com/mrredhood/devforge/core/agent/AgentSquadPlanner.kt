package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.ai.AIChatGateway
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.AISettingsRepository
import com.mrredhood.devforge.core.workspace.WorkspaceContextService
import org.json.JSONObject

/**
 * AI-only top-level planner for workspace work.
 *
 * It decides how to split a user goal into ordered phases. Agents inside a phase may
 * execute concurrently; phases are started one after another so the overall plan remains
 * deterministic and easy to inspect.
 */
data class AgentSquadMember(
    val phase: Int,
    val title: String,
    val instruction: String,
)

data class AgentSquadPlan(
    val members: List<AgentSquadMember>,
) {
    val phases: List<List<AgentSquadMember>>
        get() = members.groupBy { it.phase }.toSortedMap().values.toList()
}

class AgentSquadPlanner(context: Context) {
    private val appContext = context.applicationContext
    private val settings = AISettingsRepository(appContext)
    private val gateway = AIChatGateway()
    private val workspaceContext = WorkspaceContextService(appContext)

    suspend fun plan(
        model: AIModelInfo,
        workspaceId: String,
        goal: String,
        customBaseUrl: String?,
    ): AgentSquadPlan {
        val key = settings.getApiKey(model.provider)
            ?: throw IllegalStateException("No usable API key is available for ${model.provider.displayName}.")

        val context = workspaceContext.compactPrompt(workspaceId).take(MAX_CONTEXT_CHARS)
        val prompt = buildString {
            appendLine("You are DevForge's top-level engineering planner.")
            appendLine("Produce ONLY valid JSON. No Markdown or code fences.")
            appendLine("Schema:")
            appendLine("{\"agents\":[{\"phase\":1,\"title\":\"short role/task\",\"instruction\":\"concrete engineering goal\"}]}")
            appendLine("Rules:")
            appendLine("- Return 1 to 4 agents.")
            appendLine("- Phases are positive integers starting at 1; later phases start only after earlier phases finish.")
            appendLine("- Use multiple agents in the same phase only when their work can be performed independently without conflicting file ownership.")
            appendLine("- Prefer one agent for a small or tightly coupled change.")
            appendLine("- Every instruction must tell the agent to inspect exact files/folders first, then implement, then verify.")
            appendLine("- Never ask an agent to wait for another agent in its own instruction.")
            appendLine("- Do not invent paths; agents must discover them.")
            appendLine("- The downstream agent planner will produce the detailed search/edit/verify tool plan.")
            appendLine()
            appendLine("User goal:")
            appendLine(goal.take(MAX_GOAL_CHARS))
            appendLine()
            appendLine("Current workspace context:")
            appendLine(context.ifBlank { "(no compact context available)" })
        }

        val response = gateway.send(
            model = model,
            apiKey = key,
            history = emptyList(),
            instruction = prompt,
            attachments = emptyList(),
            customBaseUrl = customBaseUrl,
        )
        return parse(response, goal)
    }

    internal fun parse(response: String, fallbackGoal: String): AgentSquadPlan {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return fallback(fallbackGoal)
        val root = runCatching { JSONObject(response.substring(start, end + 1)) }.getOrNull()
            ?: return fallback(fallbackGoal)
        val array = root.optJSONArray("agents") ?: return fallback(fallbackGoal)
        val members = buildList {
            for (index in 0 until minOf(array.length(), MAX_AGENTS)) {
                val item = array.optJSONObject(index) ?: continue
                val phase = item.optInt("phase", index + 1).coerceIn(1, MAX_PHASES)
                val title = item.optString("title").trim().take(MAX_TITLE_CHARS)
                val instruction = item.optString("instruction").trim().take(MAX_INSTRUCTION_CHARS)
                if (title.isNotBlank() && instruction.isNotBlank()) {
                    add(AgentSquadMember(phase, title, instruction))
                }
            }
        }
        return if (members.isEmpty()) fallback(fallbackGoal)
        else AgentSquadPlan(members.sortedWith(compareBy<AgentSquadMember> { it.phase }.thenBy { it.title }))
    }

    private fun fallback(goal: String): AgentSquadPlan =
        AgentSquadPlan(
            listOf(
                AgentSquadMember(
                    phase = 1,
                    title = "Implementation",
                    instruction = "Inspect the workspace, find the exact files and folders affected by this request, implement the change, and verify the resulting state. User goal: ${goal.take(MAX_INSTRUCTION_CHARS)}",
                ),
            ),
        )

    companion object {
        private const val MAX_AGENTS = 4
        private const val MAX_PHASES = 4
        private const val MAX_GOAL_CHARS = 12_000
        private const val MAX_INSTRUCTION_CHARS = 4_000
        private const val MAX_TITLE_CHARS = 120
        private const val MAX_CONTEXT_CHARS = 12_000
    }
}
