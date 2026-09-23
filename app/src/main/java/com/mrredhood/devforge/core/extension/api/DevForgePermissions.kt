package com.mrredhood.devforge.core.extension.api

enum class DevForgePermission(val wireName: String, val risk: DevForgePermissionRisk) {
    UI_CONTRIBUTE("ui.contribute", DevForgePermissionRisk.LOW),
    COMMANDS_REGISTER("commands.register", DevForgePermissionRisk.LOW),
    SETTINGS_READ("settings.read", DevForgePermissionRisk.LOW),
    SETTINGS_WRITE("settings.write", DevForgePermissionRisk.MEDIUM),
    NOTIFICATIONS("notifications", DevForgePermissionRisk.LOW),
    WORKSPACE_READ("workspace.read", DevForgePermissionRisk.LOW),
    WORKSPACE_WRITE("workspace.write", DevForgePermissionRisk.MEDIUM),
    FILES_READ("files.read", DevForgePermissionRisk.LOW),
    FILES_WRITE("files.write", DevForgePermissionRisk.MEDIUM),
    FILES_DELETE("files.delete", DevForgePermissionRisk.HIGH),
    EDITOR_READ("editor.read", DevForgePermissionRisk.LOW),
    EDITOR_WRITE("editor.write", DevForgePermissionRisk.MEDIUM),
    TERMINAL_READ("terminal.read", DevForgePermissionRisk.MEDIUM),
    TERMINAL_EXECUTE("terminal.execute", DevForgePermissionRisk.HIGH),
    GIT_READ("git.read", DevForgePermissionRisk.LOW),
    GIT_WRITE("git.write", DevForgePermissionRisk.MEDIUM),
    GIT_PUSH("git.push", DevForgePermissionRisk.HIGH),
    GITHUB_READ("github.read", DevForgePermissionRisk.MEDIUM),
    GITHUB_WRITE("github.write", DevForgePermissionRisk.HIGH),
    GITHUB_DELETE("github.delete", DevForgePermissionRisk.HIGH),
    BUILD_READ("build.read", DevForgePermissionRisk.LOW),
    BUILD_EXECUTE("build.execute", DevForgePermissionRisk.MEDIUM),
    BUILD_CANCEL("build.cancel", DevForgePermissionRisk.MEDIUM),
    ARTIFACT_CREATE("artifact.create", DevForgePermissionRisk.LOW),
    ARTIFACT_DELETE("artifact.delete", DevForgePermissionRisk.MEDIUM),
    AI_USE("ai.use", DevForgePermissionRisk.MEDIUM),
    AI_TOOLS_REGISTER("ai.tools.register", DevForgePermissionRisk.MEDIUM),
    AGENTS_CREATE("agents.create", DevForgePermissionRisk.MEDIUM),
    AGENTS_EXECUTE("agents.execute", DevForgePermissionRisk.HIGH),
    WORKFLOWS_REGISTER("workflows.register", DevForgePermissionRisk.MEDIUM),
    WORKFLOW_EXECUTE("workflow.execute", DevForgePermissionRisk.MEDIUM),
    AUTOMATION_REGISTER("automation.register", DevForgePermissionRisk.MEDIUM),
    AUTOMATION_EXECUTE("automation.execute", DevForgePermissionRisk.MEDIUM),
    NETWORK_ACCESS("network.access", DevForgePermissionRisk.MEDIUM),
    NETWORK_UNRESTRICTED("network.unrestricted", DevForgePermissionRisk.HIGH),
    STORAGE_READ("storage.read", DevForgePermissionRisk.LOW),
    STORAGE_WRITE("storage.write", DevForgePermissionRisk.MEDIUM),
    SECURE_STORAGE("secure_storage", DevForgePermissionRisk.HIGH),
    LANGUAGE_REGISTER("language.register", DevForgePermissionRisk.LOW),
    GIT_RESET("git.reset", DevForgePermissionRisk.HIGH),
    GIT_REBASE("git.rebase", DevForgePermissionRisk.HIGH),
    WORKSPACE_DELETE("workspace.delete", DevForgePermissionRisk.HIGH);

    companion object {
        private val byWire = entries.associateBy { it.wireName }
        fun fromWireName(value: String): DevForgePermission? = byWire[value.trim()]
    }
}

enum class DevForgePermissionRisk { LOW, MEDIUM, HIGH }

class DevForgePermissionSet(requested: Set<DevForgePermission>) {
    private val permissions = requested.toSet()
    fun contains(permission: DevForgePermission): Boolean = permission in permissions
    fun all(): Set<DevForgePermission> = permissions

    fun validate(): List<String> = buildList {
        if (permissions.size > MAX_PERMISSIONS) add("Extension requests more than $MAX_PERMISSIONS permissions.")
        if (DevForgePermission.NETWORK_UNRESTRICTED in permissions && DevForgePermission.NETWORK_ACCESS !in permissions) {
            add("network.unrestricted requires network.access.")
        }
        if (DevForgePermission.AGENTS_EXECUTE in permissions && DevForgePermission.AGENTS_CREATE !in permissions) {
            add("agents.execute requires agents.create.")
        }
    }

    companion object {
        const val MAX_PERMISSIONS = 32
    }
}
