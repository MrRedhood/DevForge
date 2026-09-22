package com.mrredhood.devforge.core.power

import com.mrredhood.devforge.core.ai.workflow.AiActivity
import com.mrredhood.devforge.core.ai.workflow.AiActivityKind
import com.mrredhood.devforge.core.ai.workflow.AiActivityStatus
import com.mrredhood.devforge.core.ai.workflow.AiWorkflowPhase
import com.mrredhood.devforge.core.ai.workflow.AiWorkflowSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringPowerTest {
    @Test
    fun resourceRouterKeepsSimpleRequestsFast() {
        assertEquals(AiResourceRouter.RouteClass.FAST, AiResourceRouter.classify("rename this variable"))
        assertEquals(AiResourceRouter.RouteClass.STANDARD, AiResourceRouter.classify("fix this build"))
        assertEquals(AiResourceRouter.RouteClass.DEEP, AiResourceRouter.classify("perform a security audit"))
    }

    @Test
    fun missionGraphReflectsCompletedPhases() {
        val workflow = AiWorkflowSnapshot(
            workflowId = "test",
            workspaceId = "workspace",
            workspaceName = "Demo",
            request = "fix",
            phase = AiWorkflowPhase.EXECUTE,
            status = AiWorkflowSnapshot.Status.RUNNING,
            currentStep = "editing",
            activities = listOf(
                AiActivity("1", AiActivityKind.WRITE, AiActivityStatus.COMPLETED, "write", paths = listOf("app/A.kt"), createdAtEpochMs = 1L),
            ),
            verification = null,
            summary = null,
            startedAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
        )
        val graph = EngineeringMissionGraph.from(workflow)
        assertEquals(EngineeringMissionNode.State.PASSED, graph.first { it.id == "PLAN" }.state)
        assertEquals(EngineeringMissionNode.State.ACTIVE, graph.first { it.id == "EXECUTE" }.state)
        assertTrue(graph.first { it.id == "VERIFY" }.state == EngineeringMissionNode.State.WAITING)
    }
}
