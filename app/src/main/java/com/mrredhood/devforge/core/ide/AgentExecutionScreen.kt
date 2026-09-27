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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
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
fun AgentExecutionScreen(
    viewModel: AgentActivityViewModel = viewModel(),
) {
    val workspaceId = viewModel.workspaceId
    val audits = workspaceId?.let {
        DevForgeDatabase.get(viewModel.getApplication()).auditEventDao()
            .observeForWorkspace(it, 200)
            .collectAsState(initial = emptyList())
    }
    val auditEvents = audits?.value.orEmpty()
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTask = viewModel.tasks.firstOrNull { it.taskId == selectedTaskId }
        ?: viewModel.tasks.firstOrNull()

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI execution", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ExecutionSummary(viewModel.workspaceName, viewModel.tasks) }

            if (viewModel.tasks.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Text("No AI execution is active", fontWeight = FontWeight.Bold)
                            Text(
                                "When DevForge deploys an AI task, its plan, progress, tools, approvals and verification evidence will appear here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    Text("Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(viewModel.tasks, key = { it.taskId }) { task ->
                    ExecutionTaskCard(
                        task = task,
                        selected = task.taskId == selectedTask?.taskId,
                        onSelect = { selectedTaskId = task.taskId },
                    )
                }
                selectedTask?.let { task ->
                    item {
                        Text(
                            "Execution details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    item {
                        ExecutionDetailCard(
                            task = task,
                            auditEvents = AgentExecutionTimeline.forTask(auditEvents, task.taskId, 8),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionSummary(workspaceName: String?, tasks: List<AgentTaskEntity>) {
    val activeStatuses = setOf(
        AgentTaskStatus.QUEUED.name,
        AgentTaskStatus.PLANNING.name,
        AgentTaskStatus.RUNNING.name,
        AgentTaskStatus.WAITING_APPROVAL.name,
    )
    val active = tasks.count { it.status in activeStatuses }
    val completed = tasks.count { it.status == AgentTaskStatus.COMPLETED.name }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(workspaceName ?: "No workspace", fontWeight = FontWeight.Bold)
                    Text("AI-managed engineering execution", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Active " + active, active > 0)
                StatusChip("Completed " + completed, completed > 0)
                StatusChip("Observed " + tasks.size, true)
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, selected: Boolean) {
    FilterChip(selected = selected, onClick = {}, enabled = false, label = { Text(text) })
}

@Composable
private fun ExecutionTaskCard(
    task: AgentTaskEntity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val progress = if (task.stepCount <= 0) 0f else
        (task.currentStep.toFloat() / task.stepCount.toFloat()).coerceIn(0f, 1f)
    val status = task.status.uppercase()
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(status)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold)
                    Text(
                        status.replace('_', ' '),
                        color = statusColor(status),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    if (task.stepCount > 0) task.currentStep.toString() + "/" + task.stepCount else "—",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (task.stepCount > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                task.modelName?.takeIf { it.isNotBlank() }?.let {
                    FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(it.take(32)) })
                }
                task.lastToolId?.takeIf { it.isNotBlank() }?.let {
                    FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(it.take(32)) })
                }
            }
            task.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExecutionDetailCard(
    task: AgentTaskEntity,
    auditEvents: List<AuditEventEntity>,
) {
    val plan = runCatching { AgentTaskPlanCodec.decode(task.payload) }.getOrNull()
    val graph = AgentExecutionGraphBuilder.build(task, emptyList())
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(task.instruction.take(800), color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (plan != null) {
                Text("Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                plan.steps.forEachIndexed { index, step ->
                    val state = graph.nodes.firstOrNull { it.stepIndex == index }?.status ?: "queued"
                    val icon = when {
                        index < task.currentStep -> Icons.Default.CheckCircle
                        index == task.currentStep && task.status == AgentTaskStatus.WAITING_APPROVAL.name -> Icons.Default.Security
                        index == task.currentStep && task.status == AgentTaskStatus.FAILED.name -> Icons.Default.Error
                        index == task.currentStep -> Icons.Default.HourglassTop
                        else -> Icons.Default.Info
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = statusColor(state))
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                step.label.take(140),
                                fontWeight = if (index == task.currentStep) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                step.toolId.wireName + " · " + state.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (auditEvents.isNotEmpty()) {
                Text("Recent execution events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

            task.approvalId?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Waiting for approval: " + it.take(16) + "…", style = MaterialTheme.typography.bodySmall)
                }
            }

            task.result?.takeIf { it.isNotBlank() }?.let {
                Text("Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(it.take(2_000))
            }
        }
    }
}

@Composable
private fun StatusIcon(status: String) {
    val icon = when (status) {
        AgentTaskStatus.COMPLETED.name -> Icons.Default.CheckCircle
        AgentTaskStatus.FAILED.name -> Icons.Default.Error
        AgentTaskStatus.WAITING_APPROVAL.name -> Icons.Default.Security
        AgentTaskStatus.PAUSED.name -> Icons.Default.Warning
        AgentTaskStatus.RUNNING.name, AgentTaskStatus.PLANNING.name -> Icons.Default.HourglassTop
        else -> Icons.Default.Info
    }
    Icon(icon, contentDescription = null, tint = statusColor(status))
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    AgentTaskStatus.COMPLETED.name -> MaterialTheme.colorScheme.primary
    AgentTaskStatus.FAILED.name, AgentTaskStatus.CANCELLED.name -> MaterialTheme.colorScheme.error
    AgentTaskStatus.WAITING_APPROVAL.name -> MaterialTheme.colorScheme.tertiary
    AgentTaskStatus.PAUSED.name -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
