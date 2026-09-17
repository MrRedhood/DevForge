package com.mrredhood.devforge.core.storage

import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class CapabilityGrantRepository(
    private val dao: CapabilityGrantDao,
    private val audit: AuditTrailRepository? = null,
) {
    fun observe(workspaceId: String, limit: Int = MAX_GRANTS): Flow<List<CapabilityGrantEntity>> =
        dao.observeForWorkspace(workspaceId, limit)

    suspend fun activeGrant(workspaceId: String, capability: Capability): CapabilityGrantEntity? =
        dao.getActive(workspaceId, capability.name, System.currentTimeMillis())

    suspend fun isGranted(requestCapability: Capability, workspaceId: String, risk: RiskLevel): Boolean {
        if (!isGrantable(requestCapability)) return false
        val grant = activeGrant(workspaceId, requestCapability) ?: return false
        val ceiling = grant.maxRiskOrNull() ?: return false
        return risk.ordinal <= ceiling.ordinal
    }

    suspend fun grant(
        workspaceId: String,
        capability: Capability,
        maxRisk: RiskLevel = RiskLevel.R2,
        expiresAtEpochMs: Long? = null,
    ): CapabilityGrantEntity? {
        if (!isGrantable(capability)) return null
        val now = System.currentTimeMillis()
        val entity = CapabilityGrantEntity(
            grantId = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            capability = capability.name,
            maxRisk = maxRisk.name,
            createdAtEpochMs = now,
            expiresAtEpochMs = expiresAtEpochMs,
            enabled = true,
        )
        dao.upsert(entity)
        audit?.record(
            eventType = "GRANT_CREATED",
            summary = "Persistent grant enabled for ${capability.name}",
            workspaceId = workspaceId,
            capability = capability.name,
            risk = maxRisk.name,
        )
        return entity
    }

    suspend fun revoke(workspaceId: String, capability: Capability): Boolean {
        val changed = dao.revoke(workspaceId, capability.name) > 0
        if (changed) {
            audit?.record(
                eventType = "GRANT_REVOKED",
                summary = "Persistent grant revoked for ${capability.name}",
                workspaceId = workspaceId,
                capability = capability.name,
            )
        }
        return changed
    }

    suspend fun pruneExpired(): Int = dao.pruneExpired(System.currentTimeMillis())

    companion object {
        const val MAX_GRANTS = 50

        fun isGrantable(capability: Capability): Boolean = capability !in setOf(
            Capability.DELETE_FILES,
            Capability.DELETE_BRANCH,
            Capability.PUSH_REMOTE,
            Capability.MANAGE_RELEASE,
            Capability.ACCESS_SECRET,
            Capability.REBASE_BRANCH,
        )
    }
}
