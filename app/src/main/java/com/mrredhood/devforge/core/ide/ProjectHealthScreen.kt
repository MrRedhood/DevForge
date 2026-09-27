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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.diagnostics.DiagnosticSeverity
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.quality.WorkspaceCheckState
import com.mrredhood.devforge.core.quality.WorkspaceIntegrityReport
import com.mrredhood.devforge.core.quality.WorkspaceIntegrityService
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHealthScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    editor: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val app = workspace.getApplication<android.app.Application>()
    val database = remember { DevForgeDatabase.get(app) }
    val builds by database.buildReceiptDao().observeRecent(8).collectAsState(initial = emptyList())
    val approvals by database.approvalDao().observePending(50).collectAsState(initial = emptyList())
    var refreshToken by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<WorkspaceIntegrityReport?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(workspace.workspace, refreshToken) {
        checking = true
        report = withContext(Dispatchers.IO) {
            WorkspaceIntegrityService(app).inspect(workspace.workspace)
        }
        checking = false
    }

    val issueCount = editor.diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = editor.diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
    val dirtyTabs = editor.tabs.count { it.isDirty }
    val latestBuild = builds.firstOrNull()
    val buildCheck = when {
        latestBuild == null -> WorkspaceHealthItem("build", "Recent build evidence", WorkspaceCheckState.WARN, "No recent Build Center receipt is recorded.")
        latestBuild.conclusion.equals("success", true) -> WorkspaceHealthItem(
            "build",
            "Recent build evidence",
            WorkspaceCheckState.PASS,
            "Latest " + latestBuild.target + " run completed successfully.",
        )
        latestBuild.conclusion.equals("failure", true) -> WorkspaceHealthItem(
            "build",
            "Recent build evidence",
            WorkspaceCheckState.FAIL,
            "Latest " + latestBuild.target + " run failed. Open Diagnostics or Build Center for evidence.",
        )
        else -> WorkspaceHealthItem(
            "build",
            "Recent build evidence",
            WorkspaceCheckState.WARN,
            "Latest " + latestBuild.target + " run is " + (latestBuild.state ?: "not finalized") + ".",
        )
    }
    val editorCheck = when {
        issueCount > 0 -> WorkspaceHealthItem("editor", "Editor diagnostics", WorkspaceCheckState.FAIL, issueCount.toString() + " error(s) are currently reported.")
        warningCount > 0 -> WorkspaceHealthItem("editor", "Editor diagnostics", WorkspaceCheckState.WARN, warningCount.toString() + " warning(s) are currently reported.")
        else -> WorkspaceHealthItem("editor", "Editor diagnostics", WorkspaceCheckState.PASS, "No editor errors or warnings are currently reported.")
    }
    val unsavedCheck = if (dirtyTabs > 0) {
        WorkspaceHealthItem("changes", "Unsaved editor changes", WorkspaceCheckState.WARN, dirtyTabs.toString() + " open tab(s) contain unsaved changes.")
    } else {
        WorkspaceHealthItem("changes", "Unsaved editor changes", WorkspaceCheckState.PASS, "No open editor tab has unsaved changes.")
    }
    val approvalCheck = if (approvals.isNotEmpty()) {
        WorkspaceHealthItem("approval", "Pending approvals", WorkspaceCheckState.WARN, approvals.size.toString() + " sensitive action approval(s) are waiting.")
    } else {
        WorkspaceHealthItem("approval", "Pending approvals", WorkspaceCheckState.PASS, "No approval is waiting for user review.")
    }
    val integrityChecks = report?.checks.orEmpty().map {
        WorkspaceHealthItem(it.id, it.label, it.state, it.detail)
    }
    val healthItems = integrityChecks + buildCheck + editorCheck + unsavedCheck + approvalCheck
    val fails = healthItems.count { it.state == WorkspaceCheckState.FAIL }
    val warnings = healthItems.count { it.state == WorkspaceCheckState.WARN }
    val headline = when {
        checking -> "Checking project health…"
        workspace.workspace == null -> "Select a workspace"
        fails > 0 -> fails.toString() + " issue(s) need attention"
        warnings > 0 -> warnings.toString() + " warning(s) need attention"
        else -> "Project is healthy"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project health", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshToken++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh health checks")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(workspace.workspace?.name ?: "No workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            headline,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = headlineColor(fails, warnings, checking),
                        )
                        Text(
                            healthItems.count { it.state == WorkspaceCheckState.PASS }.toString() + " passed · " +
                                warnings + " warning(s) · " + fails + " failure(s)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Text("Health checks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (workspace.workspace == null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Text("Select a workspace to populate project health checks.")
                        }
                    }
                }
            } else {
                items(healthItems, key = { it.id }) { item ->
                    HealthItemCard(item)
                }
            }
            item {
                Text("What this means", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Text(
                    "Health combines persisted workspace integrity, build evidence, editor diagnostics, unsaved work and approvals. It is a triage view, not a replacement for Build Center or Release readiness.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class WorkspaceHealthItem(
    val id: String,
    val label: String,
    val state: WorkspaceCheckState,
    val detail: String,
)

@Composable
private fun HealthItemCard(item: WorkspaceHealthItem) {
    val icon = when (item.state) {
        WorkspaceCheckState.PASS -> Icons.Default.Check
        WorkspaceCheckState.WARN -> Icons.Default.Warning
        WorkspaceCheckState.FAIL -> Icons.Default.Error
    }
    val color = when (item.state) {
        WorkspaceCheckState.PASS -> MaterialTheme.colorScheme.primary
        WorkspaceCheckState.WARN -> MaterialTheme.colorScheme.tertiary
        WorkspaceCheckState.FAIL -> MaterialTheme.colorScheme.error
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.label, fontWeight = FontWeight.SemiBold)
                Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun headlineColor(
    failures: Int,
    warnings: Int,
    checking: Boolean,
): Color = when {
    checking -> MaterialTheme.colorScheme.onSurface
    failures > 0 -> MaterialTheme.colorScheme.error
    warnings > 0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}
