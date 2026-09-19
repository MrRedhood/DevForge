package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentWorkspacePathTest {
    @Test
    fun stripsWorkspaceFolderPrefix() {
        val scope = WorkspacePathScope()
        assertEquals(
            "src/Main.kt",
            AgentWorkspacePath.canonicalize("Nexus/src/Main.kt", "Nexus", scope),
        )
    }

    @Test
    fun stripsWorkspaceFolderPrefixCaseInsensitively() {
        val scope = WorkspacePathScope()
        assertEquals(
            "his.py",
            AgentWorkspacePath.canonicalize("nexus/his.py", "Nexus", scope),
        )
    }

    @Test
    fun keepsWorkspaceRelativePathsUnchanged() {
        val scope = WorkspacePathScope()
        assertEquals(
            "src/Main.kt",
            AgentWorkspacePath.canonicalize("src/Main.kt", "Nexus", scope),
        )
    }

    @Test
    fun preservesFilenameExtensions() {
        val scope = WorkspacePathScope()
        assertEquals(
            "his.lua",
            AgentWorkspacePath.canonicalize("his.lua", "Nexus", scope),
        )
    }

    @Test
    fun rejectsTraversal() {
        val scope = WorkspacePathScope()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AgentWorkspacePath.canonicalize("Nexus/../secret.txt", "Nexus", scope)
        }
    }
    @Test
    fun doesNotStripARealWorkspaceRelativePathThatExists() {
        val scope = WorkspacePathScope()
        assertEquals(
            "Nexus/file.kt",
            AgentWorkspacePath.canonicalize(
                rawPath = "Nexus/file.kt",
                workspaceName = "Nexus",
                scope = scope,
                directPathExists = true,
            ),
        )
    }

}
