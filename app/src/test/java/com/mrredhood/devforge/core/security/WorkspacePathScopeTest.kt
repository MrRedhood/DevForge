package com.mrredhood.devforge.core.security

import org.junit.Assert.assertFalse
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
        assertTrue(scope.canonicalPrefixes() == listOf("src", "tests/unit"))
    }
}
