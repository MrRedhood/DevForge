package com.mrredhood.devforge.core.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.agent.AgentTaskPlan
import com.mrredhood.devforge.core.agent.AgentTaskPlanCodec
import com.mrredhood.devforge.core.agent.AgentTaskStep
import com.mrredhood.devforge.core.agent.AgentToolId
import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.json.JSONObject

@Composable
fun AutomationCenterScreen(viewModel: AutomationViewModel = viewModel()) {
    var editing by remember { mutableStateOf<AutomationEntitySnapshot?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Automation", fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                        Text(
                            "Schedule work, react to repository changes or build completion, and keep every side effect behind the same agent approval boundary.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Button(onClick = { editing = AutomationEntitySnapshot.empty(viewModel.activeWorkspace?.id) }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("New")
                    }
                }
            }
        }

        viewModel.activeWorkspace?.let { workspace ->
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Active workspace", style = MaterialTheme.typography.labelLarge)
                        Text(workspace.name, fontWeight = FontWeight.Bold)
                        Text(workspace.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (viewModel.automations.isEmpty()) {
            item { EmptyAutomationCard { editing = AutomationEntitySnapshot.empty(viewModel.activeWorkspace?.id) } }
        } else {
            items(viewModel.automations, key = { it.automationId }) { automation ->
                AutomationRow(
                    automation = automation,
                    onEdit = { editing = AutomationEntitySnapshot.from(automation) },
                    onRun = { viewModel.runNow(automation) },
                    onToggle = { viewModel.setEnabled(automation, it) },
                    onDelete = { viewModel.delete(automation) },
                    onHistory = { viewModel.loadRuns(automation.automationId) },
                )
            }
        }

        viewModel.selectedRuns.takeIf { it.isNotEmpty() }?.let { runs ->
            item { AutomationRunHistory(runs) }
        }

        viewModel.message?.let { message ->
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    editing?.let { snapshot ->
        AutomationEditorDialog(
            snapshot = snapshot,
            activeWorkspaceId = viewModel.activeWorkspace?.id,
            saving = viewModel.isSaving,
            onDismiss = { editing = null },
            onSave = { draft ->
                viewModel.save(draft)
                editing = null
            },
        )
    }
}

@Composable
private fun EmptyAutomationCard(onCreate: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No automations yet", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Create a bounded workflow and choose exactly when it is allowed to run.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCreate) { Text("Create automation") }
        }
    }
}

@Composable
private fun AutomationRow(
    automation: com.mrredhood.devforge.core.storage.AutomationEntity,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
) {
    val enabled = automation.status == AutomationStatus.ENABLED.name
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(automation.name, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text(triggerSummary(automation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = enabled, onClick = {}, enabled = false, label = { Text(automation.status) })
                FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(automation.triggerType) })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onHistory) { Icon(Icons.Default.History, "View runs") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit automation") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete automation") }
                IconButton(onClick = onRun, enabled = enabled) { Icon(Icons.Default.PlayArrow, "Run now") }
            }
        }
    }
}

