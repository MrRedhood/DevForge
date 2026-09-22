package com.mrredhood.devforge.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.json.JSONArray

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
    val scopeJson: String = """[""]""",
) {
    fun capabilityOrNull(): Capability? = runCatching { Capability.valueOf(capability) }.getOrNull()
    fun maxRiskOrNull(): RiskLevel? = runCatching { RiskLevel.valueOf(maxRisk) }.getOrNull()

    fun pathScopeOrNull(): WorkspacePathScope? = runCatching {
        val array = JSONArray(scopeJson)
        require(array.length() <= WorkspacePathScope.MAX_PREFIXES) { "Grant path scope exceeds the prefix limit." }
        WorkspacePathScope(buildList(array.length()) {
            for (index in 0 until array.length()) add(array.optString(index, ""))
        })
    }.getOrNull()
}
