package com.mrredhood.devforge.core.storage

import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import kotlinx.coroutines.flow.Flow

class ApprovalRepository(private val dao: ApprovalDao) {
    fun observePending(limit: Int = MAX_PENDING): Flow<List<ApprovalEntity>> = dao.observePending(limit)

    fun observeApproved(actionPrefix: String, limit: Int = MAX_APPROVED): Flow<List<ApprovalEntity>> =
        dao.observeApproved("$actionPrefix%", limit)

    fun observeById(approvalId: String): Flow<ApprovalEntity?> = dao.observeById(approvalId)

    suspend fun createPending(
        approvalId: String,
        actionId: String,
        capability: Capability,
        risk: RiskLevel,
        workspaceId: String,
        summary: String,
        parametersHash: String,
        preconditionHash: String?,
        payload: String,
        expiresAtEpochMs: Long,
    ): ApprovalEntity {
        val entity = ApprovalEntity(
            approvalId = approvalId,
            actionId = actionId,
            capability = capability.name,
            risk = risk.name,
            workspaceId = workspaceId,
            summary = summary,
            parametersHash = parametersHash,
            preconditionHash = preconditionHash,
            payload = payload,
            status = STATUS_PENDING,
            createdAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = expiresAtEpochMs,
        )
        dao.insertPending(entity)
        return entity
    }

    suspend fun approve(approvalId: String): Boolean =
        dao.resolve(approvalId, STATUS_APPROVED, System.currentTimeMillis()) == 1

    suspend fun reject(approvalId: String): Boolean =
        dao.resolve(approvalId, STATUS_REJECTED, System.currentTimeMillis()) == 1

    suspend fun claimApproved(approvalId: String): Boolean =
        dao.claimApproved(approvalId, System.currentTimeMillis()) == 1

    suspend fun finishSuccess(approvalId: String): Boolean =
        dao.finishExecution(approvalId, STATUS_COMPLETED, System.currentTimeMillis()) == 1

    suspend fun finishFailure(approvalId: String): Boolean =
        dao.finishExecution(approvalId, STATUS_FAILED, System.currentTimeMillis()) == 1

    suspend fun expireDue(): Int = dao.expire(System.currentTimeMillis())

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_EXECUTING = "EXECUTING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val MAX_PENDING = 50
        const val MAX_APPROVED = 10
    }
}
