package com.mrredhood.devforge.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevForgeQualityTest {
    @Test
    fun operationLifecycleEndsInSuccess() {
        val id = DevForgeOperationCenter.start(
            DevForgeOperationType.BUILD,
            "Test build",
        )
        DevForgeOperationCenter.running(id, "Running", 45)
        DevForgeOperationCenter.succeed(id, "Done")

        val operation = DevForgeOperationCenter.snapshot().firstOrNull { it.id == id }
        assertTrue(operation != null)
        assertTrue(operation?.state == DevForgeOperationState.SUCCEEDED)
        assertTrue(operation?.progressPercent == 100)
    }

    @Test
    fun secretScanFlagsObviousCredentialPatterns() {
        val findings = DevForgeSecurityScanner.scan(
            listOf(
                "safe.kt" to "val answer = 42",
                "config.kt" to "val apiKey = \"sk-proj-example-token-value\"",
            ),
        )

        assertTrue(findings.any { it.path == "config.kt" })
        assertFalse(findings.any { it.path == "safe.kt" })
    }

    @Test
    fun performanceBudgetDetectsOversizedEditorState() {
        val snapshot = PerformanceBudgetSnapshot(
            openTabs = 25,
            dirtyTabs = 2,
            totalOpenContentChars = 100,
        )

        assertFalse(snapshot.withinBudget)
    }

    @Test
    fun releaseGateRequiresEveryCheck() {
        val gate = DevForgeReleaseQualityGate.evaluate(
            hasSuccessfulBuild = true,
            artifactsPresent = true,
            logsAvailable = true,
            noUnsavedEditorChanges = true,
            diagnosticsClear = false,
            configurationComplete = true,
        )

        assertFalse(gate.ready)
        assertTrue(gate.failed == 1)
    }
}
