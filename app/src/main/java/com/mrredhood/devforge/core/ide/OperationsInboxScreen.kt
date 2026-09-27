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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.agent.AgentTaskStatus
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.OfflineActionQueue
import com.mrredhood.devforge.core.storage.OfflineQueueItem
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsInboxScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
) {
    val app = workspace.getApplication<android.app.Application>()
    val database = remember { DevForgeDatabase.get(app) }
    val workspaceId = workspace.workspace?.id
    val tasks = if (workspaceId != null) {
        database.agentTaskDao().observe(workspaceId, 40).collectAsState(initial = emptyList())
    } else null
    val builds = database.buildReceiptDao().observeRecent(20).collectAsState(initial = emptyList())
    val approvals = if (workspaceId != null) {
        database.approvalDao().observePending(30).collectAsState(initial = emptyList())
    } else null
    var offline by remember { mutableStateOf<List<OfflineQueueItem>>(emptyList()) }
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(refreshToken, workspaceId) {
        offline = withContext(Dispatchers.IO) { OfflineActionQueue(app).list() }
    }

    val rows = buildList {
        tasks?.value.orEmpty().forEach { task ->
            add(
                InboxRow(
                    id = "agent:" + task.taskId,
                    title = task.title,
                    type = "AI · " + task.status.replace('_', ' '),
                    detail = task.errorMessage ?: task.result ?: (
                        "Step " + task.currentStep + "/" + task.stepCount +
                            " · " + (task.lastToolId ?: "waiting")
                        ),
                    time = task.updatedAtEpochMs,
                    kind = when (task.status) {
                        AgentTaskStatus.FAILED.name -> InboxKind.ERROR
                        AgentTaskStatus.WAITING_APPROVAL.name -> InboxKind.APPROVAL
                        AgentTaskStatus.PAUSED.name -> InboxKind.WARNING
                        else -> InboxKind.AI
                    },
                ),
            )
        }
        approvals?.value.orEmpty().forEach { approval ->
            add(
                InboxRow(
                    id = "approval:" + approval.approvalId,
                    title = "Approval required",
                    type = "Security · " + approval.risk,
                    detail = approval.summary,
                    time = approval.createdAtEpochMs,
                    kind = InboxKind.APPROVAL,
                ),
            )
        }
        builds.value.forEach { build ->
            val success = build.conclusion.equals("success", true)
            add(
                InboxRow(
                    id = "build:" + build.runId,
                    title = "Build #" + build.runNumber,
                    type = "Build · " + build.target,
                    detail = build.conclusion ?: build.state,
                    time = build.recordedAtEpochMs,
                    kind = if (success) InboxKind.INFO else InboxKind.WARNING,
                ),
            )
        }
        offline.forEach { item ->
            add(
                InboxRow(
                    id = "offline:" + item.id,
                    title = item.title,
                    type = "Offline · " + item.type,
                    detail = "Queued for reconciliation · attempts " + item.attempts,
                    time = item.createdAt,
                    kind = InboxKind.SYNC,
                ),
            )
        }
    }.sortedByDescending { it.time }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operations inbox", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshToken++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh inbox")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Durable operations inbox", fontWeight = FontWeight.Bold)
                                Text(
                                    "One place for AI runs, approvals, builds, recovery signals and offline work waiting for reconciliation.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "This is an observation and triage surface. AI agent execution remains AI-managed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (rows.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text(
                            "Nothing currently needs attention.",
                            Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(rows, key = { it.id }) { row ->
                    InboxRowCard(row, offline, app)
                }
            }
        }
    }
}

private enum class InboxKind { AI, APPROVAL, ERROR, WARNING, INFO, SYNC }

private data class InboxRow(
    val id: String,
    val title: String,
    val type: String,
    val detail: String,
    val time: Long,
    val kind: InboxKind,
)

@Composable
private fun InboxRowCard(
    row: InboxRow,
    offline: List<OfflineQueueItem>,
    app: android.content.Context,
) {
    OfflineActionQueueHolder.bind(app)
    val icon = when (row.kind) {
        InboxKind.AI -> Icons.Default.AutoAwesome
        InboxKind.APPROVAL -> Icons.Default.Security
        InboxKind.ERROR -> Icons.Default.Error
        InboxKind.WARNING -> Icons.Default.Warning
        InboxKind.INFO -> Icons.Default.Cloud
        InboxKind.SYNC -> Icons.Default.Sync
    }
    val iconColor = when (row.kind) {
        InboxKind.ERROR -> MaterialTheme.colorScheme.error
        InboxKind.APPROVAL, InboxKind.WARNING -> MaterialTheme.colorScheme.tertiary
        InboxKind.SYNC -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor)
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.title, fontWeight = FontWeight.SemiBold)
                    Text(row.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(row.time)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(row.detail)
            if (row.id.startsWith("offline:")) {
                val item = offline.firstOrNull { row.id == "offline:" + it.id }
                item?.let {
                    TextButton(onClick = { OfflineActionQueueHolder.remove(it.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Dismiss queued item")
                    }
                }
            }
        }
    }
}

private object OfflineActionQueueHolder {
    private var queue: OfflineActionQueue? = null
    fun bind(context: android.content.Context) {
        if (queue == null) queue = OfflineActionQueue(context.applicationContext)
    }
    fun remove(id: String) {
        queue?.remove(id)
    }
}
