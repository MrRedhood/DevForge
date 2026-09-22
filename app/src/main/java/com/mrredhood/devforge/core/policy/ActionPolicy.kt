package com.mrredhood.devforge.core.policy

import com.mrredhood.devforge.core.security.WorkspacePathScope

/** Capability-first policy primitives shared by future AI, Git and build execution. */
enum class RiskLevel { R0, R1, R2, R3, R4, R5 }

enum class Capability {
    READ_WORKSPACE,
    EDIT_FILES,
    COORDINATE_AGENTS,
    DELETE_FILES,
    RUN_CHECK,
    RUN_TERMINAL,
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
    CANCEL_BUILD,
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
    val pathScope: WorkspacePathScope? = null,
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
        // NEVER is an absolute deny for autonomous execution. A persistent grant must not
        // override it, otherwise the setting could silently become executable.
        if (mode == PermissionMode.NEVER) return true

        val baseRequiresApproval = when (mode) {
            PermissionMode.SOME -> request.risk >= RiskLevel.R2
            PermissionMode.AUTONOMOUS -> request.risk >= RiskLevel.R4 || request.capability in setOf(
                Capability.RUN_TERMINAL,
                Capability.DELETE_FILES,
                Capability.DELETE_BRANCH,
                Capability.PUSH_REMOTE,
                Capability.MANAGE_RELEASE,
                Capability.ACCESS_SECRET,
                Capability.REBASE_BRANCH,
            )
        }
        if (!baseRequiresApproval) return false
        return !CapabilityGrantRegistry.allows(
            request.workspaceId,
            request.capability,
            request.risk,
            request.pathScope,
        )
    }

    /** A persistent grant may relax the normal approval gate only for explicitly grantable capabilities. */
    fun requiresApprovalWithGrant(
        request: ActionRequest,
        mode: PermissionMode,
        grantActive: Boolean,
        grantRiskCeiling: RiskLevel? = null,
    ): Boolean {
        if (mode == PermissionMode.NEVER) return true
        if (!requiresApproval(request, mode)) return false
        if (!grantActive || !isGrantable(request.capability)) return true
        val ceiling = grantRiskCeiling ?: return true
        return request.risk.ordinal > ceiling.ordinal
    }

    fun isGrantable(capability: Capability): Boolean = capability !in setOf(
        Capability.DELETE_BRANCH,
        Capability.PUSH_REMOTE,
        Capability.MANAGE_RELEASE,
        Capability.ACCESS_SECRET,
        Capability.REBASE_BRANCH,
    )
}
