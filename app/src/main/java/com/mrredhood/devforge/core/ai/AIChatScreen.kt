package com.mrredhood.devforge.core.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.mrredhood.devforge.core.ai.MarkdownText

@Composable
fun AIChatScreen(viewModel: AIChatViewModel = viewModel()) {
    val listState = rememberLazyListState()
    val selected = viewModel.selectedModel

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) listState.animateScrollToItem(viewModel.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    items(viewModel.messages, key = { it.messageId }) { message -> MessageBubble(message) }
                    if (viewModel.isSending) {
                        item { StreamingBubble(viewModel.streamingText, viewModel.streamingAnimationKind) }
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
            DropdownMenu(
                expanded = viewModel.isModelMenuOpen,
                onDismissRequest = { viewModel.updateModelMenuOpen(false) },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ModelFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = viewModel.activeFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = viewModel.modelQuery,
                    onValueChange = { viewModel.modelQuery = it },
                    singleLine = true,
                    label = { Text("Search models") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                    DropdownMenuItem(
                        text = { Text(viewModel.modelError ?: "No matching models") },
                        onClick = {},
                        enabled = false,
                    )
                } else {
                    viewModel.filteredModels.forEach { model ->
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
                DropdownMenuItem(
                    text = { Text("Refresh model catalog") },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = { viewModel.refreshModels() },
                )
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
private fun StreamingBubble(content: String, animationKind: StreamingAnimationKind) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(.94f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreamingAnimation(animationKind)
                    Spacer(Modifier.width(6.dp))
                    Text("Generating", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                }
                if (content.isNotBlank()) {
                    MarkdownText(content)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: com.mrredhood.devforge.core.storage.ChatMessageEntity) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(if (user) .86f else .94f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(14.dp)) {
                if (!message.commandName.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("/${message.commandName}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (message.role == "assistant") MarkdownText(message.content) else Text(message.content)
            }
        }
    }
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
    var attachmentMenuOpen by remember { mutableStateOf(false) }
    var pickerType by remember { mutableStateOf(ChatAttachmentType.ANY_FILE) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        runCatching {
            viewModel.addAttachments(uris.distinct(), pickerType)
        }.onFailure(viewModel::reportAttachmentPickerError)
    }
    val fallbackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        runCatching {
            viewModel.addAttachments(uris.distinct(), pickerType)
        }.onFailure(viewModel::reportAttachmentPickerError)
    }
    fun launchPicker(type: ChatAttachmentType) {
        pickerType = type
        attachmentMenuOpen = false
        runCatching {
            picker.launch(attachmentMimeTypes(type))
        }.onFailure {
            runCatching {
                fallbackPicker.launch(attachmentFallbackMimeType(type))
            }.onFailure(viewModel::reportAttachmentPickerError)
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
                Box {
                    IconButton(onClick = { attachmentMenuOpen = true }) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                    DropdownMenu(
                        expanded = attachmentMenuOpen,
                        onDismissRequest = { attachmentMenuOpen = false },
                    ) {
                        ChatAttachmentType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label + " · " + maxUploadLabel(type.maxBytes)) },
                                onClick = { launchPicker(type) },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = if (viewModel.isSending) viewModel::stopGeneration else viewModel::submit,
                    enabled = viewModel.isSending || viewModel.input.isNotBlank() || viewModel.attachments.isNotEmpty(),
                ) {
                    if (viewModel.isSending) Text("■", fontWeight = FontWeight.Black)
                    else Icon(Icons.Default.Send, "Send")
                }
            }
        }
    }
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


private fun attachmentMimeTypes(type: ChatAttachmentType): Array<String> = when (type) {
    ChatAttachmentType.ANY_FILE -> arrayOf("*/*")
    ChatAttachmentType.PHOTO -> arrayOf("image/*")
    ChatAttachmentType.VIDEO -> arrayOf("video/*")
    ChatAttachmentType.AUDIO -> arrayOf("audio/*")
    ChatAttachmentType.DOCUMENT -> arrayOf(
        "application/pdf",
        "text/*",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
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
