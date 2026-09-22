package com.mrredhood.devforge.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceContextVersionTest {
    @Test
    fun versionIsStableUntilInvalidated() {
        val id = "test-workspace-" + System.nanoTime()
        val first = WorkspaceContextVersion.current(id)
        assertEquals(first, WorkspaceContextVersion.current(id))
        WorkspaceContextVersion.invalidate(id)
        assertTrue(WorkspaceContextVersion.current(id) > first)
    }

    @Test
    fun blankWorkspaceIdDoesNotCreateVersion() {
        WorkspaceContextVersion.invalidate("")
        val id = "test-workspace-" + System.nanoTime()
        assertEquals(1L, WorkspaceContextVersion.current(id))
    }
}
