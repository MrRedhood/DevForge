package com.mrredhood.devforge.core.storage

import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.CapabilityGrantRegistry
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.json.JSONArray
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class CapabilityGrantRepository(private val dao: CapabilityGrantDao) {
    fun observe(workspaceId: String, limit: Int = MAX_GRANTS): Flow<List<CapabilityGrantEntity>> =
        dao.observeForWorkspace(workspaceId, limit)

    suspend fun activeGrant(workspaceId: String, capability: Capability): CapabilityGrantEntity? =
        dao.getActive(workspaceId, capability.name, System.currentTimeMillis())

    suspend fun isGranted(
        requestCapability: Capability,
        workspaceId: String,
        risk: RiskLevel,
        pathScope: WorkspacePathScope? = null,
    ): Boolean {
        if (!isGrantable(requestCapability)) return false
        val grant = activeGrant(workspaceId, requestCapability) ?: return false
        val ceiling = grant.maxRiskOrNull() ?: return false
        val grantedScope = grant.pathScopeOrNull() ?: return false
        return risk.ordinal <= ceiling.ordinal && grantedScope.covers(pathScope ?: WorkspacePathScope())
    }

    suspend fun grant(
        workspaceId: String,
        capability: Capability,
        maxRisk: RiskLevel = RiskLevel.R2,
        expiresAtEpochMs: Long? = null,
        pathScope: WorkspacePathScope = WorkspacePathScope(),
    ): CapabilityGrantEntity? {
        if (!isGrantable(capability)) return null
        val canonicalScope = WorkspacePathScope(pathScope.canonicalPrefixes())
        val effectiveRisk = RiskLevel.R3
        val entity = CapabilityGrantEntity(
            grantId = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            capability = capability.name,
            maxRisk = effectiveRisk.name,
            createdAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = expiresAtEpochMs,
            enabled = true,
            scopeJson = JSONArray(canonicalScope.canonicalPrefixes()).toString(),
        )
        dao.upsert(entity)
        CapabilityGrantRegistry.put(workspaceId, capability, effectiveRisk, expiresAtEpochMs, canonicalScope)
        return entity
    }

    suspend fun revoke(workspaceId: String, capability: Capability): Boolean {
        CapabilityGrantRegistry.remove(workspaceId, capability)
        return dao.revoke(workspaceId, capability.name) > 0
    }

    suspend fun pruneExpired(): Int = dao.pruneExpired(System.currentTimeMillis())

    suspend fun restoreActiveGrants(limit: Int = MAX_GRANTS * 4) {
        CapabilityGrantRegistry.clear()
        dao.listActive(System.currentTimeMillis(), limit).forEach { grant ->
            val capability = grant.capabilityOrNull() ?: return@forEach
            if (!isGrantable(capability)) return@forEach
            val risk = grant.maxRiskOrNull() ?: return@forEach
            val scope = grant.pathScopeOrNull() ?: return@forEach
            CapabilityGrantRegistry.put(
                grant.workspaceId,
                capability,
                risk,
                grant.expiresAtEpochMs,
                scope,
            )
        }
    }

    companion object {
        const val MAX_GRANTS = 50

        fun isGrantable(capability: Capability): Boolean = capability !in setOf(
            Capability.DELETE_BRANCH,
            Capability.PUSH_REMOTE,
            Capability.MANAGE_RELEASE,
            Capability.ACCESS_SECRET,
            Capability.REBASE_BRANCH,
        )
    }
}
