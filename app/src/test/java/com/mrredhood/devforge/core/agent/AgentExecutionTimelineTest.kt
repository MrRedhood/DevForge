package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.storage.AuditEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentExecutionTimelineTest {

    @Test
    fun timelineIncludesOnlyTaskScopedEventsInNewestOrder() {
        val taskId = "task-1"
        val events = listOf(
            event("agent:$taskId:step:1", "AGENT_TOOL_COMPLETED", 10),
            event("agent:other:step:1", "OTHER", 30),
            event("agent:$taskId", "AGENT_TASK_STARTED", 40),
            event("agent:$taskId:step:0", "AGENT_TOOL_COMPLETED", 20),
        )

        val timeline = AgentExecutionTimeline.forTask(events, taskId)

        assertEquals(
            listOf("AGENT_TASK_STARTED", "AGENT_TOOL_COMPLETED", "AGENT_TOOL_COMPLETED"),
            timeline.map { it.eventType },
        )
    }

    @Test
    fun timelineIsBounded() {
        val taskId = "task-2"
        val events = (1..20).map { event("agent:$taskId:step:$it", "EVENT_$it", it.toLong()) }

        assertEquals(AgentExecutionTimeline.MAX_EVENTS, AgentExecutionTimeline.forTask(events, taskId).size)
    }

    private fun event(actionId: String, type: String, timestamp: Long) = AuditEventEntity(
        eventId = actionId + timestamp,
        workspaceId = "workspace",
        actionId = actionId,
        capability = null,
        risk = null,
        eventType = type,
        summary = type,
        metadataJson = null,
        createdAtEpochMs = timestamp,
    )
}
