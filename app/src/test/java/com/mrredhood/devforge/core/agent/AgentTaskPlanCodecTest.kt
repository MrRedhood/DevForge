package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.json.JSONObject
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

    @Test
    fun enforcesStepAndArgumentBounds() {
        val tooManySteps = List(AgentTaskPlan.MAX_STEPS + 1) {
            AgentTaskStep(AgentToolId.READ_FILE, "{}", "step")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentTaskPlan(tooManySteps)
        }

        val oversizedArguments = JSONObject()
            .put("path", "x".repeat(AgentTaskPlanCodec.MAX_TOOL_ARGUMENT_BYTES))
            .toString()
        val invalid = JSONObject()
            .put("version", 2)
            .put("scope", org.json.JSONArray())
            .put(
                "steps",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("tool", "read_file")
                        .put("arguments", oversizedArguments)
                        .put("label", "large"),
                ),
            )
            .toString()

        assertThrows(IllegalArgumentException::class.java) {
            AgentTaskPlanCodec.decode(invalid)
        }
    }

    @Test
    fun acceptsLegacyVersionWithoutScope() {
        val legacy = "{\"version\":1,\"steps\":[{\"tool\":\"list_files\",\"arguments\":\"{}\",\"label\":\"List\"}]}"
        val decoded = AgentTaskPlanCodec.decode(legacy)
        assertEquals(emptyList<String>(), decoded.pathScope.canonicalPrefixes())
        assertEquals(AgentToolId.LIST_FILES, decoded.steps.single().toolId)
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentTaskPlanCodec.decode("{\"version\":99,\"steps\":[]}")
        }
    }
}
