package com.mrredhood.devforge.core.automation

import com.mrredhood.devforge.core.storage.AutomationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTriggerCodecTest {
    @Test
    fun repositoryTriggerMatchesWorkspaceBranchAndPath() {
        val automation = AutomationEntity(
            automationId = "a",
            workspaceId = "workspace-1",
            name = "tests",
            status = AutomationStatus.ENABLED.name,
            triggerType = AutomationTriggerType.REPOSITORY_CHANGE.name,
            schedule = AutomationTriggerCodec.encode(
                AutomationTriggerConfig.RepositoryChange("main", listOf("src")),
            ),
            actionGraph = "{}",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        val matching = AutomationEvent.RepositoryChanged("workspace-1", "main", listOf("src/App.kt"), "fingerprint")
        val wrongWorkspace = matching.copy(workspaceId = "workspace-2")
        val wrongBranch = matching.copy(branch = "dev")
        val wrongPath = matching.copy(changedPaths = listOf("docs/readme.md"))

        assertTrue(AutomationTriggerCodec.matches(automation, matching))
        assertFalse(AutomationTriggerCodec.matches(automation, wrongWorkspace))
        assertFalse(AutomationTriggerCodec.matches(automation, wrongBranch))
        assertFalse(AutomationTriggerCodec.matches(automation, wrongPath))
    }

    @Test
    fun buildTriggerMatchesConfiguredFilters() {
        val automation = AutomationEntity(
            automationId = "a",
            workspaceId = null,
            name = "build followup",
            status = AutomationStatus.ENABLED.name,
            triggerType = AutomationTriggerType.BUILD_COMPLETION.name,
            schedule = AutomationTriggerCodec.encode(
                AutomationTriggerConfig.BuildCompletion("owner", "repo", "main", "success", "DebugApk"),
            ),
            actionGraph = "{}",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        val event = AutomationEvent.BuildCompleted("owner", "repo", "main", 42L, "success", "DebugApk")
        assertTrue(AutomationTriggerCodec.matches(automation, event))
        assertFalse(AutomationTriggerCodec.matches(automation, event.copy(runId = 43L, conclusion = "failure")))
    }

    @Test
    fun conditionTriggerSupportsWildcardAndEventValues() {
        val automation = AutomationEntity(
            automationId = "a",
            workspaceId = "workspace-1",
            name = "condition",
            status = AutomationStatus.ENABLED.name,
            triggerType = AutomationTriggerType.CONDITION.name,
            schedule = AutomationTriggerCodec.encode(
                AutomationTriggerConfig.Condition("repository_change", mapOf("branch" to "main", "workspaceId" to "*")),
            ),
            actionGraph = "{}",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        assertTrue(AutomationTriggerCodec.matches(automation, AutomationEvent.RepositoryChanged("workspace-1", "main", emptyList(), "f")))
        assertFalse(AutomationTriggerCodec.matches(automation, AutomationEvent.RepositoryChanged("workspace-1", "dev", emptyList(), "f")))
    }

    @Test
    fun fingerprintChangesWhenRepositoryStateDescriptorsChange() {
        val first = AutomationTriggerCodec.eventFingerprint("workspace", "main", listOf("head:aaa", "src/App.kt:Modified"))
        val same = AutomationTriggerCodec.eventFingerprint("workspace", "main", listOf("src/App.kt:Modified", "head:aaa"))
        val changed = AutomationTriggerCodec.eventFingerprint("workspace", "main", listOf("head:bbb", "src/App.kt:Modified"))

        assertEquals(first, same)
        assertFalse(first == changed)
    }
}
