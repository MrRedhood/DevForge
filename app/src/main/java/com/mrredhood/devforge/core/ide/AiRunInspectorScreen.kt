package com.mrredhood.devforge.core.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.agent.AgentActivityViewModel
import com.mrredhood.devforge.core.agent.AgentExecutionGraphBuilder
import com.mrredhood.devforge.core.agent.AgentExecutionTimeline
import com.mrredhood.devforge.core.agent.AgentTaskPlanCodec
import com.mrredhood.devforge.core.agent.AgentTaskStatus
import com.mrredhood.devforge.core.storage.AgentTaskEntity
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRunInspectorScreen(
    onBack: () -> Unit,
    viewModel: AgentActivityViewModel = viewModel(),
) {
    val workspaceId = viewModel.workspaceId
    val audits = workspaceId?.let {
        DevForgeDatabase.get(viewModel.getApplication()).auditEventDao()
            .observeForWorkspace(it, 250)
            .collectAsState(initial = emptyList())
    }
    val auditEvents = audits?.value.orEmpty()
    var filter by rememberSaveable { mutableStateOf("All") }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = viewModel.tasks.filter { task ->
        when (filter) {
            "Active" -> task.status in setOf(
                AgentTaskStatus.QUEUED.name,
                AgentTaskStatus.PLANNING.name,
                AgentTaskStatus.RUNNING.name,
                AgentTaskStatus.WAITING_APPROVAL.name,
            )
            "Completed" -> task.status == AgentTaskStatus.COMPLETED.name
            "Failed" -> task.status == AgentTaskStatus.FAILED.name
            "Paused" -> task.status == AgentTaskStatus.PAUSED.name
            else -> true
        }
    }
    val selected = filtered.firstOrNull { it.taskId == selectedTaskId } ?: filtered.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI run inspector", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Refresh, contentDescription = "Live state")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(viewModel.workspaceName ?: "No workspace", fontWeight = FontWeight.Bold)
                                Text(
                                    "Historical and live AI execution inspection",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "Inspect persisted task state, plan steps, model context, approvals, errors and execution events without adding user controls to AI-managed agents.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("All", "Active", "Completed", "Failed", "Paused").forEach { value ->
                        FilterChip(
                            selected = filter == value,
                            onClick = { filter = value },
                            label = { Text(value) },
                        )
                    }
                }
            }

            item {
                Text("Runs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (filtered.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text(
                            if (viewModel.tasks.isEmpty()) {
                                "No persisted AI runs exist for the active workspace."
                            } else {
                                "No AI runs match the current filter."
                            },
                            Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(filtered, key = { it.taskId }) { task ->
                    InspectorRunCard(
                        task = task,
                        selected = task.taskId == selected?.taskId,
                        onSelect = { selectedTaskId = task.taskId },
                    )
                }

                selected?.let { task ->
                    item {
                        Text("Run details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    item {
                        InspectorDetails(
                            task = task,
                            auditEvents = AgentExecutionTimeline.forTask(auditEvents, task.taskId, 12),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorRunCard(
    task: AgentTaskEntity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val icon = when (task.status) {
        AgentTaskStatus.COMPLETED.name -> Icons.Default.Info
        AgentTaskStatus.FAILED.name, AgentTaskStatus.CANCELLED.name -> Icons.Default.Error
        AgentTaskStatus.WAITING_APPROVAL.name -> Icons.Default.Security
        AgentTaskStatus.PAUSED.name -> Icons.Default.Warning
        AgentTaskStatus.RUNNING.name, AgentTaskStatus.PLANNING.name -> Icons.Default.HourglassTop
        else -> Icons.Default.Info
    }
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                Text(
                    task.status.replace('_', ' '),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Step " + task.currentStep + "/" + task.stepCount +
                        " · " + (task.lastToolId ?: "no tool yet"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InspectorDetails(
    task: AgentTaskEntity,
    auditEvents: List<AuditEventEntity>,
) {
    val plan = runCatching { AgentTaskPlanCodec.decode(task.payload) }.getOrNull()
    val graph = AgentExecutionGraphBuilder.build(task, emptyList())
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(task.instruction.take(1_200), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Run metadata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            InspectorMeta("Task ID", task.taskId)
            InspectorMeta("Provider", task.modelProviderId ?: "Not recorded")
            InspectorMeta("Model", task.modelName ?: task.modelId ?: "Not recorded")
            InspectorMeta("Created", formatTimestamp(task.createdAtEpochMs))
            InspectorMeta("Started", task.startedAtEpochMs?.let(::formatTimestamp) ?: "Not started")
            InspectorMeta("Updated", formatTimestamp(task.updatedAtEpochMs))
            InspectorMeta("Completed", task.completedAtEpochMs?.let(::formatTimestamp) ?: "Not completed")

            task.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text("Failure / recovery note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (plan != null) {
                Text("Plan trace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                plan.steps.forEachIndexed { index, step ->
                    val state = graph.nodes.firstOrNull { it.stepIndex == index }?.status ?: "queued"
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (index + 1).toString(),
                            modifier = Modifier.size(28.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(step.label.take(160), fontWeight = if (index == task.currentStep) FontWeight.SemiBold else FontWeight.Normal)
                            Text(
                                step.toolId.wireName + " · " + state.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            task.approvalId?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Approval linked: " + it.take(18) + "…")
                }
            }

            if (auditEvents.isNotEmpty()) {
                Text("Execution timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                auditEvents.forEach { event ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.createdAtEpochMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp, top = 2.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(event.summary)
                            Text(
                                event.eventType.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            task.result?.takeIf { it.isNotBlank() }?.let {
                Text("Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(it.take(3_000))
            }
        }
    }
}

@Composable
private fun InspectorMeta(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.size(width = 82.dp, height = 28.dp), style = MaterialTheme.typography.labelMedium)
        Text(value.take(240), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatTimestamp(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
