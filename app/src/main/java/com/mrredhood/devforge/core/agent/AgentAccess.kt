package com.mrredhood.devforge.core.agent

import org.json.JSONArray
import org.json.JSONObject

enum class AgentAccess(
    val label: String,
    val description: String,
) {
    WORKSPACE_ACCESS("Workspace", "Inspect the selected workspace through SAF."),
    FILE_ACCESS("Files", "Read and modify individual workspace files."),
    COORDINATION_ACCESS("Agent coordination", "Use shared memory and handoffs between agents.");

    companion object {
        val DEFAULT: Set<AgentAccess> = setOf(
            WORKSPACE_ACCESS,
            FILE_ACCESS,
        )

        val CODING_DEFAULT: Set<AgentAccess> = setOf(
            WORKSPACE_ACCESS,
            FILE_ACCESS,
            COORDINATION_ACCESS,
        )

        fun encode(access: Set<AgentAccess>): String =
            JSONArray(access.map(AgentAccess::name).sorted()).toString()

        fun decode(value: String?, fallback: Set<AgentAccess> = emptySet()): Set<AgentAccess> {
            val raw = value ?: return fallback
            val array = runCatching { JSONArray(raw) }.getOrElse { return fallback }
            val decoded = LinkedHashSet<AgentAccess>(array.length())
            for (index in 0 until array.length()) {
                val name = array.optString(index).trim()
                val access = runCatching { AgentAccess.valueOf(name) }.getOrNull()
                    ?: return fallback
                decoded += access
            }
            return decoded
        }
    }
}

object AgentAccessRules {
    fun requiredFor(toolId: AgentToolId): Set<AgentAccess> = when (toolId) {
        AgentToolId.READ_FILE,
        AgentToolId.LIST_FILES,
        AgentToolId.SEARCH_WORKSPACE,
        AgentToolId.PATCH_FILE,
        AgentToolId.WRITE_FILE -> setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS)

        AgentToolId.READ_SHARED_MEMORY,
        AgentToolId.WRITE_SHARED_MEMORY,
        AgentToolId.LIST_HANDOFFS,
        AgentToolId.CREATE_HANDOFF,
        AgentToolId.CLAIM_HANDOFF,
        AgentToolId.COMPLETE_HANDOFF -> setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.COORDINATION_ACCESS)
    }

    fun canUse(toolId: AgentToolId, access: Set<AgentAccess>): Boolean =
        requiredFor(toolId).all(access::contains)
}
