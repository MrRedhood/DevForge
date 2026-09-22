package com.mrredhood.devforge.core.build

import com.mrredhood.devforge.core.github.GitHubWorkflowRun

enum class CiHealthState {
    HEALTHY,
    RUNNING,
    FAILED,
    MIXED,
    UNKNOWN,
}

data class CiHealthSnapshot(
    val state: CiHealthState,
    val total: Int,
    val running: Int,
    val successful: Int,
    val failed: Int,
    val cancelled: Int,
    val latestWorkflow: String?,
)

object CiHealthAggregator {
    fun summarize(runs: List<GitHubWorkflowRun>): CiHealthSnapshot {
        if (runs.isEmpty()) {
            return CiHealthSnapshot(
                state = CiHealthState.UNKNOWN,
                total = 0,
                running = 0,
                successful = 0,
                failed = 0,
                cancelled = 0,
                latestWorkflow = null,
            )
        }
        val running = runs.count { it.status in ACTIVE }
        val failed = runs.count { it.conclusion in setOf("failure", "timed_out", "startup_failure") }
        val cancelled = runs.count { it.conclusion == "cancelled" }
        val successful = runs.count { it.conclusion == "success" }
        val completed = runs.size - running
        val state = when {
            running > 0 -> CiHealthState.RUNNING
            failed > 0 && successful > 0 -> CiHealthState.MIXED
            failed > 0 -> CiHealthState.FAILED
            completed > 0 && successful == completed -> CiHealthState.HEALTHY
            else -> CiHealthState.UNKNOWN
        }
        return CiHealthSnapshot(
            state = state,
            total = runs.size,
            running = running,
            successful = successful,
            failed = failed,
            cancelled = cancelled,
            latestWorkflow = runs.firstOrNull()?.name,
        )
    }

    private val ACTIVE = setOf("queued", "in_progress", "requested", "waiting", "pending")
}
