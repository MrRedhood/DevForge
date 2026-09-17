package com.mrredhood.devforge.core.policy

/** Capability-first policy primitives shared by future AI, Git and build execution. */
enum class RiskLevel { R0, R1, R2, R3, R4, R5 }

enum class Capability {
    READ_WORKSPACE,
    EDIT_FILES,
    DELETE_FILES,
    RUN_CHECK,
    STAGE_FILES,
    CREATE_COMMIT,
    CREATE_BRANCH,
    SWITCH_BRANCH,
    DELETE_BRANCH,
    FETCH_REMOTE,
    PULL_REMOTE,
    PUSH_REMOTE,
    MERGE_BRANCH,
    REBASE_BRANCH,
    CHERRY_PICK,
    DISPATCH_BUILD,
    MANAGE_RELEASE,
    ACCESS_SECRET,
}

enum class PermissionMode { NEVER, SOME, AUTONOMOUS }

data class ActionRequest(
    val actionId: String,
    val capability: Capability,
    val risk: RiskLevel,
    val workspaceId: String,
    val summary: String,
    val parametersHash: String,
    val preconditionHash: String? = null,
)

data class Approval(
    val approvalId: String,
    val actionId: String,
    val parametersHash: String,
    val workspaceRevision: String,
    val policyVersion: String,
    val expiresAtEpochMs: Long,
)

object DefaultPolicy {
    fun requiresApproval(request: ActionRequest, mode: PermissionMode): Boolean {
        val baseRequiresApproval = when (mode) {
            PermissionMode.NEVER -> true
            PermissionMode.SOME -> request.risk >= RiskLevel.R2
            PermissionMode.AUTONOMOUS -> request.risk >= RiskLevel.R4 || request.capability in setOf(
                Capability.DELETE_FILES,
                Capability.DELETE_BRANCH,
                Capability.PUSH_REMOTE,
                Capability.MANAGE_RELEASE,
                Capability.ACCESS_SECRET,
                Capability.REBASE_BRANCH,
            )
        }
        if (!baseRequiresApproval) return false
        return !CapabilityGrantRegistry.allows(request.workspaceId, request.capability, request.risk)
    }

    /** A persistent grant may relax the normal approval gate only for explicitly grantable capabilities. */
    fun requiresApprovalWithGrant(
        request: ActionRequest,
        mode: PermissionMode,
        grantActive: Boolean,
        grantRiskCeiling: RiskLevel? = null,
    ): Boolean {
        if (!requiresApproval(request, mode)) return false
        if (!grantActive || !isGrantable(request.capability)) return true
        val ceiling = grantRiskCeiling ?: return true
        return request.risk.ordinal > ceiling.ordinal
    }

    fun isGrantable(capability: Capability): Boolean = capability !in setOf(
        Capability.DELETE_FILES,
        Capability.DELETE_BRANCH,
        Capability.PUSH_REMOTE,
        Capability.MANAGE_RELEASE,
        Capability.ACCESS_SECRET,
        Capability.REBASE_BRANCH,
    )
}
