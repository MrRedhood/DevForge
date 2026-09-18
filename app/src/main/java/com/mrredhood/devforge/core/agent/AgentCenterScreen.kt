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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.mrredhood.devforge.core.ai.AIProvider
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.storage.AgentTaskEntity
import java.util.UUID

@Composable
fun AgentCenterScreen(viewModel: AgentCenterViewModel = viewModel()) {
    var drafts by remember(viewModel.profiles.size) {
        mutableStateOf(
            listOf(
                newDraft(viewModel.profiles.firstOrNull()?.id.orEmpty(), viewModel.provider, viewModel.modelId, viewModel.modelName)
            )
        )
    }
    var sharedModel by remember { mutableStateOf(true) }
    var sharedProvider by remember { mutableStateOf(viewModel.provider) }
    var sharedModelId by remember { mutableStateOf(viewModel.modelId) }
    var sharedModelName by remember { mutableStateOf(viewModel.modelName) }
    var showProfiles by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AgentProfile?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(sharedProvider) {
        viewModel.loadModels(sharedProvider)
    }
    LaunchedEffect(drafts.map { it.provider }.distinct()) {
        drafts.map { it.provider }.distinct().forEach(viewModel::loadModels)
    }

    if (showProfiles) {
        AgentProfileManager(
            profiles = viewModel.profiles,
            onEdit = { editingProfile = it; creatingProfile = false },
            onCreate = {
                editingProfile = AgentProfile(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    description = "",
                    instructions = "",
                    access = AgentAccess.CODING_DEFAULT,
                    builtin = false,
                )
                creatingProfile = true
            },
            onDelete = viewModel::deleteProfile,
            onClose = { showProfiles = false },
        )
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Agents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text(
                                "Launch up to 10 one-shot agents. Each agent runs until its task finishes, waits only when approval is required, then stops.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { showProfiles = true }) { Text("Manage agents") }
                    }
                    Text(
                        viewModel.workspaceName ?: "Choose a workspace first",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Model for all agents", fontWeight = FontWeight.Bold)
                                Text(
                                    "Use one provider/model for every task, or choose independently below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = sharedModel, onCheckedChange = { sharedModel = it })
                        }
                        if (sharedModel) {
                            ProviderPicker(sharedProvider) { value ->
                                sharedProvider = value
                                sharedModelId = ""
                                sharedModelName = ""
                                drafts = drafts.map { it.copy(provider = value, modelId = "", modelName = "") }
                            }
                            ModelPicker(
                                provider = sharedProvider,
                                selectedId = sharedModelId,
                                selectedName = sharedModelName,
                                viewModel = viewModel,
                                onSelect = { model ->
                                    sharedModelId = model.id
                                    sharedModelName = model.displayName
                                    drafts = drafts.map { it.copy(provider = sharedProvider, modelId = model.id, modelName = model.displayName) }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Launch queue · " + drafts.size + "/10", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        drafts.forEachIndexed { index, draft ->
                            AgentDraftCard(
                                index = index,
                                draft = draft,
                                profiles = viewModel.profiles,
                                sharedModel = sharedModel,
                                viewModel = viewModel,
                                onChange = { changed -> drafts = drafts.map { if (it.id == changed.id) changed else it } },
                                onRemove = { if (drafts.size > 1) drafts = drafts.filterNot { it.id == draft.id } },
                            )
                        }
                        if (drafts.size < AgentCenterViewModel.MAX_LAUNCH_AGENTS) {
                            OutlinedButton(
                                onClick = {
                                    drafts = drafts + newDraft(
                                        viewModel.profiles.firstOrNull()?.id.orEmpty(),
                                        if (sharedModel) sharedProvider else viewModel.provider,
                                        if (sharedModel) sharedModelId else "",
                                        if (sharedModel) sharedModelName else "",
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("+ Add agent") }
                        }
                        Button(
                            onClick = { viewModel.assignBatch(drafts) },
                            enabled = !viewModel.assigning && viewModel.workspaceId != null && drafts.any { it.instruction.isNotBlank() && it.modelId.isNotBlank() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (viewModel.assigning) "Starting agents…" else "Start " + drafts.count { it.instruction.isNotBlank() } + " agent(s)") }
                        viewModel.message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Text("Workspace agents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }

            if (viewModel.tasks.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text("No agents assigned yet.", Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(viewModel.tasks, key = { it.taskId }) { task ->
                    AgentTaskCard(task, viewModel, viewModel.auditEvents)
                }
            }
        }
    }

    editingProfile?.let { profile ->
        AgentProfileEditorDialog(
            initial = profile,
            creating = creatingProfile,
            onDismiss = { editingProfile = null },
            onSave = {
                viewModel.saveProfile(it)
                editingProfile = null
            },
        )
    }
}

@Composable
private fun AgentDraftCard(
    index: Int,
    draft: AgentLaunchDraft,
    profiles: List<AgentProfile>,
    sharedModel: Boolean,
    viewModel: AgentCenterViewModel,
    onChange: (AgentLaunchDraft) -> Unit,
    onRemove: () -> Unit,
) {
    var profileMenu by remember(draft.id, draft.profileId, profiles.size) { mutableStateOf(false) }
    val profile = profiles.firstOrNull { it.id == draft.profileId } ?: profiles.firstOrNull()
    val provider = if (sharedModel) draft.provider else draft.provider

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Agent " + (index + 1), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (index > 0) TextButton(onClick = onRemove) { Text("Remove") }
            }

            DropdownMenu(
                expanded = profileMenu,
                onDismissRequest = { profileMenu = false },
            ) {
                profiles.take(20).forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.name) },
                        onClick = {
                            profileMenu = false
                            onChange(draft.copy(profileId = item.id))
                        },
                    )
                }
            }
            OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text((profile?.name ?: "Select agent profile") + " · " + (profile?.description ?: ""), maxLines = 2)
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = { onChange(draft.copy(title = it.take(120))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Agent title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.instruction,
                onValueChange = { onChange(draft.copy(instruction = it.take(60_000))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Task") },
                minLines = 3,
                maxLines = 7,
            )

            if (!sharedModel) {
                ProviderPicker(draft.provider) { value ->
                    viewModel.loadModels(value)
                    onChange(draft.copy(provider = value, modelId = "", modelName = ""))
                }
                ModelPicker(
                    provider = provider,
                    selectedId = draft.modelId,
                    selectedName = draft.modelName,
                    viewModel = viewModel,
                    onSelect = { model -> onChange(draft.copy(modelId = model.id, modelName = model.displayName)) },
                )
            } else {
                Text(
                    "Model: " + if (draft.modelName.isBlank()) draft.modelId.ifBlank { "not selected" } else draft.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = draft.scopeText,
                onValueChange = { onChange(draft.copy(scopeText = it.take(1_500))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Path scope (optional)") },
                placeholder = { Text("src, tests/unit") },
                singleLine = true,
            )

            if (profile != null) {
                Text(
                    "Access: " + profile.access.map(AgentAccess::label).sorted().joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderPicker(value: AIProvider, onChange: (AIProvider) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AIProvider.entries.forEach { provider ->
            FilterChip(selected = value == provider, onClick = { onChange(provider) }, label = { Text(provider.displayName) })
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

    BoxLikeDropdown(
        label = if (selectedName.isBlank()) selectedId.ifBlank { "Select a model" } else selectedName,
        expanded = expanded,
        onOpen = { expanded = true; viewModel.loadModels(provider) },
        onDismiss = { expanded = false },
        content = {
            if (models.isEmpty()) {
                DropdownMenuItem(text = { Text("No live chat models loaded. Add a provider key in Settings.") }, onClick = {}, enabled = false)
            } else {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = { expanded = false; onSelect(model) },
                    )
                }
            }
        },
    )
}

@Composable
private fun BoxLikeDropdown(
    label: String,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) { content() }
    }
}

@Composable
private fun AgentProfileManager(
    profiles: List<AgentProfile>,
    onEdit: (AgentProfile) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agent profiles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Premade profiles can be edited. Custom profiles can be created and deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClose) { Text("Back") }
            }
        }
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("+ Create new agent") }
        }
        items(profiles, key = { it.id }) { profile ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Bold)
                            Text(if (profile.builtin) "Premade" else "Custom", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onEdit(profile) }) { Text("Edit") }
                        if (!profile.builtin) TextButton(onClick = { onDelete(profile.id) }) { Text("Delete") }
                    }
                    Text(profile.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Access: " + profile.access.map(AgentAccess::label).sorted().joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AgentProfileEditorDialog(
    initial: AgentProfile,
    creating: Boolean,
    onDismiss: () -> Unit,
    onSave: (AgentProfile) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var instructions by remember(initial.id) { mutableStateOf(initial.instructions) }
    var access by remember(initial.id) { mutableStateOf(initial.access) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Create agent" else "Edit agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(AgentProfileRepository.MAX_NAME_CHARS) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it.take(AgentProfileRepository.MAX_DESCRIPTION_CHARS) }, label = { Text("Description") }, minLines = 2, maxLines = 4)
                OutlinedTextField(value = instructions, onValueChange = { instructions = it.take(AgentProfileRepository.MAX_INSTRUCTION_CHARS) }, label = { Text("Instructions") }, minLines = 4, maxLines = 8)
                Text("Permissions", fontWeight = FontWeight.Bold)
                AgentAccess.entries.forEach { item ->
                    FilterChip(
                        selected = item in access,
                        onClick = { access = if (item in access) access - item else access + item },
                        label = { Text(item.label) },
                    )
                }
                Text(
                    "Access switches are policy declarations. Only capabilities with registered DevForge tools can currently be exercised; disabled access is enforced before any tool runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(initial.copy(name = name, description = description, instructions = instructions, access = access))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun newDraft(profileId: String, provider: AIProvider, modelId: String, modelName: String): AgentLaunchDraft =
    AgentLaunchDraft(
        id = UUID.randomUUID().toString(),
        profileId = profileId,
        title = "",
        instruction = "",
        provider = provider,
        modelId = modelId,
        modelName = modelName,
        scopeText = "",
    )

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
            val timeline = AgentExecutionTimeline.forTask(auditEvents, task.taskId)
            if (timeline.isNotEmpty()) {
                Text("Execution timeline", fontWeight = FontWeight.Bold)
                timeline.forEach { event ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(event.eventType.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(140.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.summary.take(280), style = MaterialTheme.typography.bodySmall)
                            Text(formatTimelineTime(event.createdAtEpochMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
