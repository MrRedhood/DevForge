package com.mrredhood.devforge.core.recovery

object RecoveryPolicy {
    const val AGENT_RECOVERY_MESSAGE = "Paused after the previous DevForge process ended."
    const val AUTOMATION_RECOVERY_MESSAGE = "Recovered stale automation run after process interruption."
    const val MAX_AUTOMATION_STALE_RUN_MS = 5L * 60L * 1000L

    private val recoverableAgentStatuses = setOf("PLANNING", "RUNNING")
    private val activeAutomationStatuses = setOf("RUNNING", "WAITING_APPROVAL")

    fun shouldPauseInterruptedAgent(status: String): Boolean =
        status in recoverableAgentStatuses

    fun shouldRecoverStaleAutomation(
        status: String,
        startedAtEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean =
        status in activeAutomationStatuses &&
            startedAtEpochMs + MAX_AUTOMATION_STALE_RUN_MS < nowEpochMs

    fun isActiveAutomation(status: String): Boolean =
        status in activeAutomationStatuses
}
