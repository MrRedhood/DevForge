package com.mrredhood.devforge.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAccessTest {
    @Test
    fun fileToolsRequireWorkspaceAndFileAccess() {
        val required = AgentAccessRules.requiredFor(AgentToolId.READ_FILE)
        assertTrue(AgentAccess.WORKSPACE_ACCESS in required)
        assertTrue(AgentAccess.FILE_ACCESS in required)
        assertFalse(AgentAccess.COORDINATION_ACCESS in required)
    }

    @Test
    fun coordinationToolsRequireCoordinationAccess() {
        val required = AgentAccessRules.requiredFor(AgentToolId.CREATE_HANDOFF)
        assertEquals(
            setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.COORDINATION_ACCESS),
            required,
        )
    }

    @Test
    fun relevantContextRequiresWorkspaceAccess() {
        assertEquals(
            setOf(AgentAccess.WORKSPACE_ACCESS),
            AgentAccessRules.requiredFor(AgentToolId.RETRIEVE_RELEVANT_CONTEXT),
        )
    }

    @Test
    fun disabledAccessPreventsToolUse() {
        assertFalse(
            AgentAccessRules.canUse(
                AgentToolId.PATCH_FILE,
                setOf(AgentAccess.WORKSPACE_ACCESS),
            ),
        )
        assertTrue(
            AgentAccessRules.canUse(
                AgentToolId.PATCH_FILE,
                setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.FILE_ACCESS),
            ),
        )
    }

    @Test
    fun accessEncodingRoundTrips() {
        val source = setOf(
            AgentAccess.WORKSPACE_ACCESS,
            AgentAccess.FILE_ACCESS,
            AgentAccess.COORDINATION_ACCESS,
        )
        assertEquals(source, AgentAccess.decode(AgentAccess.encode(source)))
    }
    @Test
    fun malformedAccessDoesNotGrantPermissions() {
        assertTrue(AgentAccess.decode("not-json").isEmpty())
        assertTrue(AgentAccess.decode("[\"UNKNOWN\"]").isEmpty())
    }
    @Test
    fun terminalToolRequiresTerminalAccess() {
        val required = AgentAccessRules.requiredFor(AgentToolId.RUN_COMMAND)
        assertTrue(AgentAccess.WORKSPACE_ACCESS in required)
        assertTrue(AgentAccess.TERMINAL_ACCESS in required)
    }

    @Test
    fun gitLogToolRequiresGitAccess() {
        val required = AgentAccessRules.requiredFor(AgentToolId.GET_GIT_LOG)
        assertEquals(
            setOf(AgentAccess.WORKSPACE_ACCESS, AgentAccess.GIT_ACCESS),
            required,
        )
    }

}

