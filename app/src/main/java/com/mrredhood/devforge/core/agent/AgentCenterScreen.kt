package com.mrredhood.devforge.core.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.AIProvider
import com.mrredhood.devforge.core.storage.AgentTaskEntity

@Composable
fun AgentCenterScreen(viewModel: AgentCenterViewModel = viewModel()) {
    val firstProfile = viewModel.profiles.firstOrNull()
    var selectedProfileId by remember(viewModel.profiles) { mutableStateOf(firstProfile?.id.orEmpty()) }
    var provider by remember { mutableStateOf(viewModel.provider) }
    var modelId by remember { mutableStateOf(viewModel.modelId) }
    var modelName by remember { mutableStateOf(viewModel.modelName) }
    var task by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("") }
    var showAgentPicker by remember { mutableStateOf(false) }

    val selectedProfile = viewModel.profiles.firstOrNull { it.id == selectedProfileId } ?: firstProfile
    val activeCount = viewModel.tasks.count {
        runCatching { AgentTaskStatus.valueOf(it.status) }
            .getOrDefault(AgentTaskStatus.FAILED) !in setOf(
                AgentTaskStatus.COMPLETED,
                AgentTaskStatus.FAILED,
                AgentTaskStatus.CANCELLED,
            )
    }
    val canStart = viewModel.workspaceId != null &&
        selectedProfile != null &&
        task.isNotBlank() &&
        modelId.isNotBlank() &&
        activeCount < AgentCenterViewModel.MAX_LAUNCH_AGENTS &&
        !viewModel.assigning

    LaunchedEffect(provider) {
        viewModel.loadModels(provider)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Agents",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    viewModel.workspaceName ?: "Choose a workspace first",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    activeCount.toString() + "/" + AgentCenterViewModel.MAX_LAUNCH_AGENTS + " active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Start an agent",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Choose a premade role, describe the task, then start it. Agents stop when the task finishes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = { showAgentPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = viewModel.profiles.isNotEmpty(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                selectedProfile?.name ?: "Select an agent",
                                fontWeight = FontWeight.SemiBold,
                            )
                            selectedProfile?.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = task,
                        onValueChange = { task = it.take(60_000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Task") },
                        minLines = 4,
                        maxLines = 8,
                    )

                    ProviderPicker(provider) {
                        provider = it
                        modelId = ""
                        modelName = ""
                    }

                    ModelPicker(
                        provider = provider,
                        selectedId = modelId,
                        selectedName = modelName,
                        viewModel = viewModel,
                        onSelect = {
                            modelId = it.id
                            modelName = it.displayName
                        },
                    )

                    OutlinedTextField(
                        value = scope,
                        onValueChange = { scope = it.take(1_500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Files / folders") },
                        singleLine = true,
                    )

                    Button(
                        onClick = {
                            val profile = selectedProfile ?: return@Button
                            viewModel.assignBatch(
                                listOf(
                                    AgentLaunchDraft(
                                        id = profile.id + "-" + System.nanoTime(),
                                        profileId = profile.id,
                                        title = profile.name,
                                        instruction = task.trim(),
                                        provider = provider,
                                        modelId = modelId.trim(),
                                        modelName = modelName.ifBlank { modelId.trim() },
                                        scopeText = scope.trim(),
                                    )
                                )
                            )
                            task = ""
                        },
                        enabled = canStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(
                            when {
                                viewModel.assigning -> "Starting…"
                                activeCount >= AgentCenterViewModel.MAX_LAUNCH_AGENTS -> "Limit reached"
                                viewModel.workspaceId == null -> "Choose a workspace"
                                modelId.isBlank() -> "Select a model"
                                else -> "Start agent"
                            },
                        )
                    }

                    viewModel.message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Running and recent",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (viewModel.tasks.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text(
                        "No agents have been started yet.",
                        Modifier.fillMaxWidth().padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(viewModel.tasks, key = { it.taskId }) { taskEntity ->
                AgentTaskCard(taskEntity, viewModel)
            }
        }
    }

    if (showAgentPicker) {
        AlertDialog(
            onDismissRequest = { showAgentPicker = false },
            title = { Text("Choose an agent") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(viewModel.profiles, key = { it.id }) { profile ->
                        TextButton(
                            onClick = {
                                selectedProfileId = profile.id
                                showAgentPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(profile.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    profile.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAgentPicker = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun ProviderPicker(
    value: AIProvider,
    onChange: (AIProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Provider",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value.displayName, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AIProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        expanded = false
                        onChange(provider)
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelPicker(
    provider: AIProvider,
    selectedId: String,
    selectedName: String,
    viewModel: AgentCenterViewModel,
    onSelect: (AIModelInfo) -> Unit,
) {
    var expanded by remember(provider, selectedId) { mutableStateOf(false) }
    val models = viewModel.modelOptions(provider).filter { it.isTextCapable }.take(120)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Model",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = {
                expanded = true
                viewModel.loadModels(provider)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (selectedName.isBlank()) selectedId.ifBlank { "Select a model" } else selectedName,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No live models. Add a provider key in Settings.") },
                    onClick = {},
                    enabled = false,
                )
            } else {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    model.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(model)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentTaskCard(
    task: AgentTaskEntity,
    viewModel: AgentCenterViewModel,
) {
    val status = runCatching { AgentTaskStatus.valueOf(task.status) }
        .getOrDefault(AgentTaskStatus.FAILED)
    val terminal = status in setOf(
        AgentTaskStatus.COMPLETED,
        AgentTaskStatus.FAILED,
        AgentTaskStatus.CANCELLED,
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        status.name.replace('_', ' '),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    task.currentStep.coerceAtMost(task.stepCount).toString() + "/" + task.stepCount,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Text(
                task.modelName ?: task.modelId ?: "Model unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            task.lastToolId?.let {
                Text(
                    "Working: " + it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            task.errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!terminal) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (status) {
                        AgentTaskStatus.RUNNING,
                        AgentTaskStatus.QUEUED,
                        AgentTaskStatus.PLANNING -> OutlinedButton(
                            onClick = { viewModel.pause(task.taskId) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Hold") }

                        AgentTaskStatus.PAUSED -> Button(
                            onClick = { viewModel.resume(task.taskId) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Resume") }

                        AgentTaskStatus.WAITING_APPROVAL -> OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                        ) { Text("Approval required") }

                        else -> Spacer(Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { viewModel.cancel(task.taskId) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Stop") }
                }
            }
        }
    }
}
