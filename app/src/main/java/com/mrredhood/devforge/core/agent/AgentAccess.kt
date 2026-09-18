package com.mrredhood.devforge.core.agent

import org.json.JSONArray
import org.json.JSONObject

enum class AgentAccess(
    val label: String,
    val description: String,
) {
    WEB_ACCESS("Web", "Use registered web-search tools when available."),
    FILE_ACCESS("Files", "Read and modify individual workspace files."),
    WORKSPACE_ACCESS("Workspace", "Inspect and operate inside the selected workspace."),
    TERMINAL_ACCESS("Terminal", "Use registered terminal tools when available."),
    GIT_ACCESS("Git", "Use registered Git tools when available."),
    BUILD_ACCESS("Build", "Use registered build tools when available."),
    DIAGNOSTICS_ACCESS("Diagnostics", "Read build/test/diagnostic information when available."),
    COORDINATION_ACCESS("Agent coordination", "Use shared memory and handoffs between agents."),
    NETWORK_ACCESS("Network", "Use registered non-web network tools when available.");

    companion object {
        val DEFAULT: Set<AgentAccess> = setOf(
            WORKSPACE_ACCESS,
            FILE_ACCESS,
        )

        val CODING_DEFAULT: Set<AgentAccess> = setOf(
            WORKSPACE_ACCESS,
            FILE_ACCESS,
            COORDINATION_ACCESS,
            DIAGNOSTICS_ACCESS,
        )

        fun encode(access: Set<AgentAccess>): String =
            JSONArray(access.map(AgentAccess::name).sorted()).toString()

        fun decode(value: String?): Set<AgentAccess> {
            val array = runCatching { JSONArray(value ?: "[]") }.getOrElse { return DEFAULT }
            return buildSet {
                for (index in 0 until array.length()) {
                    runCatching { AgentAccess.valueOf(array.optString(index)) }
                        .getOrNull()
                        ?.let(::add)
                }
            }.ifEmpty { DEFAULT }
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
