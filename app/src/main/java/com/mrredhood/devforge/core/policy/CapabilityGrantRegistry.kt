package com.mrredhood.devforge.core.policy

import com.mrredhood.devforge.core.security.WorkspacePathScope
import java.util.concurrent.ConcurrentHashMap

/** Process-local mirror of durable grants so execution gates stay synchronous and cheap. */
object CapabilityGrantRegistry {
    private data class Entry(
        val maxRisk: RiskLevel,
        val expiresAtEpochMs: Long?,
        val pathScope: WorkspacePathScope,
    )

    private val grants = ConcurrentHashMap<String, Entry>()

    private fun key(workspaceId: String, capability: Capability): String = "$workspaceId|${capability.name}"

    fun clear() = grants.clear()

    fun put(
        workspaceId: String,
        capability: Capability,
        maxRisk: RiskLevel,
        expiresAtEpochMs: Long?,
        pathScope: WorkspacePathScope = WorkspacePathScope(),
    ) {
        grants[key(workspaceId, capability)] = Entry(maxRisk, expiresAtEpochMs, pathScope)
    }

    fun remove(workspaceId: String, capability: Capability) {
        grants.remove(key(workspaceId, capability))
    }

    /** Backward-compatible time-aware overload retained for existing policy tests/callers. */
    fun allows(
        workspaceId: String,
        capability: Capability,
        risk: RiskLevel,
        now: Long,
    ): Boolean = allows(workspaceId, capability, risk, null, now)

    fun allows(
        workspaceId: String,
        capability: Capability,
        risk: RiskLevel,
        pathScope: WorkspacePathScope? = null,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val entry = grants[key(workspaceId, capability)] ?: return false
        if (entry.expiresAtEpochMs != null && entry.expiresAtEpochMs <= now) {
            grants.remove(key(workspaceId, capability))
            return false
        }
        if (risk.ordinal > entry.maxRisk.ordinal) return false
        val requiredScope = pathScope ?: WorkspacePathScope()
        return entry.pathScope.covers(requiredScope)
    }
}
