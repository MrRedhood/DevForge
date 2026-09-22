package com.mrredhood.devforge.core.settings

import android.content.Context

enum class ThemeMode { SYSTEM, DARK, LIGHT, OBSIDIAN, NORD, OCEAN, FOREST, AMETHYST, SUNSET, CYBER, DRACULA, MONOKAI, SOLARIZED }
enum class DensityMode { COMFORTABLE, COMPACT }
enum class EditorFontSize(val sp: Int) { SMALL(13), MEDIUM(15), LARGE(18) }
enum class AiRoutingMode { FIXED, BALANCED, LOW_COST, QUALITY }

data class DevForgeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val densityMode: DensityMode = DensityMode.COMFORTABLE,
    val editorFontSize: EditorFontSize = EditorFontSize.MEDIUM,
    val wordWrap: Boolean = true,
    val showInvisibles: Boolean = false,
    val autoSaveEnabled: Boolean = false,
    val autoSaveIntervalMs: Long = 5_000L,
    val aiRoutingMode: AiRoutingMode = AiRoutingMode.FIXED,
    val githubOwner: String = "",
    val githubRepository: String = "",
    val githubBranch: String = "main",
    val githubWorkflow: String = ".github/workflows/android.yml",
    val buildPollSeconds: Long = 5L,
    val terminalTimeoutMs: Long = 10_000L,
    val automationEventIntervalMinutes: Long = 15L,
    val auditRetentionDays: Long = 30L,
    val chatRetentionDays: Long = 90L,
)

class DevForgeSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun snapshot(): DevForgeSettings = DevForgeSettings(
        themeMode = enumValue(KEY_THEME, ThemeMode.SYSTEM),
        densityMode = enumValue(KEY_DENSITY, DensityMode.COMFORTABLE),
        editorFontSize = enumValue(KEY_EDITOR_FONT, EditorFontSize.MEDIUM),
        wordWrap = prefs.getBoolean(KEY_WORD_WRAP, true),
        showInvisibles = prefs.getBoolean(KEY_INVISIBLES, false),
        autoSaveEnabled = prefs.getBoolean(KEY_AUTOSAVE_ENABLED, false),
        autoSaveIntervalMs = prefs.getLong(KEY_AUTOSAVE_INTERVAL, 5_000L).coerceIn(1_000L, 10_000_000_000L),
        aiRoutingMode = enumValue(KEY_AI_ROUTING, AiRoutingMode.FIXED),
        githubOwner = prefs.getString(KEY_GH_OWNER, "").orEmpty(),
        githubRepository = prefs.getString(KEY_GH_REPO, "").orEmpty(),
        githubBranch = prefs.getString(KEY_GH_BRANCH, "main").orEmpty().ifBlank { "main" },
        githubWorkflow = prefs.getString(KEY_GH_WORKFLOW, ".github/workflows/android.yml").orEmpty(),
        buildPollSeconds = prefs.getLong(KEY_BUILD_POLL, 5L).coerceIn(2L, 60L),
        terminalTimeoutMs = prefs.getLong(KEY_TERMINAL_TIMEOUT, 10_000L).coerceIn(250L, 15_000L),
        automationEventIntervalMinutes = prefs.getLong(KEY_AUTOMATION_INTERVAL, 15L).coerceIn(15L, 7L * 24L * 60L),
        auditRetentionDays = prefs.getLong(KEY_AUDIT_RETENTION, 30L).coerceIn(1L, 365L),
        chatRetentionDays = prefs.getLong(KEY_CHAT_RETENTION, 90L).coerceIn(1L, 3650L),
    )

    fun update(transform: (DevForgeSettings) -> DevForgeSettings) {
        val value = transform(snapshot())
        prefs.edit()
            .putString(KEY_THEME, value.themeMode.name)
            .putString(KEY_DENSITY, value.densityMode.name)
            .putString(KEY_EDITOR_FONT, value.editorFontSize.name)
            .putBoolean(KEY_WORD_WRAP, value.wordWrap)
            .putBoolean(KEY_INVISIBLES, value.showInvisibles)
            .putBoolean(KEY_AUTOSAVE_ENABLED, value.autoSaveEnabled)
            .putLong(KEY_AUTOSAVE_INTERVAL, value.autoSaveIntervalMs.coerceIn(1_000L, 10_000_000_000L))
            .putString(KEY_AI_ROUTING, value.aiRoutingMode.name)
            .putString(KEY_GH_OWNER, value.githubOwner.trim().take(200))
            .putString(KEY_GH_REPO, value.githubRepository.trim().take(200))
            .putString(KEY_GH_BRANCH, value.githubBranch.trim().ifBlank { "main" }.take(200))
            .putString(KEY_GH_WORKFLOW, value.githubWorkflow.trim().take(300))
            .putLong(KEY_BUILD_POLL, value.buildPollSeconds.coerceIn(2L, 60L))
            .putLong(KEY_TERMINAL_TIMEOUT, value.terminalTimeoutMs.coerceIn(250L, 15_000L))
            .putLong(KEY_AUTOMATION_INTERVAL, value.automationEventIntervalMinutes.coerceIn(15L, 7L * 24L * 60L))
            .putLong(KEY_AUDIT_RETENTION, value.auditRetentionDays.coerceIn(1L, 365L))
            .putLong(KEY_CHAT_RETENTION, value.chatRetentionDays.coerceIn(1L, 3650L))
            .apply()
    }

    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }
    fun setDensityMode(value: DensityMode) = update { it.copy(densityMode = value) }
    fun setEditorFontSize(value: EditorFontSize) = update { it.copy(editorFontSize = value) }
    fun setWordWrap(value: Boolean) = update { it.copy(wordWrap = value) }
    fun setShowInvisibles(value: Boolean) = update { it.copy(showInvisibles = value) }
    fun setAutoSaveEnabled(value: Boolean) = update { it.copy(autoSaveEnabled = value) }
    fun setAutoSaveIntervalMs(value: Long) = update { it.copy(autoSaveIntervalMs = value) }
    fun setAiRoutingMode(value: AiRoutingMode) = update { it.copy(aiRoutingMode = value) }

    fun setGithub(owner: String, repository: String, branch: String, workflow: String) =
        update { it.copy(githubOwner = owner, githubRepository = repository, githubBranch = branch, githubWorkflow = workflow) }

    fun setBuildPollSeconds(value: Long) = update { it.copy(buildPollSeconds = value) }
    fun setTerminalTimeoutMs(value: Long) = update { it.copy(terminalTimeoutMs = value) }
    fun setAutomationEventIntervalMinutes(value: Long) = update { it.copy(automationEventIntervalMinutes = value) }
    fun setAuditRetentionDays(value: Long) = update { it.copy(auditRetentionDays = value) }
    fun setChatRetentionDays(value: Long) = update { it.copy(chatRetentionDays = value) }

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, null).orEmpty()) }.getOrDefault(fallback)

    private companion object {
        const val PREFERENCES = "devforge_settings"
        const val KEY_THEME = "theme"
        const val KEY_DENSITY = "density"
        const val KEY_EDITOR_FONT = "editor_font"
        const val KEY_WORD_WRAP = "word_wrap"
        const val KEY_INVISIBLES = "show_invisibles"
        const val KEY_AUTOSAVE_ENABLED = "autosave_enabled"
        const val KEY_AUTOSAVE_INTERVAL = "autosave_interval_ms"
        const val KEY_AI_ROUTING = "ai_routing"
        const val KEY_GH_OWNER = "github_owner"
        const val KEY_GH_REPO = "github_repository"
        const val KEY_GH_BRANCH = "github_branch"
        const val KEY_GH_WORKFLOW = "github_workflow"
        const val KEY_BUILD_POLL = "build_poll_seconds"
        const val KEY_TERMINAL_TIMEOUT = "terminal_timeout_ms"
        const val KEY_AUTOMATION_INTERVAL = "automation_interval_minutes"
        const val KEY_AUDIT_RETENTION = "audit_retention_days"
        const val KEY_CHAT_RETENTION = "chat_retention_days"
    }
}
