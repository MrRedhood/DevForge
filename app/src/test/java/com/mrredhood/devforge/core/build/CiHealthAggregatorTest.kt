package com.mrredhood.devforge.core.build

import com.mrredhood.devforge.core.github.GitHubWorkflowRun
import org.junit.Assert.assertEquals
import org.junit.Test

class CiHealthAggregatorTest {
    private fun run(status: String, conclusion: String?) = GitHubWorkflowRun(
        id = 1L,
        runNumber = 1L,
        name = "Android CI",
        status = status,
        conclusion = conclusion,
        branch = "main",
        event = "push",
        workflowFile = "android.yml",
        htmlUrl = null,
        createdAt = null,
    )

    @Test
    fun aggregatesHealthyAndRunningStates() {
        assertEquals(CiHealthState.HEALTHY, CiHealthAggregator.summarize(listOf(run("completed", "success"))).state)
        assertEquals(CiHealthState.RUNNING, CiHealthAggregator.summarize(listOf(run("in_progress", null))).state)
    }

    @Test
    fun aggregatesMixedFailures() {
        val snapshot = CiHealthAggregator.summarize(
            listOf(run("completed", "success"), run("completed", "failure")),
        )
        assertEquals(CiHealthState.MIXED, snapshot.state)
        assertEquals(1, snapshot.successful)
        assertEquals(1, snapshot.failed)
    }
}
