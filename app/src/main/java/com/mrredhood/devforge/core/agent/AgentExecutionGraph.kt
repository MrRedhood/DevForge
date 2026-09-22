package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.storage.AgentHandoffEntity
import com.mrredhood.devforge.core.storage.AgentTaskEntity
import org.json.JSONObject

data class AgentGraphNode(
    val id: String,
    val title: String,
    val status: String,
    val stepIndex: Int? = null,
    val tool: String? = null,
)

data class AgentGraphEdge(
    val from: String,
    val to: String,
    val kind: String,
)

data class AgentExecutionGraph(
    val nodes: List<AgentGraphNode>,
    val edges: List<AgentGraphEdge>,
)

object AgentExecutionGraphBuilder {
    fun build(task: AgentTaskEntity, handoffs: List<AgentHandoffEntity>): AgentExecutionGraph {
        val nodes = mutableListOf<AgentGraphNode>()
        val edges = mutableListOf<AgentGraphEdge>()
        val plan = runCatching { AgentTaskPlanCodec.decode(task.payload) }.getOrNull()
        plan?.steps?.forEachIndexed { index, step ->
            val id = task.taskId + ":step:" + index
            nodes += AgentGraphNode(
                id = id,
                title = step.label.take(120),
                status = if (index < task.currentStep) "completed" else if (index == task.currentStep) task.status else "queued",
                stepIndex = index,
                tool = step.toolId.wireName,
            )
            if (index > 0) edges += AgentGraphEdge(task.taskId + ":step:" + (index - 1), id, "sequence")
        }
        handoffs.filter { it.fromTaskId == task.taskId || it.toTaskId == task.taskId }.forEach { handoff ->
            val from = handoff.fromTaskId
            val to = handoff.toTaskId ?: ("handoff:" + handoff.handoffId)
            if (from != to) edges += AgentGraphEdge(from, to, "handoff")
        }
        if (nodes.isEmpty()) {
            nodes += AgentGraphNode(task.taskId, task.title.take(120), task.status)
        }
        return AgentExecutionGraph(nodes.take(MAX_NODES), edges.take(MAX_EDGES))
    }

    fun compactJson(graph: AgentExecutionGraph): String {
        val root = JSONObject()
        root.put("nodes", graph.nodes.map { it.id to it.title + ":" + it.status }.toMap())
        root.put("edges", graph.edges.map { it.from + "->" + it.to + ":" + it.kind })
        return root.toString()
    }

    private const val MAX_NODES = 24
    private const val MAX_EDGES = 48
}
