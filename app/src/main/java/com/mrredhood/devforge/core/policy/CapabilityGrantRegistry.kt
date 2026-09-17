package com.mrredhood.devforge.core.policy

import java.util.concurrent.ConcurrentHashMap

/** Process-local mirror of durable grants so execution gates stay synchronous and cheap. */
object CapabilityGrantRegistry {
    private data class Entry(
        val maxRisk: RiskLevel,
        val expiresAtEpochMs: Long?,
    )

    private val grants = ConcurrentHashMap<String, Entry>()

    private fun key(workspaceId: String, capability: Capability): String = "$workspaceId|${capability.name}"

    fun clear() = grants.clear()

    fun put(workspaceId: String, capability: Capability, maxRisk: RiskLevel, expiresAtEpochMs: Long?) {
        grants[key(workspaceId, capability)] = Entry(maxRisk, expiresAtEpochMs)
    }

    fun remove(workspaceId: String, capability: Capability) {
        grants.remove(key(workspaceId, capability))
    }

    fun allows(workspaceId: String, capability: Capability, risk: RiskLevel, now: Long = System.currentTimeMillis()): Boolean {
        val entry = grants[key(workspaceId, capability)] ?: return false
        if (entry.expiresAtEpochMs != null && entry.expiresAtEpochMs <= now) {
            grants.remove(key(workspaceId, capability))
            return false
        }
        return risk.ordinal <= entry.maxRisk.ordinal
    }
}
