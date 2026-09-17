package com.mrredhood.devforge.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel

@Entity(
    tableName = "capability_grants",
    indices = [
        androidx.room.Index(value = ["workspaceId", "capability"], unique = true),
        androidx.room.Index(value = ["expiresAtEpochMs"]),
    ],
)
data class CapabilityGrantEntity(
    @PrimaryKey val grantId: String,
    val workspaceId: String,
    val capability: String,
    val maxRisk: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val enabled: Boolean = true,
) {
    fun capabilityOrNull(): Capability? = runCatching { Capability.valueOf(capability) }.getOrNull()
    fun maxRiskOrNull(): RiskLevel? = runCatching { RiskLevel.valueOf(maxRisk) }.getOrNull()
}
