package com.mrredhood.devforge.core.recovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPolicyTest {

    @Test
    fun interruptedAgentStatesPauseButTerminalAndWaitingStatesDoNot() {
        assertTrue(RecoveryPolicy.shouldPauseInterruptedAgent("PLANNING"))
        assertTrue(RecoveryPolicy.shouldPauseInterruptedAgent("RUNNING"))
        assertFalse(RecoveryPolicy.shouldPauseInterruptedAgent("QUEUED"))
        assertFalse(RecoveryPolicy.shouldPauseInterruptedAgent("WAITING_APPROVAL"))
        assertFalse(RecoveryPolicy.shouldPauseInterruptedAgent("COMPLETED"))
        assertFalse(RecoveryPolicy.shouldPauseInterruptedAgent("FAILED"))
        assertFalse(RecoveryPolicy.shouldPauseInterruptedAgent("CANCELLED"))
    }

    @Test
    fun staleAutomationRecoveryRequiresActiveStatusAndStrictAgeThreshold() {
        val now = 1_000_000L
        val stale = now - RecoveryPolicy.MAX_AUTOMATION_STALE_RUN_MS - 1L
        val fresh = now - RecoveryPolicy.MAX_AUTOMATION_STALE_RUN_MS

        assertTrue(RecoveryPolicy.shouldRecoverStaleAutomation("RUNNING", stale, now))
        assertTrue(RecoveryPolicy.shouldRecoverStaleAutomation("WAITING_APPROVAL", stale, now))
        assertFalse(RecoveryPolicy.shouldRecoverStaleAutomation("RUNNING", fresh, now))
        assertFalse(RecoveryPolicy.shouldRecoverStaleAutomation("QUEUED", stale, now))
        assertFalse(RecoveryPolicy.shouldRecoverStaleAutomation("COMPLETED", stale, now))
    }

    @Test
    fun activeAutomationStatusesAreExplicitlyBounded() {
        assertTrue(RecoveryPolicy.isActiveAutomation("RUNNING"))
        assertTrue(RecoveryPolicy.isActiveAutomation("WAITING_APPROVAL"))
        assertFalse(RecoveryPolicy.isActiveAutomation("FAILED"))
        assertFalse(RecoveryPolicy.isActiveAutomation("CANCELLED"))
    }
}
