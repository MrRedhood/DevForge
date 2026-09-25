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
        DevForgeToolCatalogEntry(AgentToolId.READ_SHARED_MEMORY, "Read shared memory", "Read shared AI coordination memory.", "Agent coordination"),
        DevForgeToolCatalogEntry(AgentToolId.WRITE_SHARED_MEMORY, "Write shared memory", "Write bounded AI coordination memory.", "Agent coordination"),
        DevForgeToolCatalogEntry(AgentToolId.LIST_HANDOFFS, "List handoffs", "List pending agent handoffs.", "Agent coordination"),
        DevForgeToolCatalogEntry(AgentToolId.CREATE_HANDOFF, "Create handoff", "Create a bounded agent handoff.", "Agent coordination"),
        DevForgeToolCatalogEntry(AgentToolId.CLAIM_HANDOFF, "Claim handoff", "Claim a pending agent handoff.", "Agent coordination"),
        DevForgeToolCatalogEntry(AgentToolId.COMPLETE_HANDOFF, "Complete handoff", "Complete an agent handoff.", "Agent coordination"),
        DevForgeToolCatalogEntry(AgentToolId.GET_WORKSPACE_CONTEXT, "Workspace context", "Read authoritative active-workspace identity and state.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.RETRIEVE_RELEVANT_CONTEXT, "Relevant context", "Retrieve bounded source context relevant to the current task.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.RUN_COMMAND, "Run terminal command", "Run a bounded Android/Linux shell command in the active workspace.", "Terminal"),
        DevForgeToolCatalogEntry(AgentToolId.GET_GIT_LOG, "Read Git log", "Read bounded recent Git commit history.", "Git"),
        DevForgeToolCatalogEntry(AgentToolId.READ_FILE, "Read file", "Read bounded text from the active workspace.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.LIST_FILES, "List files", "List files and folders in the active workspace.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.SEARCH_WORKSPACE, "Search workspace", "Find workspace entries by name or path.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.FIND_FILES, "Find files", "Recursively find files or folders matching a query.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.FILE_INFO, "File info", "Inspect a workspace path's type and bounded metadata.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.COUNT_LINES, "Count lines", "Count lines, characters, and bytes in a text file.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.HASH_FILE, "Hash file", "Calculate a SHA-256 hash for a text file.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.FILE_EXISTS, "File exists", "Check whether a workspace path exists and report its type.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.DIRECTORY_INFO, "Directory info", "Inspect a folder's item count and bounded metadata.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.COMPARE_FILES, "Compare files", "Compare two workspace text files and report the first differences.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.DETECT_PROJECT_TYPE, "Detect project type", "Detect common Android, Gradle, Node, Python, Rust and Go project markers.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.GET_OS_INFO, "OS info", "Read bounded Android/Linux runtime identity without exposing secrets.", "Environment"),
        DevForgeToolCatalogEntry(AgentToolId.GET_RUNTIME_INFO, "Runtime info", "Inspect bounded JVM/Android memory and runtime information.", "Environment"),
        DevForgeToolCatalogEntry(AgentToolId.GET_STORAGE_INFO, "Storage info", "Inspect bounded DevForge app storage capacity and availability.", "Environment"),
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
            "Run a bounded Linux shell command in the active workspace and return its output. Normal shell operators are supported for local workspaces. AI execution is approval-gated.",
            "Terminal",
        ),
        DevForgeToolCatalogEntry(
            AgentToolId.GET_GIT_LOG,
            "Read Git log",
            "Read bounded recent commit history from the active local Git repository or GitHub-backed workspace.",
            "Git",
        ),
        DevForgeToolCatalogEntry(AgentToolId.GET_GIT_BRANCH, "Git branch", "Inspect the active Git branch, detached-head state and revision.", "Git"),
        DevForgeToolCatalogEntry(AgentToolId.GET_GIT_REMOTES, "Git remotes", "Inspect whether a repository has a configured remote without exposing credentials.", "Git"),
        DevForgeToolCatalogEntry(AgentToolId.COPY_FILE, "Copy file", "Copy a workspace file to another folder.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.COPY_FOLDER, "Copy folder", "Copy a workspace folder recursively.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.REPLACE_TEXT, "Replace text", "Replace an exact text occurrence in a workspace file.", "Code editing"),
        DevForgeToolCatalogEntry(AgentToolId.INSERT_TEXT, "Insert text", "Insert text at a line or character location.", "Code editing"),
        DevForgeToolCatalogEntry(AgentToolId.DELETE_TEXT, "Delete text", "Delete an exact text range from a workspace file.", "Code editing"),
        DevForgeToolCatalogEntry(AgentToolId.REPLACE_RANGE, "Replace range", "Replace a bounded line range in a workspace file.", "Code editing"),
        DevForgeToolCatalogEntry(AgentToolId.FORMAT_FILE, "Format file", "Apply safe whitespace/import formatting to a text file.", "Code editing"),
        DevForgeToolCatalogEntry(AgentToolId.ORGANIZE_IMPORTS, "Organize imports", "Deduplicate and sort import lines in a source file.", "Code editing"),
        DevForgeToolCatalogEntry(AgentToolId.FIND_SYMBOL, "Find symbol", "Find declarations matching a symbol name.", "Code intelligence"),
        DevForgeToolCatalogEntry(AgentToolId.FIND_REFERENCES, "Find references", "Find text references to a symbol across the workspace.", "Code intelligence"),
        DevForgeToolCatalogEntry(AgentToolId.ANALYZE_WORKSPACE, "Analyze workspace", "Summarize workspace type, structure, configs and current state.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.GET_PROJECT_INFO, "Project info", "Identify likely language, framework, build system and key files.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.GET_PROJECT_STRUCTURE, "Project structure", "Return a bounded project tree with important files.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.FIND_ENTRY_POINTS, "Find entry points", "Find common application and executable entry points.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.FIND_CONFIG_FILES, "Find config files", "Find build, package and configuration files.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.GET_DEPENDENCIES, "Get dependencies", "Extract dependency declarations from common build files.", "Workspace"),
        DevForgeToolCatalogEntry(AgentToolId.GET_BUILD_TARGETS, "Get build targets", "Inspect available DevForge/GitHub build targets.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.BUILD_PROJECT, "Build project", "Build the active project through the configured DevForge build workflow.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.CLEAN_PROJECT, "Clean project", "Run a bounded clean/build-clean workflow when configured.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.RUN_TESTS, "Run tests", "Run the configured project test workflow.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.RUN_TEST, "Run test", "Run a specific test through the configured build workflow.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.RUN_LINT, "Run lint", "Run the configured lint workflow.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.INSPECT_BUILD_ERROR, "Inspect build error", "Extract structured causes from the latest build logs.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.GET_BUILD_OUTPUT, "Get build output", "Read current/live output and logs from the active or latest GitHub Actions build run; repository and run ID are inferred when omitted.", "Build"),
        DevForgeToolCatalogEntry(AgentToolId.RUN_BACKGROUND_COMMAND, "Run background command", "Start a bounded terminal command without blocking the AI turn.", "Terminal"),
        DevForgeToolCatalogEntry(AgentToolId.GET_PROCESS_STATUS, "Get process status", "Inspect a background AI terminal process.", "Terminal"),
        DevForgeToolCatalogEntry(AgentToolId.STOP_PROCESS, "Stop process", "Stop a background AI terminal process.", "Terminal", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.LIST_PROCESSES, "List processes", "List DevForge-managed AI terminal processes.", "Terminal"),
        DevForgeToolCatalogEntry(AgentToolId.READ_PROCESS_OUTPUT, "Read process output", "Read buffered output from a DevForge-managed background process.", "Terminal"),
        DevForgeToolCatalogEntry(AgentToolId.GIT_STATUS, "Git status", "Inspect bounded Git worktree/index state.", "Git"),
        DevForgeToolCatalogEntry(AgentToolId.GIT_DIFF, "Git diff", "Inspect bounded workspace Git differences.", "Git"),
        DevForgeToolCatalogEntry(AgentToolId.GIT_DIFF_FILE, "Git diff file", "Inspect Git differences for one file.", "Git"),
        DevForgeToolCatalogEntry(AgentToolId.GIT_ADD, "Git add", "Stage selected workspace paths when supported.", "Git", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GIT_RESTORE, "Git restore", "Restore selected paths to the last verified state.", "Git", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GIT_COMMIT, "Git commit", "Create a local Git commit for selected changes.", "Git", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GIT_SHOW_COMMIT, "Git show commit", "Inspect a commit and its changed files.", "Git"),
        DevForgeToolCatalogEntry(AgentToolId.GIT_CREATE_TAG, "Git create tag", "Create a Git tag at the current verified revision.", "Git", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GIT_STASH, "Git stash", "Temporarily stash local changes.", "Git", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GIT_RESET, "Git reset", "Reset selected Git state; destructive and approval protected.", "Git", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_CREATE_BRANCH, "GitHub create branch", "Create a remote branch when explicitly requested.", "GitHub", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_CREATE_PR, "GitHub create PR", "Create a pull request from the configured repository state.", "GitHub", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_CREATE_ISSUE, "GitHub create issue", "Open a GitHub issue.", "GitHub", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_COMMENT_ISSUE, "GitHub comment issue", "Add a comment to a GitHub issue.", "GitHub", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_LIST_ISSUES, "GitHub list issues", "List open GitHub issues.", "GitHub"),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_GET_ACTIONS, "GitHub actions", "Inspect recent GitHub Actions workflow runs.", "GitHub"),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_GET_WORKFLOW_LOGS, "GitHub workflow logs", "Fetch bounded logs for a GitHub Actions run.", "GitHub"),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_DISPATCH_WORKFLOW, "GitHub dispatch workflow", "Start a GitHub Actions workflow.", "GitHub", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GITHUB_GET_ARTIFACT, "GitHub artifact", "List or download a GitHub Actions artifact.", "GitHub"),
        DevForgeToolCatalogEntry(AgentToolId.ANDROID_LOGCAT, "Android logcat", "Read bounded Android Logcat output from the device.", "Android"),
        DevForgeToolCatalogEntry(AgentToolId.ANDROID_BUILD_APK, "Android build APK", "Dispatch the configured Android APK build.", "Android", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.ANDROID_BUILD_AAB, "Android build AAB", "Dispatch the configured Android AAB build.", "Android", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.ANDROID_INSTALL_APK, "Android install APK", "Request APK installation for a generated artifact.", "Android", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.ANDROID_LAUNCH_APP, "Android launch app", "Launch the selected Android package when the device bridge is available.", "Android", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.ANDROID_GET_DEVICE_INFO, "Android device info", "Return Android OS/device information.", "Android"),
        DevForgeToolCatalogEntry(AgentToolId.CREATE_CHECKPOINT, "Create checkpoint", "Snapshot bounded workspace files before risky AI work.", "Recovery"),
        DevForgeToolCatalogEntry(AgentToolId.RESTORE_CHECKPOINT, "Restore checkpoint", "Restore a previous DevForge AI checkpoint.", "Recovery", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.UNDO_AI_CHANGE, "Undo AI change", "Revert the latest checkpoint-backed AI change set.", "Recovery", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.COMPARE_WORKSPACE, "Compare workspace", "Compare current workspace content against a checkpoint.", "Recovery"),
        DevForgeToolCatalogEntry(AgentToolId.SCRAP_VIDEO, "Scrap video", "Extract bounded video metadata and a key-frame sample.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.SCRAP_AUDIO, "Scrap audio", "Extract bounded audio metadata and embedded artwork metadata.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.MEDIA_INFO, "Media info", "Inspect media duration, MIME, dimensions, rotation and tags.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.MEDIA_DURATION, "Media duration", "Return media duration in milliseconds.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.MEDIA_MIME, "Media MIME", "Return the workspace media MIME type.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.MEDIA_COVER_ART, "Media cover art", "Extract bounded embedded artwork as base64.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.EXTRACT_VIDEO_FRAME, "Extract video frame", "Extract a bounded key video frame as base64.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.EXTRACT_AUDIO_METADATA, "Audio metadata", "Extract bounded audio metadata.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.MEDIA_CHECKSUM, "Media checksum", "Calculate a bounded SHA-256 checksum for media.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.LIST_MEDIA_STREAMS, "Media streams", "Summarize detected media stream metadata.", "Media", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.READ_BYTES, "Read bytes", "Read bounded raw bytes as hexadecimal.", "Workspace", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GET_FILE_METADATA, "Detailed file metadata", "Inspect detailed SAF metadata for a workspace path.", "Workspace", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.LIST_DIRECTORY_DETAILED, "Detailed directory listing", "List entries with type, size and modified time.", "Workspace", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.SORT_DIRECTORY, "Sort directory", "List entries ordered by name, size or modification time.", "Workspace", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.FIND_LARGEST_FILES, "Find largest files", "Find largest files in a bounded recursive scan.", "Workspace", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.FIND_DUPLICATES, "Find duplicates", "Find bounded duplicates using size and SHA-256.", "Workspace", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GET_TEXT_STATS, "Text statistics", "Return bounded line, word, character and byte counts.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.DETECT_ENCODING, "Detect encoding", "Detect BOMs and validate UTF-8.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.DETECT_BINARY_FILE, "Detect binary file", "Classify a file using binary heuristics.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.PREVIEW_FILE, "Preview file", "Return a bounded text preview.", "Workspace", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.SEARCH_REGEX, "Regex search", "Search source text using a bounded regex.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.FIND_TODO, "Find TODOs", "Find TODO markers in source files.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.FIND_FIXME, "Find FIXMEs", "Find FIXME markers in source files.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.FIND_COMMENTS, "Find comments", "Find bounded source comment lines.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.COUNT_SYMBOLS, "Count symbols", "Estimate common declaration counts.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.FIND_TEST_FILES, "Find test files", "Find common test sources.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.FIND_SOURCE_FILES, "Find source files", "Find common source-code files.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GET_LANGUAGE_BREAKDOWN, "Language breakdown", "Summarize files by language extension.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.DETECT_FORMATTER, "Detect formatter", "Detect formatter/editor configuration files.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.DETECT_LINTER, "Detect linter", "Detect linter/static-analysis configuration files.", "Code intelligence", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.WHICH_COMMAND, "Which command", "Find a command in the Android PATH.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.COMMAND_VERSION, "Command version", "Run a bounded --version query.", "Environment", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.SHELL_INFO, "Shell info", "Describe Android shell/kernel environment.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.ENVIRONMENT_VARS, "Environment variables", "Return a bounded non-secret environment snapshot.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.SYSTEM_PROPERTIES, "System properties", "Return safe Android build/device properties.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.CPU_INFO, "CPU info", "Return CPU architecture and core information.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.MEMORY_INFO, "Memory info", "Return JVM and Android memory information.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.STORAGE_CAPACITY, "Storage capacity", "Return DevForge storage capacity.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.NETWORK_INFO, "Network info", "Return current Android network capabilities.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.PROCESS_SNAPSHOT, "Process snapshot", "Return a bounded Android process snapshot.", "Environment", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GRADLE_TASKS, "Gradle tasks", "Run a bounded Gradle task listing.", "Build", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.GRADLE_DEPENDENCIES, "Gradle dependencies", "Run a bounded Gradle dependency report.", "Build", defaultEnabled = false),
        DevForgeToolCatalogEntry(AgentToolId.PROJECT_MODULES, "Project modules", "Discover Gradle/module declarations.", "Build", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.BUILD_FILES, "Build files", "Find common build/configuration files.", "Build", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.PACKAGE_NAME, "Package name", "Detect an Android package/application ID.", "Build", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GIT_HEAD, "Git HEAD", "Read repository branch and HEAD revision.", "Git", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GIT_BRANCHES, "Git branches", "List bounded local Git branches.", "Git", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GIT_TAGS, "Git tags", "List bounded local Git tags.", "Git", defaultEnabled = true),
        DevForgeToolCatalogEntry(AgentToolId.GIT_REMOTE_INFO, "Git remote info", "Inspect Git remotes without exposing credentials.", "Git", defaultEnabled = true),
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
