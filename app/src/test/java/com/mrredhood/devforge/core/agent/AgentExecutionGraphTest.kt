package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.AgentHandoffEntity
import com.mrredhood.devforge.core.storage.AgentTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentExecutionGraphTest {
    @Test
    fun buildsSequenceAndHandoffEdges() {
        val task = AgentTaskEntity(
            taskId = "task-1",
            workspaceId = "ws",
            title = "Task",
            instruction = "inspect",
            status = AgentTaskStatus.RUNNING.name,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
            payload = AgentTaskPlanCodec.encode(
                AgentTaskPlan(
                    steps = listOf(
                        AgentTaskStep(AgentToolId.LIST_FILES, "{}", "List"),
                        AgentTaskStep(AgentToolId.READ_FILE, "{\"path\":\"Main.kt\"}", "Read"),
                    ),
                    pathScope = WorkspacePathScope(),
                ),
            ),
            errorMessage = null,
            currentStep = 1,
            stepCount = 2,
        )
        val handoff = AgentHandoffEntity(
            handoffId = "h1",
            workspaceId = "ws",
            fromTaskId = "task-1",
            toTaskId = "task-2",
            title = "handoff",
            summary = "continue",
            contextJson = "{}",
            status = AgentHandoffStatus.PENDING.name,
            createdAtEpochMs = 2,
            claimedAtEpochMs = null,
            claimedByTaskId = null,
            completedAtEpochMs = null,
        )
        val graph = AgentExecutionGraphBuilder.build(task, listOf(handoff))
        assertEquals(2, graph.nodes.count { it.stepIndex != null })
        assertTrue(graph.edges.any { it.kind == "sequence" })
        assertTrue(graph.edges.any { it.kind == "handoff" })
    }
}
