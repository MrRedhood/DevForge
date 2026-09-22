package com.mrredhood.devforge.core.storage

import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.SecretRedactor
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
        require(approvalId.isNotBlank() && approvalId.length <= MAX_ID_LENGTH) {
            "Approval identifier is invalid."
        }
        require(actionId.isNotBlank() && actionId.length <= MAX_ACTION_ID_LENGTH) {
            "Approval action identifier is invalid."
        }
        require(workspaceId.isNotBlank() && workspaceId.length <= MAX_ID_LENGTH) {
            "Approval workspace identifier is invalid."
        }
        require(parametersHash.matches(HASH_PATTERN)) {
            "Approval parameter hash is invalid."
        }
        require(preconditionHash == null || preconditionHash.matches(HASH_PATTERN)) {
            "Approval precondition hash is invalid."
        }
        require(expiresAtEpochMs > System.currentTimeMillis()) {
            "Approval expiration must be in the future."
        }
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "Approval payload exceeds the persistence limit."
        }
        val redactedPayload = SecretRedactor.redact(payload, MAX_PAYLOAD_BYTES)
        require(redactedPayload == payload || !payload.contains("\"tool\"")) {
            "Executable approval payload contains secret-like material and cannot be safely redacted."
        }
        val entity = ApprovalEntity(
            approvalId = approvalId,
            actionId = actionId,
            capability = capability.name,
            risk = risk.name,
            workspaceId = workspaceId,
            summary = SecretRedactor.redact(summary, MAX_SUMMARY_LENGTH),
            parametersHash = parametersHash,
            preconditionHash = preconditionHash,
            payload = redactedPayload,
            status = STATUS_PENDING,
            createdAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = expiresAtEpochMs,
        )
        dao.insertPending(entity)
        return entity
    }

    suspend fun approve(approvalId: String): Boolean {
        val resolved = dao.resolve(approvalId, STATUS_APPROVED, System.currentTimeMillis()) == 1
        return resolved
    }

    suspend fun reject(approvalId: String): Boolean {
        val resolved = dao.resolve(approvalId, STATUS_REJECTED, System.currentTimeMillis()) == 1
        return resolved
    }

    suspend fun claimApproved(approvalId: String): Boolean =
        dao.claimApproved(approvalId, System.currentTimeMillis()) == 1

    suspend fun finishSuccess(approvalId: String): Boolean =
        dao.finishExecution(approvalId, STATUS_COMPLETED, System.currentTimeMillis()) == 1

    suspend fun finishFailure(approvalId: String): Boolean =
        dao.finishExecution(approvalId, STATUS_FAILED, System.currentTimeMillis()) == 1

    suspend fun expireDue(): Int = dao.expire(System.currentTimeMillis())

    suspend fun recoverStaleExecuting(maxAgeMs: Long = STALE_EXECUTION_MAX_AGE_MS): Int {
        require(maxAgeMs > 0) { "Execution recovery age must be positive." }
        val now = System.currentTimeMillis()
        return dao.recoverStaleExecuting(now, now - maxAgeMs)
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_EXECUTING = "EXECUTING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val MAX_PENDING = 50
        const val MAX_SUMMARY_LENGTH = 500
        const val MAX_PAYLOAD_BYTES = 64 * 1024
        const val MAX_APPROVED = 10
        const val MAX_ID_LENGTH = 200
        const val MAX_ACTION_ID_LENGTH = 500
        const val STALE_EXECUTION_MAX_AGE_MS = 15L * 60L * 1000L
        val HASH_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
}
