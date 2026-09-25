package com.mrredhood.devforge.core.ai

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.mrredhood.devforge.core.ai.MarkdownText
import com.mrredhood.devforge.core.agent.DevForgeToolCatalog
import com.mrredhood.devforge.core.picker.PickerBridge
import com.mrredhood.devforge.core.picker.SystemPickerActivity
import com.mrredhood.devforge.core.ai.workflow.AiPlanCard
import com.mrredhood.devforge.core.ai.workflow.AiWorkflowSnapshot
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AIChatScreen(viewModel: AIChatViewModel = viewModel()) {
    val listState = rememberLazyListState()
    val selected = viewModel.selectedModel

    LaunchedEffect(Unit) {
        viewModel.syncProviderFromSettings()
    }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) listState.animateScrollToItem(viewModel.messages.lastIndex)
    }

    LaunchedEffect(
        viewModel.streamingText,
        viewModel.toolActivities,
        viewModel.aiWorkflow?.updatedAtEpochMs,
    ) {
        if (viewModel.isSending || viewModel.toolActivities.isNotEmpty()) {
            val target = viewModel.messages.size
            if (target >= 0) listState.scrollToItem(target)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelSelector(viewModel)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.messages.isEmpty()) {
                EmptyChat(viewModel, selected)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(viewModel.messages, key = { it.messageId }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = { viewModel.copyMessageText(message) },
                            onEdit = if (message.role == "user") ({ viewModel.beginEditMessage(message) }) else null,
                            isEditing = viewModel.editingMessageId == message.messageId,
                        )
                    }
                    if (viewModel.isSending || viewModel.toolActivities.isNotEmpty()) {
                        item {
                            StreamingBubble(
                                content = viewModel.streamingText,
                                animationKind = viewModel.streamingAnimationKind,
                                toolActivities = viewModel.toolActivities,
                                workflow = viewModel.aiWorkflow,
                                isSending = viewModel.isSending,
                            )
                        }
                    }
                }
            }
        }

        if (viewModel.suggestions.isNotEmpty()) {
            CommandPalette(viewModel.suggestions, viewModel::selectCommand)
        }

        viewModel.sendError?.let { error ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    IconButton(onClick = viewModel::dismissError) { Text("×") }
                }
            }
        }

        AiPlanCard(viewModel.aiWorkflow)
        ChatComposer(viewModel)
    }
}

