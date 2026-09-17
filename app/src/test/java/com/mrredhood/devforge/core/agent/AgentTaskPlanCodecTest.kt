package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskPlanCodecTest {
    @Test
    fun persistsExplicitPathScope() {
        val plan = AgentTaskPlan(
            steps = listOf(
                AgentTaskStep(
                    toolId = AgentToolId.READ_FILE,
                    argumentsJson = "{\"path\":\"src/Main.kt\"}",
                    label = "Inspect source",
                ),
            ),
            pathScope = WorkspacePathScope(listOf("src", "tests")),
        )

        val restored = AgentTaskPlanCodec.decode(AgentTaskPlanCodec.encode(plan))

        assertEquals(listOf("src", "tests"), restored.pathScope.canonicalPrefixes())
        assertEquals(plan.steps.single().argumentsJson, restored.steps.single().argumentsJson)
        assertTrue(restored.pathScope.allows("tests/Example.kt"))
    }

    @Test
    fun rejectsUnknownToolInPersistedPlan() {
        val invalid = "{\"version\":2,\"scope\":[\"src\"],\"steps\":[{\"tool\":\"shell\",\"arguments\":{},\"label\":\"bad\"}]}"
        assertThrows(IllegalArgumentException::class.java) {
            AgentTaskPlanCodec.decode(invalid)
        }
    }
}
