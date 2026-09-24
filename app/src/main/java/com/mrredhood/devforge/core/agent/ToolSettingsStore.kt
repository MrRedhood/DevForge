package com.mrredhood.devforge.core.agent

import android.content.Context

data class DevForgeToolCatalogEntry(
    val id: AgentToolId,
    val title: String,
    val description: String,
    val group: String,
    val defaultEnabled: Boolean = true,
)

object DevForgeToolCatalog {
    val entries: List<DevForgeToolCatalogEntry> = listOf(
        DevForgeToolCatalogEntry(AgentToolId.READ_FILE, "Read file", "Read bounded text from the active workspace.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.LIST_FILES, "List files", "List files and folders in the active workspace.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.SEARCH_WORKSPACE, "Search workspace", "Find workspace entries by name or path.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.FIND_FILES, "Find files", "Recursively find files or folders matching a query.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.FILE_INFO, "File info", "Inspect a workspace path's type and bounded metadata.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.COUNT_LINES, "Count lines", "Count lines, characters, and bytes in a text file.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.HASH_FILE, "Hash file", "Calculate a SHA-256 hash for a text file.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.SEARCH_CONTENT, "Search content", "Search text inside workspace files and return matching lines.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.DIRECTORY_TREE, "Directory tree", "Build a bounded directory tree for the workspace.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.PATCH_FILE, "Patch file", "Replace a file with an exact, precondition-checked patch.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.WRITE_FILE, "Write file", "Write complete text content to an existing workspace path.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.CREATE_FILE, "Create file", "Create a new workspace text file.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.CREATE_FOLDER, "Create folder", "Create a new workspace folder.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.MOVE_FILE, "Move file", "Move a workspace file to another folder.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.MOVE_FOLDER, "Move folder", "Move a workspace folder and its contents to another folder.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.RENAME_FILE, "Rename file", "Rename a workspace file, including changing its extension when requested.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.RENAME_FOLDER, "Rename folder", "Rename a workspace folder.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.DELETE_PATH, "Delete path", "Delete a workspace file or folder. High impact; destructive actions still require approval.", "Workspace", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.WEB_SEARCH, "Web search", "Search the public web for current information.", "Web"),
        DevForgeToolCatalogEntry(AgentToolId.SCRAPE_URL, "Scrape URL", "Fetch a web page and extract readable text.", "Web"),
        DevForgeToolCatalogEntry(AgentToolId.FETCH_URL, "Fetch URL", "Fetch bounded raw content from an HTTP(S) URL.", "Web"),
        DevForgeToolCatalogEntry(AgentToolId.EXTRACT_LINKS, "Extract links", "Extract and normalize links from a web page.", "Web"),
        DevForgeToolCatalogEntry(AgentToolId.CALCULATE, "Calculate", "Evaluate a bounded arithmetic expression.", "Utilities"),
        DevForgeToolCatalogEntry(AgentToolId.CURRENT_TIME, "Current time", "Return device time, epoch timestamp, and requested timezone.", "Utilities"),
        DevForgeToolCatalogEntry(
            AgentToolId.RUN_COMMAND,
            "Run terminal command",
            "Run a bounded allowlisted terminal command in the active workspace and return its output. AI execution is approval-gated.",
            "Terminal",
        ),
        DevForgeToolCatalogEntry(
            AgentToolId.GET_GIT_LOG,
            "Read Git log",
            "Read bounded recent commit history from the active local Git repository or GitHub-backed workspace.",
            "Git",
        ),
    )

    val userToolIds: Set<AgentToolId> = entries.mapTo(linkedSetOf()) { it.id }
    val defaultEnabledIds: Set<AgentToolId> =
        entries.filter { it.defaultEnabled }.mapTo(linkedSetOf()) { it.id }

    fun entry(id: AgentToolId): DevForgeToolCatalogEntry? = entries.firstOrNull { it.id == id }

    fun isUserTool(id: AgentToolId): Boolean = id in userToolIds
}

class ToolSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun isEnabled(toolId: AgentToolId): Boolean {
        if (!DevForgeToolCatalog.isUserTool(toolId)) return true
        val key = "enabled_" + toolId.wireName
        return if (prefs.contains(key)) prefs.getBoolean(key, true)
        else toolId in DevForgeToolCatalog.defaultEnabledIds
    }

    fun setEnabled(toolId: AgentToolId, enabled: Boolean) {
        if (!DevForgeToolCatalog.isUserTool(toolId)) return
        prefs.edit().putBoolean("enabled_" + toolId.wireName, enabled).apply()
    }

    fun enabledToolIds(): Set<AgentToolId> =
        DevForgeToolCatalog.userToolIds.filterTo(linkedSetOf()) { isEnabled(it) }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    init {
        migrateMainAiCodingDefaults()
    }

    /**
     * Versioned defaults for existing installs.
     *
     * Workspace inspection/mutation remains enabled by default, but deletion is
     * intentionally opt-in because it is destructive even though the execution
     * path remains approval-protected.
     */
    private fun migrateMainAiCodingDefaults() {
        val current = prefs.getInt(TOOL_DEFAULTS_VERSION_KEY, 0)
        if (current >= TOOL_DEFAULTS_VERSION) return
        prefs.edit().apply {
            if (current < 1) {
                DevForgeToolCatalog.entries
                    .filter { it.group == "Workspace" }
                    .forEach { putBoolean("enabled_" + it.id.wireName, true) }
            }
            if (current < 2) {
                putBoolean("enabled_" + AgentToolId.DELETE_PATH.wireName, false)
            }
            putInt(TOOL_DEFAULTS_VERSION_KEY, TOOL_DEFAULTS_VERSION)
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "devforge_tool_settings"
        private const val TOOL_DEFAULTS_VERSION_KEY = "tool_defaults_version"
        private const val TOOL_DEFAULTS_VERSION = 2
    }
}