@Composable
private fun AutomationRunHistory(runs: List<com.mrredhood.devforge.core.storage.AutomationRunEntity>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent runs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            runs.take(12).forEach { run ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(run.status, Modifier.weight(0.35f), style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(0.65f)) {
                        Text(run.runId.take(12), style = MaterialTheme.typography.labelSmall)
                        run.errorMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationEditorDialog(
    snapshot: AutomationEntitySnapshot,
    activeWorkspaceId: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (AutomationDraft) -> Unit,
) {
    var name by remember(snapshot.automationId) { mutableStateOf(snapshot.name) }
    var workspaceId by remember(snapshot.automationId) { mutableStateOf(snapshot.workspaceId ?: activeWorkspaceId.orEmpty()) }
    var triggerType by remember(snapshot.automationId) { mutableStateOf(snapshot.triggerType) }
    var triggerConfig by remember(snapshot.automationId) { mutableStateOf(snapshot.triggerConfig) }
    var scopeText by remember(snapshot.automationId) { mutableStateOf(snapshot.scopePrefixes.joinToString(", ")) }
    var enabled by remember(snapshot.automationId) { mutableStateOf(snapshot.enabled) }
    var steps by remember(snapshot.automationId) { mutableStateOf(snapshot.steps) }

    fun selectTrigger(type: AutomationTriggerType) {
        triggerType = type
        triggerConfig = when (type) {
            AutomationTriggerType.SCHEDULE -> "daily@09:00"
            AutomationTriggerType.MANUAL -> ""
            AutomationTriggerType.REPOSITORY_CHANGE -> AutomationTriggerCodec.encode(AutomationTriggerConfig.RepositoryChange())
            AutomationTriggerType.BUILD_COMPLETION -> AutomationTriggerCodec.encode(AutomationTriggerConfig.BuildCompletion("", ""))
            AutomationTriggerType.CONDITION -> AutomationTriggerCodec.encode(AutomationTriggerConfig.Condition("repository_change", mapOf("branch" to "*")))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (snapshot.automationId == null) "Create automation" else "Edit automation") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Trigger", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AutomationTriggerType.entries.forEach { type ->
                        FilterChip(selected = type == triggerType, onClick = { selectTrigger(type) }, label = { Text(type.label()) })
                    }
                }

                when (triggerType) {
                    AutomationTriggerType.SCHEDULE -> OutlinedTextField(
                        value = triggerConfig,
                        onValueChange = { triggerConfig = it },
                        label = { Text("Schedule") },
                        placeholder = { Text("daily@09:00 or interval:30") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AutomationTriggerType.MANUAL -> Text("Runs only when you press Run now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AutomationTriggerType.REPOSITORY_CHANGE -> {
                        OutlinedTextField(value = workspaceId, onValueChange = { workspaceId = it }, label = { Text("Workspace ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        TriggerJsonField("Repository trigger JSON", triggerConfig) { triggerConfig = it }
                        Text("Use optional branch/path filters in the JSON editor.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AutomationTriggerType.BUILD_COMPLETION -> {
                        TriggerJsonField("Build completion JSON", triggerConfig) { triggerConfig = it }
                    }
                    AutomationTriggerType.CONDITION -> {
                        TriggerJsonField("Condition/event JSON", triggerConfig) { triggerConfig = it }
                    }
                }

                OutlinedTextField(
                    value = scopeText,
                    onValueChange = { scopeText = it },
                    label = { Text("Agent path scope") },
                    placeholder = { Text("src, tests") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Comma-separated prefixes. Leave empty for the workspace root; .git and traversal are always rejected.") },
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enabled", fontWeight = FontWeight.SemiBold)
                        Text("The scheduler and event monitor only consider enabled automations.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Actions", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        if (steps.size < 12) steps = steps + ActionStepDraft("READ_FILE", "Read workspace input", "{}")
                    }, enabled = steps.size < 12) { Text("Add step") }
                }
                steps.forEachIndexed { index, step ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Step ${index + 1}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                if (steps.size > 1) TextButton(onClick = { steps = steps.filterIndexed { stepIndex, _ -> stepIndex != index } }) { Text("Remove") }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AgentToolId.entries.forEach { tool ->
                                    FilterChip(selected = step.tool == tool.wireName, onClick = {
                                        steps = steps.mapIndexed { stepIndex, current -> if (stepIndex == index) current.copy(tool = tool.wireName) else current }
                                    }, label = { Text(tool.wireName) })
                                }
                            }
                            OutlinedTextField(
                                value = step.label,
                                onValueChange = { value -> steps = steps.mapIndexed { stepIndex, current -> if (stepIndex == index) current.copy(label = value) else current } },
                                label = { Text("Step label") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = step.arguments,
                                onValueChange = { value -> steps = steps.mapIndexed { stepIndex, current -> if (stepIndex == index) current.copy(arguments = value) else current } },
                                label = { Text("Tool arguments JSON") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                        }
                    }
                }
                Text("All action steps execute through the typed agent gateway; write/edit operations still enter the normal approval path.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val plan = AgentTaskPlan(
                        steps = steps.map { step ->
                            AgentTaskStep(
                                toolId = AgentToolId.valueOf(step.tool),
                                argumentsJson = step.arguments,
                                label = step.label,
                            )
                        },
                        pathScope = WorkspacePathScope(scopeText.split(',').map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("") }),
                    )
                    onSave(
                        AutomationDraft(
                            automationId = snapshot.automationId,
                            name = name,
                            workspaceId = workspaceId.takeIf { it.isNotBlank() },
                            triggerType = triggerType,
                            triggerConfig = triggerConfig,
                            actionGraph = AgentTaskPlanCodec.encode(plan),
                            enabled = enabled,
                        ),
                    )
                },
                enabled = !saving && name.isNotBlank() && steps.isNotEmpty(),
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TriggerJsonField(title: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(title) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
}

private fun triggerSummary(automation: com.mrredhood.devforge.core.storage.AutomationEntity): String = when (automation.triggerType) {
    AutomationTriggerType.SCHEDULE.name -> "Scheduled · ${automation.schedule.orEmpty()}"
    AutomationTriggerType.REPOSITORY_CHANGE.name -> "Repository change · ${automation.workspaceId ?: "workspace"}"
    AutomationTriggerType.BUILD_COMPLETION.name -> "Build completion · event filtered"
    AutomationTriggerType.CONDITION.name -> "Condition graph · event filtered"
    else -> "Manual trigger"
}

private fun AutomationTriggerType.label(): String = when (this) {
    AutomationTriggerType.REPOSITORY_CHANGE -> "Git change"
    AutomationTriggerType.BUILD_COMPLETION -> "Build done"
    AutomationTriggerType.CONDITION -> "Condition"
    AutomationTriggerType.SCHEDULE -> "Schedule"
    AutomationTriggerType.MANUAL -> "Manual"
}

private data class ActionStepDraft(
    val tool: String,
    val label: String,
    val arguments: String,
)

private data class AutomationEntitySnapshot(
    val automationId: String?,
    val name: String,
    val workspaceId: String?,
    val triggerType: AutomationTriggerType,
    val triggerConfig: String,
    val scopePrefixes: List<String>,
    val steps: List<ActionStepDraft>,
    val enabled: Boolean,
) {
    companion object {
        fun empty(workspaceId: String?): AutomationEntitySnapshot = AutomationEntitySnapshot(
            automationId = null,
            name = "",
            workspaceId = workspaceId,
            triggerType = AutomationTriggerType.SCHEDULE,
            triggerConfig = "daily@09:00",
            scopePrefixes = listOf(""),
            steps = listOf(ActionStepDraft("READ_FILE", "Read workspace input", "{}")),
            enabled = true,
        )

        fun from(entity: com.mrredhood.devforge.core.storage.AutomationEntity): AutomationEntitySnapshot {
            val decoded = runCatching { AgentTaskPlanCodec.decode(entity.actionGraph) }.getOrElse {
                AgentTaskPlan(emptyList(), WorkspacePathScope())
            }
            return AutomationEntitySnapshot(
                automationId = entity.automationId,
                name = entity.name,
                workspaceId = entity.workspaceId,
                triggerType = runCatching { AutomationTriggerType.valueOf(entity.triggerType) }.getOrDefault(AutomationTriggerType.MANUAL),
                triggerConfig = entity.schedule.orEmpty(),
                scopePrefixes = decoded.pathScope.canonicalPrefixes(),
                steps = decoded.steps.map { ActionStepDraft(it.toolId.wireName, it.label, it.argumentsJson) }.ifEmpty { listOf(ActionStepDraft("READ_FILE", "Read workspace input", "{}")) },
                enabled = entity.status == AutomationStatus.ENABLED.name,
            )
        }
    }
}
