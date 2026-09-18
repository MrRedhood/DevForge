package com.mrredhood.devforge.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProfileRepositoryTest {
    @Test
    fun catalogContainsAtLeastFifteenPremadeAgents() {
        val profiles = AgentProfileRepository().list()

        assertTrue(profiles.size >= 15)
        assertTrue(profiles.all { it.builtin })
        assertEquals(profiles.size, profiles.map { it.id }.toSet().size)
    }

    @Test
    fun everyPremadeAgentUsesOnlyRegisteredAccessCapabilities() {
        val registered = AgentAccess.entries.toSet()

        AgentProfileRepository().list().forEach { profile ->
            assertTrue(profile.access.all { it in registered })
        }
    }
}
