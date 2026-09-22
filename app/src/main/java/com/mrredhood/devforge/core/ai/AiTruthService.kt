package com.mrredhood.devforge.core.ai

import android.content.Context
import com.mrredhood.devforge.core.agent.AgentRuntime
import com.mrredhood.devforge.core.agent.DevForgeToolCatalog
import com.mrredhood.devforge.core.agent.ToolSettingsStore
import com.mrredhood.devforge.core.policy.PermissionMode

data class DevForgeToolInventory(
    val registeredCount: Int,
    val userConfigurableCount: Int,
    val enabledUserCount: Int,
    val disabledUserCount: Int,
    val internalCount: Int,
    val currentlyUsableCount: Int,
    val enabledUserTitles: List<String>,
    val disabledUserTitles: List<String>,
    val unregisteredCatalogTitles: List<String>,
)

class AiTruthService(context: Context) {
    private val appContext = context.applicationContext
    private val settings = ToolSettingsStore(appContext)
    private val runtime by lazy {
        AgentRuntime.createToolRuntime(appContext, PermissionMode.SOME)
    }

    fun toolInventory(): DevForgeToolInventory {
        val registered = runtime.registry.definitions()
        val registeredIds = registered.mapTo(linkedSetOf()) { it.id }
        val catalogIds = DevForgeToolCatalog.userToolIds
        val enabledIds = settings.enabledToolIds()
        val enabledUser = enabledIds
            .filter { it in registeredIds }
            .mapNotNull { DevForgeToolCatalog.entry(it)?.title }
        val disabledUser = catalogIds
            .filter { it !in enabledIds }
            .mapNotNull { DevForgeToolCatalog.entry(it)?.title }
        val unregistered = catalogIds
            .filter { it !in registeredIds }
            .mapNotNull { DevForgeToolCatalog.entry(it)?.title }
        val internalCount = registered.count { it.id !in catalogIds }
        return DevForgeToolInventory(
            registeredCount = registered.size,
            userConfigurableCount = DevForgeToolCatalog.entries.size,
            enabledUserCount = enabledUser.size,
            disabledUserCount = disabledUser.size,
            internalCount = internalCount,
            currentlyUsableCount = enabledUser.size + internalCount,
            enabledUserTitles = enabledUser.sorted(),
            disabledUserTitles = disabledUser.sorted(),
            unregisteredCatalogTitles = unregistered.sorted(),
        )
    }

    fun answerIfKnown(question: String): String? {
        val normalized = question.trim().lowercase()
        if (normalized.isBlank()) return null
        val asksToolCount = "tool" in normalized && (
            "how many" in normalized ||
                "count" in normalized ||
                "working" in normalized ||
                "available" in normalized ||
                "enabled" in normalized ||
                "registered" in normalized
        )
        val asksToolList = "tool" in normalized && (
            "which" in normalized ||
                "what tools" in normalized ||
                "list" in normalized
        )
        if (!asksToolCount && !asksToolList) return null

        val inventory = toolInventory()
        val lines = mutableListOf<String>()
        lines += "Live DevForge tool inventory:"
        lines += "• Registered tool definitions: ${inventory.registeredCount}"
        lines += "• User-configurable tools: ${inventory.userConfigurableCount}"
        lines += "• Enabled user tools: ${inventory.enabledUserCount}"
        lines += "• Disabled user tools: ${inventory.disabledUserCount}"
        lines += "• Internal/system tools: ${inventory.internalCount}"
        lines += "• Currently usable registered tools: ${inventory.currentlyUsableCount}"
        if (inventory.unregisteredCatalogTitles.isNotEmpty()) {
            lines += "• Catalog entries not registered right now: ${inventory.unregisteredCatalogTitles.joinToString(", ")}"
        }
        if (asksToolList) {
            lines += ""
            lines += "Enabled user tools:"
            inventory.enabledUserTitles.forEach { lines += "• $it" }
            if (inventory.internalCount > 0) lines += "• ${inventory.internalCount} internal/system tools are also registered."
        }
        lines += ""
        lines += "This is read from the live DevForge registry and tool settings, not estimated by the AI. “Currently usable” means registered and enabled; it does not mean every tool has just executed successfully."
        return lines.joinToString("\n")
    }

    fun requiresAuthoritativeEvidence(question: String): Boolean {
        val value = question.lowercase()
        return listOf(
            "currently", "current", "latest", "working", "enabled", "disabled", "available",
            "registered", "status", "how many", "count", "which tools", "what tools",
            "what files", "where is", "where are", "in this project", "in the workspace",
            "devforge", "build", "ci", "github actions", "setting", "provider", "model",
        ).any { it in value }
    }

    fun groundingInstruction(question: String): String = if (requiresAuthoritativeEvidence(question)) {
        "AUTHORITATIVE-EVIDENCE RULE: This request may depend on current DevForge/project state. Do not answer from memory. First call the most authoritative available tool (tool registry/settings for tool questions; get_workspace_context for current workspace/build/editor state; search/read tools for codebase facts; web_search for current external facts). Only state facts supported by returned evidence. Never invent counts, names, statuses, paths, versions, or completion claims. Distinguish registered vs enabled vs executable vs successfully tested. If evidence is unavailable or contradictory, explicitly say that and do not guess."
    } else {
        "GENERAL ACCURACY RULE: Do not invent facts. When a claim depends on current state, verify it with an authoritative tool or source. When evidence is insufficient, say so rather than filling the gap."
    }
}