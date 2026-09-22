package com.mrredhood.devforge.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevForgeToolCatalogTest {
    @Test
    fun exposesTwentyUserTools() {
        assertEquals(22, DevForgeToolCatalog.entries.size)
        assertEquals(22, DevForgeToolCatalog.userToolIds.size)
    }

    @Test
    fun toolIdsAreUniqueAndHaveDescriptions() {
        val ids = DevForgeToolCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(DevForgeToolCatalog.entries.all { it.title.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun destructiveDeleteToolDefaultsOffButApprovalProtected() {
        assertFalse(DevForgeToolCatalog.defaultEnabledIds.contains(AgentToolId.DELETE_PATH))
        assertTrue(DevForgeToolCatalog.isUserTool(AgentToolId.WEB_SEARCH))
    }
    @Test
    fun terminalAndGitLogToolsAreEnabledByDefault() {
        assertTrue(DevForgeToolCatalog.defaultEnabledIds.contains(AgentToolId.RUN_COMMAND))
        assertTrue(DevForgeToolCatalog.defaultEnabledIds.contains(AgentToolId.GET_GIT_LOG))
    }

}

