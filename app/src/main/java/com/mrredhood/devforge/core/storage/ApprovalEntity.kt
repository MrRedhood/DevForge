package com.mrredhood.devforge.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mrredhood.devforge.core.policy.Approval
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel

@Entity(tableName = "approval_actions")
data class ApprovalEntity(
    @PrimaryKey val approvalId: String,
    val actionId: String,
    val capability: String,
    val risk: String,
    val workspaceId: String,
    val summary: String,
    val parametersHash: String,
    val preconditionHash: String?,
    val payload: String,
    val status: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val resolvedAtEpochMs: Long? = null,
)

fun ApprovalEntity.toDomain(): Approval = Approval(
    approvalId = approvalId,
    actionId = actionId,
    parametersHash = parametersHash,
    workspaceRevision = preconditionHash.orEmpty(),
    policyVersion = "v1",
    expiresAtEpochMs = expiresAtEpochMs,
)

fun ApprovalEntity.capabilityOrNull(): Capability? = runCatching { Capability.valueOf(capability) }.getOrNull()
fun ApprovalEntity.riskOrNull(): RiskLevel? = runCatching { RiskLevel.valueOf(risk) }.getOrNull()
