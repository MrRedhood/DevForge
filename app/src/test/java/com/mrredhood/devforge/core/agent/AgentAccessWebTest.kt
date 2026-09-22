package com.mrredhood.devforge.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAccessWebTest {
    @Test
    fun webToolsRequireWebAccess() {
        assertEquals(
            setOf(AgentAccess.WEB_ACCESS),
            AgentAccessRules.requiredFor(AgentToolId.WEB_SEARCH),
        )
        assertTrue(
            AgentAccessRules.canUse(
                AgentToolId.SCRAPE_URL,
                setOf(AgentAccess.WEB_ACCESS),
            ),
        )
    }

    @Test
    fun utilityToolsNeedNoExtraAccess() {
        assertTrue(AgentAccessRules.canUse(AgentToolId.CALCULATE, emptySet()))
        assertTrue(AgentAccessRules.canUse(AgentToolId.CURRENT_TIME, emptySet()))
    }

    @Test
    fun workspaceContextRequiresWorkspaceAccess() {
        assertEquals(
            setOf(AgentAccess.WORKSPACE_ACCESS),
            AgentAccessRules.requiredFor(AgentToolId.GET_WORKSPACE_CONTEXT),
        )
        assertTrue(
            AgentAccessRules.canUse(
                AgentToolId.GET_WORKSPACE_CONTEXT,
                setOf(AgentAccess.WORKSPACE_ACCESS),
            ),
        )
    }
}
