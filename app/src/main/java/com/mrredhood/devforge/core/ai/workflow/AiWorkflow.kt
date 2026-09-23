package com.mrredhood.devforge.core.ai.workflow

enum class AiWorkflowPhase { UNDERSTAND, PLAN, INSPECT, EXECUTE, VERIFY, REVIEW }
enum class AiActivityKind { PLAN, SEARCH, READ, WRITE, DELETE, COMMAND, TEST, BUILD, AGENT, APPROVAL, VERIFICATION, ERROR, INFO }
enum class AiActivityStatus { RUNNING, COMPLETED, FAILED, WAITING }

enum class AiPlanStepStatus { PENDING, RUNNING, COMPLETED, FAILED }

data class AiPlanStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: AiPlanStepStatus,
)

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
    val planSteps: List<AiPlanStep>
        get() {
            val definitions = listOf(
                "understand" to ("Understand request" to "Clarify the goal, constraints, workspace and expected result."),
                "plan" to ("Create plan" to "Turn the request into an ordered set of implementation steps."),
                "inspect" to ("Inspect workspace" to "Search and read the relevant files before making changes."),
                "execute" to ("Execute plan" to "Apply approved file, folder, terminal, Git and other required actions."),
                "verify" to ("Verify result" to "Run the checks, tests, lint or build evidence required for the task."),
                "review" to ("Review result" to "Summarize what changed, what was verified and any remaining issues."),
            )
            val currentIndex = phase.ordinal.coerceIn(0, definitions.lastIndex)
            return definitions.mapIndexed { index, (id, copy) ->
                val status = when {
                    this.status == Status.COMPLETED -> AiPlanStepStatus.COMPLETED
                    index < currentIndex -> AiPlanStepStatus.COMPLETED
                    index > currentIndex -> AiPlanStepStatus.PENDING
                    this.status == Status.FAILED || this.status == Status.CANCELLED -> AiPlanStepStatus.FAILED
                    else -> AiPlanStepStatus.RUNNING
                }
                AiPlanStep(id, copy.first, copy.second, status)
            }
        }

    val completedPlanSteps: Int
        get() = planSteps.count { it.status == AiPlanStepStatus.COMPLETED }

    val changedPaths: List<String>
        get() = activities.flatMap { it.paths }.distinct().take(40)

    val writeCount: Int
        get() = activities.count { it.kind == AiActivityKind.WRITE && it.status != AiActivityStatus.FAILED }

    val deleteCount: Int
        get() = activities.count { it.kind == AiActivityKind.DELETE && it.status != AiActivityStatus.FAILED }

    val commandCount: Int
        get() = activities.count { it.kind == AiActivityKind.COMMAND && it.status != AiActivityStatus.FAILED }

    val changeSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (writeCount > 0) parts += "$writeCount writes"
            if (deleteCount > 0) parts += "$deleteCount deletes"
            if (commandCount > 0) parts += "$commandCount commands"
            if (changedPaths.isNotEmpty()) parts += changedPaths.size.toString() + " paths"
            return parts.joinToString(" · ").ifBlank { "No recorded file mutations" }
        }
    enum class Status { RUNNING, WAITING, COMPLETED, FAILED, CANCELLED }
}
