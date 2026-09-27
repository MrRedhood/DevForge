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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.agent.AgentCoordinationService
import com.mrredhood.devforge.core.storage.AgentHandoffEntity
import com.mrredhood.devforge.core.storage.AgentSharedMemoryEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectMemoryScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val workspaceId = workspace.workspace?.id
    val coordination = remember {
        AgentCoordinationService(DevForgeDatabase.get(workspace.getApplication()))
    }
    val memory = if (workspaceId != null) {
        coordination.observeMemory(workspaceId, 50).collectAsState(initial = emptyList())
    } else null
    val handoffs = if (workspaceId != null) {
        coordination.observeHandoffs(workspaceId, 30).collectAsState(initial = emptyList())
    } else null
    var query by remember(workspaceId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project memory", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = workspace::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh workspace")
                    }
                },
            )
        },
    ) { padding ->
        val normalized = query.trim()
        val memoryItems = memory?.value.orEmpty().filter {
            normalized.isBlank() ||
                it.key.contains(normalized, true) ||
                it.content.contains(normalized, true)
        }
        val handoffItems = handoffs?.value.orEmpty().filter {
            normalized.isBlank() ||
                it.title.contains(normalized, true) ||
                it.summary.contains(normalized, true) ||
                it.status.contains(normalized, true)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Text("Durable AI project memory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Shared notes let the AI preserve verified project context across tasks. Secret-like content is rejected by the coordination layer.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search memory and handoffs") },
                    placeholder = { Text("Try build, architecture, migration…") },
                    singleLine = true,
                )
            }

            item {
                Text(
                    "Memory entries",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (workspaceId == null) {
                item { EmptyProjectMemoryState("Select a workspace to view project memory.") }
            } else if (memoryItems.isEmpty()) {
                item {
                    EmptyProjectMemoryState(
                        if (normalized.isBlank()) "No durable memory has been recorded for this workspace yet."
                        else "No memory matches the current search.",
                    )
                }
            } else {
                items(memoryItems, key = { it.memoryId }) { entry ->
                    MemoryEntryCard(
                        entry = entry,
                        onDelete = {
                            workspaceId?.let { id ->
                                scope.launch { coordination.deleteMemory(id, entry.key) }
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    "Agent handoffs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (workspaceId != null && handoffItems.isEmpty()) {
                item {
                    EmptyProjectMemoryState(
                        if (normalized.isBlank()) "No handoffs are currently stored for this workspace."
                        else "No handoffs match the current search.",
                    )
                }
            } else {
                items(handoffItems, key = { it.handoffId }) { handoff ->
                    HandoffCard(handoff)
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "This screen is intentionally observational for agent coordination. AI manages handoffs during execution; users can inspect continuity and remove stale memory notes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryEntryCard(
    entry: AgentSharedMemoryEntity,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.key, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatTimestamp(entry.updatedAtEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Delete")
                }
            }
            Text(entry.content)
            entry.sourceTaskId?.takeIf(String::isNotBlank)?.let {
                Text(
                    "Source task: " + it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HandoffCard(handoff: AgentHandoffEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(handoff.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    handoff.status.lowercase(Locale.US),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(handoff.summary)
            Text(
                "From: " + handoff.fromTaskId + (handoff.toTaskId?.let { " → " + it } ?: " → next available agent"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTimestamp(handoff.createdAtEpochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyProjectMemoryState(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(value))
