package com.mrredhood.devforge.core.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class DevForgeAiSkillsTest {
    @Test
    fun matchesAndroidAndTestingSkills() {
        val ids = DevForgeAiSkills.matched("Fix the Compose UI and add unit tests to the Android Gradle project").map { it.id }
        assertTrue("android" in ids)
        assertTrue("ui" in ids)
        assertTrue("testing" in ids)
    }

    @Test
    fun alwaysIncludesProjectRulesPolicy() {
        val prompt = DevForgeAiSkills.prompt("Rename a class")
        assertTrue(prompt.contains(".devforge/rules/"))
        assertTrue(prompt.contains("Build Center"))
    }
}