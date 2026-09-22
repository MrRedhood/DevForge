package com.mrredhood.devforge.core.ide

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.diagnostics.Diagnostic
import com.mrredhood.devforge.core.diagnostics.DiagnosticSeverity
import com.mrredhood.devforge.core.editor.ContentSnapshot
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class IdeTool(val title: String) {
    OVERVIEW("Overview"),
    PROBLEMS("Problems"),
    PROJECT_MAP("Project map"),
    DEPENDENCIES("Dependencies"),
    ACTIVITY("Activity"),
    LOCAL_HISTORY("Local history"),
    LOGS("Logs"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeToolScreen(
    tool: IdeTool,
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (tool) {
            IdeTool.OVERVIEW -> OverviewContent(workspace, editor, padding)
            IdeTool.PROBLEMS -> ProblemsContent(editor, padding)
            IdeTool.PROJECT_MAP -> ProjectMapContent(workspace, padding)
            IdeTool.DEPENDENCIES -> DependenciesContent(workspace, padding)
            IdeTool.ACTIVITY -> ActivityContent(workspace, padding)
            IdeTool.LOCAL_HISTORY -> LocalHistoryContent(editor, padding)
            IdeTool.LOGS -> LogsContent(padding)
        }
    }
}

@Composable
private fun OverviewContent(workspace: WorkspaceViewModel, editor: EditorViewModel, padding: PaddingValues) {
    val db = remember { DevForgeDatabase.get(workspace.getApplication()) }
    val pending by db.approvalDao().observePending(50).collectAsState(initial = emptyList())
    val builds by db.buildReceiptDao().observeRecent(10).collectAsState(initial = emptyList())
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(workspace.workspace?.name ?: "No workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    workspace.remoteWorkspace?.let {
                        Text(it.owner + "/" + it.repository + " · " + it.branch, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { MetricRow("Files", workspace.entries.size.toString(), Icons.Default.List) }
        item { MetricRow("Open tabs", editor.tabs.size.toString(), Icons.Default.Code) }
        item { MetricRow("Dirty tabs", editor.tabs.count { it.isDirty }.toString(), Icons.Default.Warning) }
        item {
            val hasErrors = editor.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
            MetricRow("Problems", editor.diagnostics.size.toString(), if (hasErrors) Icons.Default.Error else Icons.Default.CheckCircle)
        }
        item { MetricRow("Pending approvals", pending.size.toString(), Icons.Default.Info) }
        item {
            val last = builds.firstOrNull()
            MetricRow("Last build", last?.conclusion ?: "No history", if (last?.conclusion == "success") Icons.Default.CheckCircle else Icons.Default.Build)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProblemsContent(editor: EditorViewModel, padding: PaddingValues) {
    val problems = editor.diagnostics
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (problems.isEmpty()) item { EmptyState("No problems") }
        items(problems, key = { it.message + ":" + (it.location?.line ?: 0) }) { DiagnosticRow(it) }
    }
}

@Composable
private fun DiagnosticRow(diagnostic: Diagnostic) {
    val color = when (diagnostic.severity) {
        DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
        DiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (diagnostic.severity == DiagnosticSeverity.ERROR) Icons.Default.Error else Icons.Default.Warning, contentDescription = null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(diagnostic.code ?: diagnostic.severity.name.lowercase(), color = color, fontWeight = FontWeight.Bold)
            }
            Text(diagnostic.message)
            diagnostic.location?.let {
                val position = listOfNotNull(it.path, it.line?.toString(), it.column?.toString()).joinToString(":")
                if (position.isNotBlank()) Text(position, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProjectMapContent(workspace: WorkspaceViewModel, padding: PaddingValues) {
    val symbols = workspace.symbolResults
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (symbols.isEmpty()) {
            item { EmptyState(if (workspace.isIndexing) "Indexing workspace…" else "No indexed symbols") }
        } else {
            symbols.groupBy { it.path }.forEach { group ->
                item { Text(group.key, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                items(group.value, key = { it.path + ":" + it.name + ":" + it.line }) { symbol ->
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                        Text(symbol.kind, Modifier.width(78.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(symbol.name, Modifier.weight(1f))
                        Text(":" + symbol.line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DependenciesContent(workspace: WorkspaceViewModel, padding: PaddingValues) {
    val manifests = workspace.entries.filter {
        it.name == "libs.versions.toml" || it.name == "build.gradle" || it.name == "build.gradle.kts" ||
            it.name == "settings.gradle" || it.name == "settings.gradle.kts"
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Dependency manifests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (manifests.isEmpty()) item { EmptyState("No common dependency manifests in this directory") }
        items(manifests, key = { it.uri.toString() }) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(entry.name, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActivityContent(workspace: WorkspaceViewModel, padding: PaddingValues) {
    val db = remember { DevForgeDatabase.get(workspace.getApplication()) }
    val audits by db.auditEventDao().observeRecent(60).collectAsState(initial = emptyList())
    val builds by db.buildReceiptDao().observeRecent(20).collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (audits.isEmpty() && builds.isEmpty()) item { EmptyState("No recent activity") }
        audits.forEach { event ->
            item(key = "audit:" + event.eventId) { ActivityRow(event.summary, event.eventType, event.createdAtEpochMs) }
        }
        builds.forEach { build ->
            item(key = "build:" + build.runId) {
                ActivityRow("Build #" + build.runNumber + " · " + build.target, build.conclusion ?: build.state, build.recordedAtEpochMs)
            }
        }
    }
}

@Composable
private fun ActivityRow(title: String, meta: String, time: Long) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(meta + " · " + SimpleDateFormat("HH:mm", Locale.US).format(Date(time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocalHistoryContent(editor: EditorViewModel, padding: PaddingValues) {
    val snapshots = produceState<List<ContentSnapshot>>(emptyList(), editor.activeTab?.uri) {
        value = withContext(Dispatchers.IO) {
            val durable = DurableStateRepository(DevForgeDatabase.get(editor.getApplication()))
            editor.activeTab?.uri?.let { durable.listSnapshots(it, 20) }.orEmpty()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (snapshots.value.isEmpty()) item { EmptyState("No local history for this file") }
        items(snapshots.value, key = { it.id }) { snapshot ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(snapshot.reason.name.lowercase().replace('_', ' '), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(snapshot.createdAt)), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { editor.restoreSnapshot(snapshot) }) { Text("Restore") }
                }
            }
        }
    }
}

@Composable
private fun LogsContent(padding: PaddingValues) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "200"))
                process.inputStream.bufferedReader().useLines { sequence -> lines = sequence.take(200).toList() }
                process.errorStream.bufferedReader().useLines { sequence -> error = sequence.firstOrNull() }
            }.onFailure { error = it.message ?: "Unable to read logcat." }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (lines.isEmpty() && error == null) item { EmptyState("No log output") }
        items(lines, key = { it.hashCode().toString() + it.take(20) }) {
            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text)
        }
    }
}