@Composable
private fun ModelSelector(viewModel: AIChatViewModel) {
    var providerMenuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(0.40f)) {
            OutlinedButton(
                onClick = { providerMenuOpen = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    viewModel.provider.displayName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Providers")
            }
            DropdownMenu(
                expanded = providerMenuOpen,
                onDismissRequest = { providerMenuOpen = false },
            ) {
                AIProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName) },
                        onClick = {
                            providerMenuOpen = false
                            viewModel.selectProvider(provider)
                        },
                    )
                }
            }
        }

        Box(Modifier.weight(0.60f)) {
            OutlinedButton(
                onClick = { viewModel.updateModelMenuOpen(true) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    viewModel.selectedModel?.displayName ?: "Select model",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Models")
            }
            if (viewModel.isModelMenuOpen) {
                Dialog(
                    onDismissRequest = { viewModel.updateModelMenuOpen(false) },
                ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .widthIn(max = 560.dp)
                        .heightIn(max = 680.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(end = 4.dp),
                        ) {
                            items(
                                ModelFilter.entries,
                                key = { it.name },
                            ) { filter ->
                                FilterChip(
                                    selected = viewModel.activeFilter == filter,
                                    onClick = { viewModel.setFilter(filter) },
                                    modifier = Modifier.widthIn(min = 64.dp),
                                    label = {
                                        Text(
                                            filter.label,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                        )
                                    },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = viewModel.modelQuery,
                            onValueChange = { viewModel.modelQuery = it },
                            singleLine = true,
                            label = { Text("Search models") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                        if (viewModel.isLoadingModels) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Loading live models…")
                            }
                        } else if (viewModel.filteredModels.isEmpty()) {
                            Text(
                                viewModel.modelError ?: "No matching models",
                                Modifier.fillMaxWidth().padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 480.dp),
                            ) {
                                items(
                                    viewModel.filteredModels,
                                    key = { it.provider.id + ":" + it.id },
                                ) { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    modelMetaLine(model),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = { viewModel.selectModel(model) },
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { viewModel.refreshModels() },
                            modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh model catalog")
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun EmptyChat(viewModel: AIChatViewModel, selected: AIModelInfo?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("DevForge AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            if (selected == null) "Choose a model to start. The dropdown fetches the provider's live catalog." else "This chat is persistent and isolated for ${selected.displayName}.",
            Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!viewModel.apiKeyConfigured) {
            Text("Add a provider API key in Settings.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun StreamingBubble(
    content: String,
    animationKind: StreamingAnimationKind,
    toolActivities: List<ChatToolActivity>,
    workflow: AiWorkflowSnapshot?,
    isSending: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(.94f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI is working", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    workflow?.let {
                        Text(
                            it.currentStep ?: when (it.status) {
                                AiWorkflowSnapshot.Status.COMPLETED -> "Completed"
                                AiWorkflowSnapshot.Status.FAILED -> "Failed"
                                AiWorkflowSnapshot.Status.CANCELLED -> "Cancelled"
                                AiWorkflowSnapshot.Status.WAITING -> "Waiting"
                                AiWorkflowSnapshot.Status.RUNNING -> "Working"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } ?: Spacer(Modifier.weight(1f))
                    if (isSending) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            workflow?.let { it.completedPlanSteps.toString() + "/" + it.planSteps.size } ?: "Done",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (toolActivities.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        toolActivities.forEach { activity -> ToolActivityChip(activity) }
                    }
                } else if (isSending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StreamingAnimation(animationKind)
                        Spacer(Modifier.width(6.dp))
                        Text("Thinking · working", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                workflow?.verification?.let { verification ->
                    Text(
                        "Verification · " +
                            verification.passedCount + " passed · " +
                            verification.failedCount + " failed · " +
                            verification.checks.count { it.status == com.mrredhood.devforge.core.ai.workflow.AiVerificationCheck.Status.SKIPPED } + " skipped",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (content.isNotBlank()) MarkdownText(content)
            }
        }
    }
}

@Composable
private fun ToolActivityChip(
    activity: ChatToolActivity,
) {
    val title = DevForgeToolCatalog.entry(activity.toolId)?.title ?: activity.toolId.wireName
    val surfaceColor = when (activity.status) {
        ChatToolActivity.Status.RUNNING -> MaterialTheme.colorScheme.secondaryContainer
        ChatToolActivity.Status.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        ChatToolActivity.Status.FAILED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (activity.status) {
        ChatToolActivity.Status.RUNNING -> MaterialTheme.colorScheme.onSecondaryContainer
        ChatToolActivity.Status.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
        ChatToolActivity.Status.FAILED -> MaterialTheme.colorScheme.onErrorContainer
    }
    var expanded by remember(activity.callId) {
        mutableStateOf(false)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (activity.status) {
                    ChatToolActivity.Status.RUNNING -> CircularProgressIndicator(
                        Modifier.size(13.dp), strokeWidth = 2.dp, color = contentColor,
                    )
                    ChatToolActivity.Status.COMPLETED -> Text("✓", color = contentColor, fontWeight = FontWeight.Bold)
                    ChatToolActivity.Status.FAILED -> Text("!", color = contentColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    when (activity.status) {
                        ChatToolActivity.Status.RUNNING -> title
                        ChatToolActivity.Status.COMPLETED -> "$title completed"
                        ChatToolActivity.Status.FAILED -> "$title failed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse execution details" else "Expand execution details",
                    tint = contentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
            if (expanded) {
                Text(activity.toolId.wireName, style = MaterialTheme.typography.labelSmall, color = contentColor)
                if (activity.detail.isNotBlank()) {
                    Text(activity.detail.take(500), style = MaterialTheme.typography.bodySmall, color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: com.mrredhood.devforge.core.storage.ChatMessageEntity,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    isEditing: Boolean,
) {
    val user = message.role == "user"
    var selectedAttachment by remember(message.messageId) { mutableStateOf<ChatMessageAttachment?>(null) }
    val messageAttachments = remember(message.content) { parseChatMessageAttachments(message.content) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (user) .86f else .94f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!message.commandName.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("/" + message.commandName, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                val visibleContent = stripChatMessageAttachmentMetadata(message.content)
                    .let { content ->
                        if (message.role == "user") {
                            content.substringBefore("Device attachments:")
                                .trimEnd()
                                .ifBlank { content.substringBefore("Device attachment:").trimEnd() }
                        } else {
                            content
                        }
                    }
                if (visibleContent.isNotBlank()) {
                    if (message.role == "assistant") MarkdownText(visibleContent) else Text(visibleContent)
                }
                if (messageAttachments.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        messageAttachments.forEach { attachment ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAttachment = attachment },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("FILE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            attachment.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            attachment.mimeType + " · " + formatChatAttachmentSize(attachment.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "Open",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy message") }
                    if (user && onEdit != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit and resend") }
                    }
                    if (message.editedAtEpochMs != null) Text("Edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isEditing) Text(if (user) " · resend" else " · editing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    selectedAttachment?.let { attachment ->
        ChatAttachmentViewerDialog(
            attachment = attachment,
            onDismiss = { selectedAttachment = null },
        )
    }
}

private fun formatChatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> (bytes / (1024L * 1024L)).toString() + " MB"
    bytes >= 1024L -> (bytes / 1024L).toString() + " KB"
    else -> bytes.toString() + " B"
}

@Composable
private fun CommandPalette(commands: List<AICommandDefinition>, onSelect: (AICommandDefinition) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text("Commands", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
            commands.forEach { command ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("/${command.name}", fontWeight = FontWeight.SemiBold)
                            Text(command.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(command) },
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(viewModel: AIChatViewModel) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        PickerBridge.results.collectLatest { result ->
            val type = when (result.kind) {
                SystemPickerActivity.KIND_PHOTO -> ChatAttachmentType.PHOTO
                SystemPickerActivity.KIND_VIDEO -> ChatAttachmentType.VIDEO
                SystemPickerActivity.KIND_AUDIO -> ChatAttachmentType.AUDIO
                SystemPickerActivity.KIND_DOCUMENT -> ChatAttachmentType.DOCUMENT
                else -> ChatAttachmentType.ANY_FILE
            }
            if (!result.cancelled && result.uris.isNotEmpty()) {
                viewModel.addAttachments(result.uris.distinct(), type)
            }
        }
    }

    fun launchUniversalPicker() {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            viewModel.reportAttachmentPickerError(IllegalStateException("Unable to access the current Activity."))
            return
        }
        runCatching {
            activity.startActivityForResult(
                Intent(context, SystemPickerActivity::class.java)
                    .putExtra(SystemPickerActivity.EXTRA_KIND, SystemPickerActivity.KIND_ATTACHMENTS),
                SystemPickerActivity.PICKER_REQUEST_CODE,
            )
        }.onFailure { viewModel.reportAttachmentPickerError(it) }
    }

    if (viewModel.isEditingMessage) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Editing user message · resend",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::cancelEditMessage) { Text("Cancel") }
        }
    }

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (viewModel.attachments.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    viewModel.attachments.forEach { attachment ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.padding(start = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(attachment.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                TextButton(onClick = { viewModel.removeAttachment(attachment.uri) }) { Text("×") }
                            }
                        }
                    }
                }
            }

            BasicTextField(
                value = viewModel.input,
                onValueChange = viewModel::updateInput,
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp, max = 92.dp).padding(horizontal = 8.dp),
                minLines = 1,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box {
                        if (viewModel.input.isBlank()) Text("Ask anything, type / for commands, or @ a file…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::launchUniversalPicker) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = when {
                        viewModel.isSending -> viewModel::stopGeneration
                        viewModel.isPausing -> ({})
                        else -> viewModel::submit
                    },
                    enabled = when {
                        viewModel.isSending -> true
                        viewModel.isPausing -> false
                        else -> viewModel.input.isNotBlank() || viewModel.attachments.isNotEmpty()
                    },
                ) {
                    when {
                        viewModel.isPausing -> {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        viewModel.isSending -> {
                            Icon(Icons.Default.Pause, contentDescription = "Pause AI")
                        }
                        else -> {
                            Icon(Icons.Default.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    repeat(8) {
        when (val value = current) {
            is FragmentActivity -> return value
            is ContextWrapper -> current = value.baseContext
            else -> return null
        }
    }
    return null
}

private fun modelMetaLine(model: AIModelInfo): String = buildString {
    append(model.priceClass.name.lowercase().replaceFirstChar { it.uppercase() })
    model.contextLimit?.let { append(" · ").append(contextLabel(it)) }
    if (model.isImageCapable) append(" · image")
    if (model.isVideoCapable) append(" · video")
    if (model.isVoiceCapable) append(" · voice")
    if (model.isEmbedding) append(" · embedding")
}

private fun contextLabel(value: Long?): String {
    if (value == null) return "Context unknown"
    return when {
        value >= 1_000_000L -> "${value / 1_000_000L}M context"
        value >= 1_000L -> "${value / 1_000L}K context"
        else -> "$value tokens"
    }
}



private fun attachmentFallbackMimeType(type: ChatAttachmentType): String = when (type) {
    ChatAttachmentType.ANY_FILE -> "*/*"
    ChatAttachmentType.PHOTO -> "image/*"
    ChatAttachmentType.VIDEO -> "video/*"
    ChatAttachmentType.AUDIO -> "audio/*"
    ChatAttachmentType.DOCUMENT -> "*/*"
}

private fun maxUploadLabel(bytes: Long): String =
    if (bytes >= 1024L * 1024L) (bytes / (1024L * 1024L)).toString() + " MB max" else bytes.toString() + " B max"
