package com.mrredhood.devforge.core.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentProfile(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val access: Set<AgentAccess>,
    val builtin: Boolean,
)

class AgentProfileRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<AgentProfile> {
        val stored = runCatching { JSONArray(prefs.getString(KEY_PROFILES, "[]")) }.getOrNull()
        if (stored == null || stored.length() == 0) {
            val defaults = defaultProfiles()
            persist(defaults)
            return defaults
        }
        return buildList(stored.length()) {
            for (index in 0 until stored.length()) {
                runCatching { decode(stored.getJSONObject(index)) }.getOrNull()?.let(::add)
            }
        }.ifEmpty {
            val defaults = defaultProfiles()
            persist(defaults)
            defaults
        }
    }

    fun save(profile: AgentProfile): List<AgentProfile> {
        val normalized = profile.copy(
            name = profile.name.trim().take(MAX_NAME_CHARS),
            description = profile.description.trim().take(MAX_DESCRIPTION_CHARS),
            instructions = profile.instructions.trim().take(MAX_INSTRUCTION_CHARS),
            access = profile.access.ifEmpty { AgentAccess.DEFAULT },
        )
        require(normalized.name.isNotBlank()) { "Agent name is required." }
        require(normalized.instructions.isNotBlank()) { "Agent instructions are required." }
        val values = list().toMutableList()
        val index = values.indexOfFirst { it.id == normalized.id }
        if (index >= 0) values[index] = normalized else values += normalized
        require(values.size <= MAX_PROFILES) { "At most $MAX_PROFILES saved agent profiles are supported." }
        persist(values)
        return values
    }

    fun delete(profileId: String): List<AgentProfile> {
        val values = list()
        require(values.firstOrNull { it.id == profileId }?.builtin != true) { "Premade agents are editable but cannot be deleted." }
        val remaining = values.filterNot { it.id == profileId }
        persist(remaining)
        return remaining
    }

    private fun persist(values: List<AgentProfile>) {
        prefs.edit().putString(KEY_PROFILES, JSONArray(values.map(::encode)).toString()).apply()
    }

    private fun encode(profile: AgentProfile): JSONObject = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name.take(MAX_NAME_CHARS))
        .put("description", profile.description.take(MAX_DESCRIPTION_CHARS))
        .put("instructions", profile.instructions.take(MAX_INSTRUCTION_CHARS))
        .put("access", JSONArray(profile.access.map(AgentAccess::name).sorted()))
        .put("builtin", profile.builtin)

    private fun decode(json: JSONObject): AgentProfile = AgentProfile(
        id = json.optString("id").take(120).ifBlank { UUID.randomUUID().toString() },
        name = json.optString("name").take(MAX_NAME_CHARS),
        description = json.optString("description").take(MAX_DESCRIPTION_CHARS),
        instructions = json.optString("instructions").take(MAX_INSTRUCTION_CHARS),
        access = AgentAccess.decode(json.optJSONArray("access")?.toString(), AgentAccess.DEFAULT),
        builtin = json.optBoolean("builtin", false),
    )

    companion object {
        private const val PREFS = "devforge_agent_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val MAX_PROFILES = 20
        const val MAX_NAME_CHARS = 80
        const val MAX_DESCRIPTION_CHARS = 240
        const val MAX_INSTRUCTION_CHARS = 8_000

        private fun defaultProfiles(): List<AgentProfile> = listOf(
            AgentProfile(
                "coder",
                "Coder",
                "Implements focused code changes and keeps edits bounded.",
                "Inspect the relevant files, make the smallest safe implementation, validate assumptions, and report exactly what changed.",
                setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS, AgentAccess.DIAGNOSTICS_ACCESS),
                true,
            ),
            AgentProfile(
                "reviewer",
                "Reviewer",
                "Reviews code for bugs, security flaws and maintainability issues.",
                "Inspect the requested area carefully. Identify concrete defects and, when requested, propose precise safe fixes.",
                setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS, AgentAccess.DIAGNOSTICS_ACCESS),
                true,
            ),
            AgentProfile(
                "tester",
                "Tester",
                "Focuses on regression coverage and validation.",
                "Inspect existing behavior and tests, add focused regression coverage, and keep test changes deterministic and bounded.",
                setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS, AgentAccess.DIAGNOSTICS_ACCESS),
                true,
            ),
            AgentProfile(
                "refactorer",
                "Refactorer",
                "Improves structure without changing intended behavior.",
                "Refactor only after understanding the existing behavior. Preserve public contracts and keep changes localized.",
                setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS, AgentAccess.DIAGNOSTICS_ACCESS),
                true,
            ),
            AgentProfile(
                "coordinator",
                "Coordinator",
                "Breaks work into focused steps and coordinates with other agents.",
                "Inspect the workspace, create precise handoffs or shared-memory notes, and avoid unnecessary mutations.",
                setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS, AgentAccess.COORDINATION_ACCESS),
                true,
            ),
        )
    }
}
