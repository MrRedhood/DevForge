package com.mrredhood.devforge.core.agent

import org.json.JSONArray
import org.json.JSONObject

enum class AgentAccess(
    val label: String,
    val description: String,
) {
    WORKSPACE_ACCESS("Workspace", "Inspect the selected workspace through SAF."),
    FILE_ACCESS("Files", "Read and modify individual workspace files."),
    COORDINATION_ACCESS("Agent coordination", "Use shared memory and handoffs between agents."),
    WEB_ACCESS("Web", "Search and fetch public web content through bounded HTTP tools."),
    GIT_ACCESS("Git", "Read repository history and Git metadata."),
    TERMINAL_ACCESS("Terminal", "Run bounded commands in the selected workspace.");

    companion object {
        val DEFAULT: Set<AgentAccess> = setOf(
            WORKSPACE_ACCESS,
            FILE_ACCESS,
            WEB_ACCESS,
        )

        val CODING_DEFAULT: Set<AgentAccess> = setOf(
            WORKSPACE_ACCESS,
            FILE_ACCESS,
            COORDINATION_ACCESS,
            WEB_ACCESS,
            GIT_ACCESS,
            TERMINAL_ACCESS,
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
        AgentToolId.FIND_FILES,
        AgentToolId.FILE_INFO,
        AgentToolId.COUNT_LINES,
        AgentToolId.HASH_FILE,
        AgentToolId.SEARCH_CONTENT,
        AgentToolId.DIRECTORY_TREE,
        AgentToolId.PATCH_FILE,
        AgentToolId.WRITE_FILE,
        AgentToolId.CREATE_FILE,
        AgentToolId.CREATE_FOLDER,
        AgentToolId.MOVE_FILE,
        AgentToolId.MOVE_FOLDER,
        AgentToolId.RENAME_FILE,
        AgentToolId.RENAME_FOLDER,
        AgentToolId.DELETE_PATH -> setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS)

        AgentToolId.READ_SHARED_MEMORY,
        AgentToolId.WRITE_SHARED_MEMORY,
        AgentToolId.LIST_HANDOFFS,
        AgentToolId.CREATE_HANDOFF,
        AgentToolId.CLAIM_HANDOFF,
        AgentToolId.COMPLETE_HANDOFF -> setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.COORDINATION_ACCESS)

        AgentToolId.WEB_SEARCH,
        AgentToolId.SCRAPE_URL,
        AgentToolId.FETCH_URL,
        AgentToolId.EXTRACT_LINKS -> setOf(AgentAccess.WEB_ACCESS)

        AgentToolId.CALCULATE,
        AgentToolId.CURRENT_TIME -> emptySet()

        AgentToolId.GET_WORKSPACE_CONTEXT,
        AgentToolId.RETRIEVE_RELEVANT_CONTEXT -> setOf(AgentAccess.WORKSPACE_ACCESS)

        AgentToolId.GET_GIT_LOG -> setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.GIT_ACCESS)

        AgentToolId.RUN_COMMAND -> setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.TERMINAL_ACCESS)
    }

    fun canUse(toolId: AgentToolId, access: Set<AgentAccess>): Boolean =
        requiredFor(toolId).all(access::contains)
}
