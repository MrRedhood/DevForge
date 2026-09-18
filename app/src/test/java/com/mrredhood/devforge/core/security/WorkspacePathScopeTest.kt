package com.mrredhood.devforge.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePathScopeTest {
    @Test
    fun rejectsTraversalAndGitMetadata() {
        val scope = WorkspacePathScope(listOf("src"))
        assertFalse(scope.allows("src/../build.gradle"))
        assertFalse(scope.allows("src/.git/config"))
        assertFalse(scope.allows("README.md"))
    }

    @Test
    fun allowsOnlyRequestedPrefix() {
        val scope = WorkspacePathScope(listOf("src", "tests/unit"))
        assertTrue(scope.allows("src/Main.kt"))
        assertTrue(scope.allows("tests/unit/Example.kt"))
        assertFalse(scope.allows("tests/integration/Example.kt"))
    }

    @Test
    fun canonicalPrefixesAreStable() {
        val scope = WorkspacePathScope(listOf("src/", "src", "tests/unit"))
        assertEquals(listOf("src", "tests/unit"), scope.canonicalPrefixes())
    }

    @Test
    fun requireAllowedReturnsCanonicalPath() {
        val scope = WorkspacePathScope(listOf("src"))
        assertEquals("src/Main.kt", scope.requireAllowed("/src/Main.kt/"))
        assertThrows(IllegalArgumentException::class.java) {
            scope.requireAllowed("docs/readme.md")
        }
    }

    @Test
    fun rejectsDepthAndPrefixCountLimits() {
        val tooDeep = (1..(WorkspacePathScope.MAX_PATH_DEPTH + 1)).joinToString("/") { "x" }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspacePathScope.normalize(tooDeep)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspacePathScope(List(WorkspacePathScope.MAX_PREFIXES + 1) { "src$it" })
        }
    }
}
