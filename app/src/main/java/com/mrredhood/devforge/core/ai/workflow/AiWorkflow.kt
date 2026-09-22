package com.mrredhood.devforge.core.ai.workflow

enum class AiWorkflowPhase { UNDERSTAND, PLAN, INSPECT, EXECUTE, VERIFY, REVIEW }
enum class AiActivityKind { PLAN, SEARCH, READ, WRITE, DELETE, COMMAND, TEST, BUILD, AGENT, APPROVAL, VERIFICATION, ERROR, INFO }
enum class AiActivityStatus { RUNNING, COMPLETED, FAILED, WAITING }

data class AiActivity(
    val id: String,
    val kind: AiActivityKind,
    val status: AiActivityStatus,
    val title: String,
    val detail: String = "",
    val paths: List<String> = emptyList(),
    val createdAtEpochMs: Long,
)

data class AiVerificationCheck(
    val id: String,
    val title: String,
    val status: Status,
    val detail: String = "",
) {
    enum class Status { PASSED, SKIPPED, FAILED }
}

data class AiVerificationReceipt(
    val checks: List<AiVerificationCheck>,
    val verifiedAtEpochMs: Long,
) {
    val passedCount: Int get() = checks.count { it.status == AiVerificationCheck.Status.PASSED }
    val failedCount: Int get() = checks.count { it.status == AiVerificationCheck.Status.FAILED }
}

data class AiWorkflowSnapshot(
    val workflowId: String,
    val workspaceId: String?,
    val workspaceName: String?,
    val request: String,
    val phase: AiWorkflowPhase,
    val status: Status,
    val currentStep: String?,
    val activities: List<AiActivity>,
    val verification: AiVerificationReceipt?,
    val summary: String?,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    enum class Status { RUNNING, WAITING, COMPLETED, FAILED, CANCELLED }
}
