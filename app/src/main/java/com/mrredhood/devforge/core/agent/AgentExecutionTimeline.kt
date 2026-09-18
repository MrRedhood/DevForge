package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.storage.AuditEventEntity

object AgentExecutionTimeline {
    const val MAX_EVENTS = 8

    fun forTask(
        events: List<AuditEventEntity>,
        taskId: String,
        limit: Int = MAX_EVENTS,
    ): List<AuditEventEntity> {
        require(taskId.isNotBlank()) { "Task ID is required." }
        val safeLimit = limit.coerceIn(1, MAX_EVENTS)
        val prefix = "agent:$taskId"
        return events.asSequence()
            .filter { event ->
                event.actionId == prefix || event.actionId?.startsWith("$prefix:") == true
            }
            .sortedByDescending { it.createdAtEpochMs }
            .take(safeLimit)
            .toList()
    }
}
