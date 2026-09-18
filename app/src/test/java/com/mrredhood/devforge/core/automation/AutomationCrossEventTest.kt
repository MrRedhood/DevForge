package com.mrredhood.devforge.core.automation

import com.mrredhood.devforge.core.storage.AutomationEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationCrossEventTest {
    @Test
    fun wildcardConditionCanMatchRepositoryAndBuildEvents() {
        val automation = AutomationEntity(
            automationId = "automation",
            workspaceId = "workspace",
            name = "Cross event",
            status = AutomationStatus.ENABLED.name,
            triggerType = AutomationTriggerType.CONDITION.name,
            schedule = """{"event":"any","events":["repository_change","build_completion"],"conditions":{"branch":"main"}}""",
            actionGraph = "{}",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val repo = AutomationEvent.RepositoryChanged("workspace", "main", listOf("src/App.kt"), "fingerprint")
        val build = AutomationEvent.BuildCompleted("owner", "repo", "main", 42L, "success", "debug_apk")
        assertTrue(AutomationTriggerCodec.matches(automation, repo))
        assertTrue(AutomationTriggerCodec.matches(automation, build))
    }
}
