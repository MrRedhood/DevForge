package com.mrredhood.devforge.core.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.ai.AIProvider
import com.mrredhood.devforge.core.storage.AgentTaskEntity

@Composable
fun AgentCenterScreen(viewModel: AgentCenterViewModel = viewModel()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Agents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Run multiple independent coding agents in the same workspace. Each agent keeps its own model, scope and controls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Assign agent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(viewModel.workspaceName ?: "Choose a workspace first", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = viewModel.title, onValueChange = { viewModel.title = it.take(120) }, modifier = Modifier.fillMaxWidth(), label = { Text("Agent name") }, singleLine = true)
                    OutlinedTextField(value = viewModel.instruction, onValueChange = { viewModel.instruction = it.take(60_000) }, modifier = Modifier.fillMaxWidth(), label = { Text("What should this agent do?") }, minLines = 4)
                    Text("Model", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AIProvider.entries.forEach { provider ->
                            FilterChip(selected = viewModel.provider == provider, onClick = { viewModel.selectProvider(provider) }, label = { Text(provider.displayName) })
                        }
                    }
                    OutlinedTextField(value = viewModel.modelId, onValueChange = viewModel::updateModelId, modifier = Modifier.fillMaxWidth(), label = { Text("Model ID") }, placeholder = { Text("Use a provider model ID") }, singleLine = true)
                    OutlinedTextField(value = viewModel.scopeText, onValueChange = { viewModel.scopeText = it.take(1_500) }, modifier = Modifier.fillMaxWidth(), label = { Text("Path scope (optional)") }, placeholder = { Text("src, tests/unit") }, singleLine = true)
                    Text("Up to 4 agents execute concurrently in one workspace. Additional agents remain queued. Holding one agent does not hold the others.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = viewModel::assign, enabled = !viewModel.assigning && viewModel.workspaceId != null && viewModel.instruction.isNotBlank() && viewModel.modelId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (viewModel.assigning) "Planning agent…" else "Assign & start") }
                    viewModel.message?.let {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Shared coordination", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        viewModel.sharedMemory.size.toString() + " shared notes · " +
                            viewModel.handoffs.count { it.status != AgentHandoffStatus.COMPLETED.name }.toString() + " active handoffs · " +
                            viewModel.fileLeases.size.toString() + " file locks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (viewModel.sharedMemory.isEmpty()) {
                        Text("No shared memory has been published yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Recent memory", fontWeight = FontWeight.Bold)
                        viewModel.sharedMemory.take(5).forEach { memory ->
                            Column {
                                Text(memory.key, fontWeight = FontWeight.SemiBold)
                                Text(memory.content.take(400), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (viewModel.handoffs.isNotEmpty()) {
                        Text("Handoffs", fontWeight = FontWeight.Bold)
                        viewModel.handoffs.take(5).forEach { handoff ->
                            Text(
                                handoff.title + " · " + handoff.status.replace("_", " "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(handoff.summary.take(400), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (viewModel.fileLeases.isNotEmpty()) {
                        Text("Active file locks", fontWeight = FontWeight.Bold)
                        viewModel.fileLeases.take(5).forEach { lease ->
                            Text(
                                lease.path + " · agent " + lease.taskId.take(8),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column {
                Text("Workspace agents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(viewModel.tasks.size.toString() + " persistent agent tasks", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (viewModel.tasks.isEmpty()) {
            item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Text("No agents assigned yet.", Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
            items(viewModel.tasks, key = { it.taskId }) { task -> AgentTaskCard(task, viewModel, viewModel.auditEvents) }
        }
    }
}

@Composable
private fun AgentTaskCard(task: AgentTaskEntity, viewModel: AgentCenterViewModel, auditEvents: List<com.mrredhood.devforge.core.storage.AuditEventEntity>) {
    val status = runCatching { AgentTaskStatus.valueOf(task.status) }.getOrDefault(AgentTaskStatus.FAILED)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(status.name.replace("_", " "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(task.currentStep.coerceAtMost(task.stepCount).toString() + "/" + task.stepCount, style = MaterialTheme.typography.labelMedium)
            }
            Text("Model: " + (task.modelProviderId ?: "unknown") + " / " + (task.modelName ?: task.modelId ?: "unknown"), style = MaterialTheme.typography.bodySmall)
            Text("Scope: " + scopeSummary(task), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            task.lastToolId?.let { Text("Current tool: " + it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            task.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            val timeline = auditEvents.asSequence()
                .filter { it.actionId == "agent:" + task.taskId || it.actionId?.startsWith("agent:" + task.taskId + ":") == true }
                .take(8)
                .toList()
            if (timeline.isNotEmpty()) {
                Text("Execution timeline", fontWeight = FontWeight.Bold)
                timeline.forEach { event ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            event.eventType.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(140.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(event.summary.take(280), style = MaterialTheme.typography.bodySmall)
                            Text(
                                formatTimelineTime(event.createdAtEpochMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    AgentTaskStatus.RUNNING, AgentTaskStatus.QUEUED, AgentTaskStatus.PLANNING -> OutlinedButton(onClick = { viewModel.pause(task.taskId) }, modifier = Modifier.weight(1f)) { Text("Hold") }
                    AgentTaskStatus.PAUSED -> Button(onClick = { viewModel.resume(task.taskId) }, modifier = Modifier.weight(1f)) { Text("Resume") }
                    AgentTaskStatus.WAITING_APPROVAL -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Approval needed") }
                    else -> Spacer(Modifier.weight(1f))
                }
                if (status !in setOf(AgentTaskStatus.COMPLETED, AgentTaskStatus.FAILED, AgentTaskStatus.CANCELLED)) {
                    TextButton(onClick = { viewModel.cancel(task.taskId) }, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
            }
        }
    }
}

private fun scopeSummary(task: AgentTaskEntity): String = runCatching {
    AgentTaskPlanCodec.decode(task.payload).pathScope.canonicalPrefixes().joinToString(", ").ifBlank { "workspace root" }
}.getOrDefault("invalid scope")


private fun formatTimelineTime(epochMs: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(epochMs))
