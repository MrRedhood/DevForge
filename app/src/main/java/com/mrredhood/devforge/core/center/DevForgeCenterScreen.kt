package com.mrredhood.devforge.core.center

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.agent.AgentActivityViewModel
import com.mrredhood.devforge.core.build.BuildFailureDiagnosis
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.github.GitHubRepositoryViewModel
import com.mrredhood.devforge.core.github.GitHubRepositoryActivityViewModel
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.OfflineActionQueue
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import com.mrredhood.devforge.core.quality.DevForgeOperationCenter
import com.mrredhood.devforge.core.quality.DevForgeReleaseQualityGate
import com.mrredhood.devforge.core.quality.DevForgeSecurityScanner
import com.mrredhood.devforge.core.quality.PerformanceBudgetSnapshot
import com.mrredhood.devforge.core.quality.SecurityFinding
import com.mrredhood.devforge.core.quality.WorkspaceIntegrityReport
import com.mrredhood.devforge.core.quality.WorkspaceIntegrityService
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CenterPage {
    HOME, HEALTH, QUALITY, AI_RUNS, TESTING, DEPENDENCIES, DIAGNOSIS, OFFLINE, BACKUP, RELEASE, GITHUB, AUTOMATION, CODE, EXTENSIONS
}

private data class CenterCard(
    val page: CenterPage,
    val title: String,
    val description: String,
)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun DevForgeCenterScreen(
    onBack: () -> Unit,
    onOpenBuild: () -> Unit,
    onOpenGitHubCreate: () -> Unit,
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
) {
    var page by rememberSaveable { mutableStateOf(CenterPage.HOME.name) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val agent: AgentActivityViewModel = viewModel()
    val build: BuildViewModel = viewModel()
    val repos: GitHubRepositoryViewModel = viewModel()
    val queue = remember { OfflineActionQueue(editor.getApplication<Application>()) }
    val scope = rememberCoroutineScope()
    var queueItems by remember { mutableStateOf(queue.list()) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    fun currentPage(): CenterPage = runCatching { CenterPage.valueOf(page) }.getOrDefault(CenterPage.HOME)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val root = JSONObject()
                .put("version", 1)
                .put("exportedAt", System.currentTimeMillis())
                .put("workspaceId", workspace.workspace?.id)
                .put("workspaceName", workspace.workspace?.name)
                .put("workspaceUri", workspace.workspace?.treeUri?.toString())
            val tabs = JSONArray()
            editor.tabs.take(40).forEach { tab ->
                tabs.put(
                    JSONObject()
                        .put("uri", tab.uri.toString())
                        .put("name", tab.name.take(500))
                        .put("content", tab.content.take(250_000))
                        .put("savedContent", tab.savedContent.take(250_000)),
                )
            }
            root.put("openTabs", tabs)
            runCatching {
                editor.getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(root.toString(2).toByteArray(Charsets.UTF_8))
                } ?: error("Unable to open the destination file.")
            }.onSuccess {
                withContext(Dispatchers.Main.immediate) { backupMessage = "Backup exported." }
            }.onFailure {
                withContext(Dispatchers.Main.immediate) { backupMessage = it.message ?: "Backup export failed." }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val text = editor.getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (bytes.size() + read > MAX_BACKUP_BYTES) {
                            error("Backup file exceeds the ${MAX_BACKUP_BYTES / 1024} KiB safety limit.")
                        }
                        bytes.write(buffer, 0, read)
                    }
                    bytes.toByteArray().toString(Charsets.UTF_8)
                } ?: error("Unable to read backup.")
                JSONObject(text)
            }
            result.onSuccess { root ->
                val array = root.optJSONArray("openTabs") ?: JSONArray()
                var restored = 0
                for (index in 0 until minOf(array.length(), 40)) {
                    val item = array.optJSONObject(index) ?: continue
                    val uriValue = item.optString("uri").takeIf { it.isNotBlank() } ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                    editor.restoreBackupTab(
                        uriValue,
                        name,
                        item.optString("content").take(250_000),
                        item.optString("savedContent").take(250_000),
                    )
                    restored++
                }
                withContext(Dispatchers.Main.immediate) {
                    backupMessage = "Restored " + restored + " saved editor tabs."
                }
            }.onFailure {
                withContext(Dispatchers.Main.immediate) { backupMessage = it.message ?: "Backup import failed." }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentPage() == CenterPage.HOME) "DevForge Center" else currentPage().title(),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPage() == CenterPage.HOME) onBack() else page = CenterPage.HOME.name
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (currentPage()) {
            CenterPage.HOME -> CenterHome(
                padding,
                listOf(
                    CenterCard(CenterPage.HEALTH, "Project Health", "One dashboard for workspace, agent, build, Git and approvals."),
                    CenterCard(CenterPage.QUALITY, "Quality & Reliability", "Operations, workspace integrity, security scan, performance budgets and release readiness."),
                    CenterCard(CenterPage.AI_RUNS, "AI Run Inspector", "Inspect agent tasks, models, approvals, tools and results."),
                    CenterCard(CenterPage.TESTING, "Testing Center", "Review CI state, artifacts, logs and the existing build/test entry point."),
                    CenterCard(CenterPage.DEPENDENCIES, "Dependencies", "Detect dependency manifests and identify where dependency work lives."),
                    CenterCard(CenterPage.DIAGNOSIS, "Build Diagnosis", "Turn workflow logs into grouped failure findings and evidence."),
                    CenterCard(CenterPage.OFFLINE, "Offline Queue", "Keep supported remote intents instead of losing them while offline."),
                    CenterCard(CenterPage.BACKUP, "Workspace Backup", "Export and restore bounded editor/workspace state."),
                    CenterCard(CenterPage.RELEASE, "Release Center", "Release APK/AAB entry, signing state and artifact history."),
                    CenterCard(CenterPage.GITHUB, "GitHub Hub", "Connected repositories and direct repository creation."),
                    CenterCard(CenterPage.AUTOMATION, "Automation", "Inspect the existing durable event → action → approval pipeline."),
                    CenterCard(CenterPage.CODE, "Code Intelligence", "Language detection, symbols, diagnostics and LSP extension path."),
                    CenterCard(CenterPage.EXTENSIONS, "Extensions & MCP", "Document the sandbox boundary for future extensions."),
                ),
                onClick = { page = it.name },
            )
            CenterPage.HEALTH -> ProjectHealthPage(padding, workspace, editor, agent, build)
            CenterPage.QUALITY -> QualityReliabilityPage(padding, workspace, editor, build)
            CenterPage.AI_RUNS -> AIRunsPage(padding, agent, selectedTaskId, { selectedTaskId = it.takeIf(String::isNotBlank) })
            CenterPage.TESTING -> TestingPage(padding, build, onOpenBuild)
            CenterPage.DEPENDENCIES -> DependenciesPage(padding, workspace)
            CenterPage.DIAGNOSIS -> BuildDiagnosisPage(padding, build)
            CenterPage.OFFLINE -> OfflineQueuePage(padding, queue, queueItems, { queueItems = queue.list() })
            CenterPage.BACKUP -> BackupPage(
                padding,
                onExport = { exportLauncher.launch("devforge-backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                message = backupMessage,
            )
            CenterPage.RELEASE -> ReleasePage(padding, build, onOpenBuild)
            CenterPage.GITHUB -> GitHubHubPage(padding, repos, onOpenGitHubCreate)
            CenterPage.AUTOMATION -> AutomationExpansionPage(padding)
            CenterPage.CODE -> CodeIntelligencePage(padding)
            CenterPage.EXTENSIONS -> ExtensionsPage(padding)
        }
    }
}

@Composable
private fun CenterHome(
    padding: PaddingValues,
    cards: List<CenterCard>,
    onClick: (CenterPage) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("DevForge Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "The remaining product systems are grouped here without changing the editor-first workflow.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(cards, key = { it.page.name }) { card ->
            Card(
                onClick = { onClick(card.page) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(card.title, fontWeight = FontWeight.SemiBold)
                        Text(card.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

private fun CenterPage.title(): String = when (this) {
    CenterPage.HOME -> "DevForge Center"
    CenterPage.HEALTH -> "Project Health"
    CenterPage.QUALITY -> "Quality & Reliability"
    CenterPage.AI_RUNS -> "AI Run Inspector"
    CenterPage.TESTING -> "Testing Center"
    CenterPage.DEPENDENCIES -> "Dependencies"
    CenterPage.DIAGNOSIS -> "Build Diagnosis"
    CenterPage.OFFLINE -> "Offline Queue"
    CenterPage.BACKUP -> "Workspace Backup"
    CenterPage.RELEASE -> "Release Center"
    CenterPage.GITHUB -> "GitHub Hub"
    CenterPage.AUTOMATION -> "Automation"
    CenterPage.CODE -> "Code Intelligence"
    CenterPage.EXTENSIONS -> "Extensions & MCP"
}

@Composable
private fun QualityReliabilityPage(
    padding: PaddingValues,
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    build: BuildViewModel,
) {
    val app = editor.getApplication<Application>()
    var integrity by remember { mutableStateOf<WorkspaceIntegrityReport?>(null) }
    val operations by DevForgeOperationCenter.operations.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(workspace.workspace?.id) {
        integrity = null
        val current = workspace.workspace
        scope.launch(Dispatchers.IO) {
            val report = WorkspaceIntegrityService(app).inspect(current)
            withContext(Dispatchers.Main.immediate) { integrity = report }
        }
    }

    val findings: List<SecurityFinding> = remember(editor.tabs) {
        DevForgeSecurityScanner.scan(editor.tabs.map { it.name to it.content })
    }
    val performance = remember(editor.tabs) {
        PerformanceBudgetSnapshot(
            openTabs = editor.tabs.size,
            dirtyTabs = editor.tabs.count { it.isDirty },
            totalOpenContentChars = editor.tabs.sumOf { it.content.length },
        )
    }
    val configurationComplete = build.configuration.githubOwner.isNotBlank() &&
        build.configuration.githubRepository.isNotBlank() &&
        build.configuration.workflowFile.isNotBlank()
    val successfulBuild = build.state is com.mrredhood.devforge.core.build.BuildState.Succeeded
    val releaseGate = remember(build.state, build.artifacts, build.logs, editor.tabs, configurationComplete) {
        DevForgeReleaseQualityGate.evaluate(
            hasSuccessfulBuild = successfulBuild,
            artifactsPresent = build.artifacts.isNotEmpty(),
            logsAvailable = build.logs.isNotEmpty(),
            noUnsavedEditorChanges = editor.tabs.none { it.isDirty },
            diagnosticsClear = true,
            configurationComplete = configurationComplete,
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Quality & Reliability", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "One place for long-running operations, workspace health, security review, mobile performance budgets and release readiness.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text("Operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (operations.isEmpty()) {
                DetailField("No recorded operations", "Long-running actions can report lifecycle state here without blocking navigation.")
            } else {
                operations.take(12).forEach { operation ->
                    DetailField(
                        operation.title,
                        operation.state.name.replace('_', ' ') +
                            " · " + (operation.progressPercent?.let { it.toString() + "%" } ?: "progress unknown") +
                            (operation.statusMessage?.let { " · " + it } ?: ""),
                    )
                }
            }
        }
        item {
            Text("Workspace integrity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val report = integrity
            if (report == null) {
                DetailField("Checking…", "Validating workspace permissions, root access, metadata persistence and Git state.")
            } else {
                report.checks.forEach { check ->
                    DetailField(check.label, check.state.name + " · " + check.detail)
                }
            }
        }
        item {
            Text("Security scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (findings.isEmpty()) {
                DetailField("No obvious secret-like material detected", "Best-effort scan of currently open editor content; it is not a full repository scanner.")
            } else {
                findings.forEach { finding ->
                    DetailField(finding.path, finding.severity + " · " + finding.summary)
                }
            }
        }
        item {
            Text("Mobile performance budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DetailField(
                "Open tabs",
                performance.openTabs.toString() + "/" + performance.maxOpenTabs +
                    if (performance.openTabs <= performance.maxOpenTabs) " · within budget" else " · over budget",
            )
            DetailField(
                "Open editor content",
                performance.totalOpenContentChars.toString() + "/" + performance.maxTotalOpenContentChars + " chars" +
                    if (performance.withinBudget) " · within budget" else " · over budget",
            )
            DetailField("Dirty tabs", performance.dirtyTabs.toString())
        }
        item {
            Text("Release quality gate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            releaseGate.checks.forEach { pair ->
                val label = pair.first
                val passed = pair.second
                DetailField(
                    if (passed) "PASS · " + label else "CHECK · " + label,
                    if (passed) "Ready" else "Not satisfied yet.",
                )
            }
            DetailField(
                "Gate result",
                if (releaseGate.ready) "All current gate checks pass." else releaseGate.failed.toString() + " check(s) still need attention.",
            )
        }
    }
}

@Composable
private fun ProjectHealthPage(
    padding: PaddingValues,
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    agent: AgentActivityViewModel,
    build: BuildViewModel,
) {
    val db = DevForgeDatabase.get(editor.getApplication<Application>())
    val pending by db.approvalDao().observePending(50).collectAsState(initial = emptyList())
    val builds by db.buildReceiptDao().observeRecent(10).collectAsState(initial = emptyList())
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MetricRow("Workspace", workspace.workspace?.name ?: "None", Icons.Default.Folder) }
        item { MetricRow("Files in current folder", workspace.entries.size.toString(), Icons.Default.List) }
        item { MetricRow("Open tabs", editor.tabs.size.toString(), Icons.Default.Code) }
        item { MetricRow("Dirty tabs", editor.tabs.count { it.isDirty }.toString(), Icons.Default.Warning) }
        item { MetricRow("Agent tasks", agent.tasks.size.toString(), Icons.Default.Science) }
        item { MetricRow("Pending approvals", pending.size.toString(), Icons.Default.Security) }
        item { MetricRow("Recent builds", builds.size.toString(), Icons.Default.Build) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Current build state", fontWeight = FontWeight.Bold)
                    Text(build.state.toString())
                    build.runSnapshot?.let {
                        Text("Run #" + it.runNumber + " · " + it.status + " · " + (it.conclusion ?: "running"))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AIRunsPage(
    padding: PaddingValues,
    agent: AgentCenterViewModel,
    selectedTaskId: String?,
    onSelect: (String) -> Unit,
) {
    val selected = agent.tasks.firstOrNull { it.taskId == selectedTaskId }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        "Select an agent run to inspect its lifecycle, model, current step, approval binding and result.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(agent.tasks, key = { it.taskId }) { task ->
                Card(
                    onClick = { onSelect(task.taskId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        val end = task.completedAtEpochMs ?: System.currentTimeMillis()
                        val elapsedSeconds = ((end - (task.startedAtEpochMs ?: task.createdAtEpochMs)).coerceAtLeast(0L) / 1000L)
                        Text(task.status + " · step " + task.currentStep + "/" + task.stepCount, style = MaterialTheme.typography.bodySmall)
                        Text(
                            (task.modelName ?: task.modelId ?: "Unknown model") + " · " + (task.modelProviderId ?: "Unknown provider") +
                                " · " + elapsedSeconds + "s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item { OutlinedButton(onClick = { onSelect("") }) { Text("← All runs") } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailField("Title", selected.title)
                    DetailField("Status", selected.status)
                    DetailField("Instruction", selected.instruction)
                    DetailField("Provider", selected.modelProviderId ?: "Unknown")
                    DetailField("Model", selected.modelName ?: selected.modelId ?: "Unknown")
                    DetailField("Step", selected.currentStep.toString() + "/" + selected.stepCount)
                    DetailField(
                        "Elapsed",
                        (((selected.completedAtEpochMs ?: System.currentTimeMillis()) - (selected.startedAtEpochMs ?: selected.createdAtEpochMs))
                            .coerceAtLeast(0L) / 1000L).toString() + "s",
                    )
                    DetailField("Last tool", selected.lastToolId ?: "None")
                    DetailField("Approval", selected.approvalId ?: "None")
                    selected.errorMessage?.let { DetailField("Error", it) }
                    selected.result?.let { DetailField("Result", it) }
                }
            }
        }
    }
}

@Composable
private fun TestingPage(
    padding: PaddingValues,
    build: BuildViewModel,
    onOpenBuild: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Testing & CI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Build Center remains the execution surface for workflow dispatch, cancellation, logs and artifacts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenBuild) { Text("Open Build Center") }
                }
            }
        }
        item { MetricRow("Current run", build.runSnapshot?.let { "#" + it.runNumber + " " + (it.conclusion ?: it.status) } ?: "None", Icons.Default.Science) }
        item { MetricRow("Artifacts", build.artifacts.size.toString(), Icons.Default.Download) }
        item { MetricRow("Log jobs", build.logs.size.toString(), Icons.Default.List) }
        build.monitoringMessage?.let { item { DetailField("Monitor", it) } }
    }
}

@Composable
private fun DependenciesPage(
    padding: PaddingValues,
    workspace: WorkspaceViewModel,
) {
    val candidates = workspace.entries.filter {
        it.name in setOf(
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "gradle.properties", "libs.versions.toml", "pom.xml", "package.json", "Cargo.toml",
            "pubspec.yaml", "requirements.txt", "go.mod",
        )
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Dependency manifests in the current workspace folder.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (candidates.isEmpty()) {
            item { DetailField("Result", "No common manifest is visible in the current folder. Use Files search for nested manifests.") }
        } else {
            items(candidates, key = { it.uri.toString() }) { entry ->
                DetailField(entry.name, "Open this file in Editor to inspect and modify dependencies.")
            }
        }
    }
}

@Composable
private fun BuildDiagnosisPage(
    padding: PaddingValues,
    build: BuildViewModel,
) {
    val findings = remember(build.logs) { BuildFailureDiagnosis.analyze(build.logs) }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DetailField(
                "Result",
                if (findings.isEmpty()) {
                    "No known failure signature was detected in the currently loaded workflow logs."
                } else {
                    findings.size.toString() + " grouped finding(s) detected."
                },
            )
        }
        items(findings, key = { it.category + it.jobName + it.evidence }) { finding ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(finding.category + " · " + finding.severity, fontWeight = FontWeight.Bold)
                    Text(finding.summary)
                    Text("Job: " + finding.jobName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(finding.evidence, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OfflineQueuePage(
    padding: PaddingValues,
    queue: OfflineActionQueue,
    items: List<com.mrredhood.devforge.core.storage.OfflineQueueItem>,
    onRefresh: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Offline queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Supported remote intents can be retained and retried later. Replay handlers remain capability-specific.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            DetailField(item.title, item.type + " · attempts " + item.attempts + " · created " + item.createdAt)
        }
        if (items.isEmpty()) item { DetailField("Queue", "Empty") }
    }
}

@Composable
private fun BackupPage(
    padding: PaddingValues,
    onExport: () -> Unit,
    onImport: () -> Unit,
    message: String?,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Workspace backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Exports bounded editor/workspace metadata and open-tab content. Credentials are never included.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExport) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export")
                        }
                        OutlinedButton(onClick = onImport) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
private fun ReleasePage(
    padding: PaddingValues,
    build: BuildViewModel,
    onOpenBuild: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Release center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Release APK and AAB targets are executed through the Build Center and protected remote workflow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenBuild) { Text("Open Build Center") }
                }
            }
        }
        item { MetricRow("Last run", build.runSnapshot?.let { "#" + it.runNumber } ?: "None", Icons.Default.Cloud) }
        item { DetailField("Signing", "Release CI validates the configured signing material and artifact signature before upload.") }
    }
}

@Composable
private fun GitHubHubPage(
    padding: PaddingValues,
    repositories: GitHubRepositoryViewModel,
    onOpenCreate: () -> Unit,
) {
    LaunchedEffect(Unit) { repositories.refreshRepositories() }
    val activity: GitHubRepositoryActivityViewModel = viewModel()
    val repoState = repositories.state
    val activityState = activity.state

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(repoState.accountName ?: "GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Connected repositories, issues and pull requests", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onOpenCreate) { Text("Create repository") }
            }
        }
        repoState.error?.let { item { DetailField("Repository error", it) } }
        item {
            if (repoState.repositories.isEmpty()) {
                DetailField("Repositories", "No connected repositories are visible.")
            } else {
                Text("Repositories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        items(repoState.repositories, key = { it.fullName }) { repo ->
            Card(
                onClick = { activity.select(repo) },
                colors = CardDefaults.cardColors(
                    containerColor = if (activityState.selectedRepository?.fullName == repo.fullName) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(repo.fullName, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (repo.isPrivate) "Private" else "Public") + " · " + repo.defaultBranch,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        activityState.selectedRepository?.let { selected ->
            item {
                Text(selected.fullName + " activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                if (activityState.loading) {
                    Text("Loading open issues and pull requests…")
                } else {
                    activityState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (!activityState.loading) {
                item { Text("Issues (" + activityState.issues.size + ")", style = MaterialTheme.typography.labelLarge) }
                items(activityState.issues, key = { "issue-" + it.number }) { item ->
                    DetailField(
                        "#" + item.number + " " + item.title,
                        (item.author.ifBlank { "unknown" }) + " · " + (item.createdAt ?: "unknown date"),
                    )
                }
                item { Text("Pull requests (" + activityState.pullRequests.size + ")", style = MaterialTheme.typography.labelLarge) }
                items(activityState.pullRequests, key = { "pr-" + it.number }) { item ->
                    DetailField(
                        "#" + item.number + " " + item.title,
                        (item.author.ifBlank { "unknown" }) + " · " + (item.createdAt ?: "unknown date"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationExpansionPage(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { DetailField("Automation flow", "Trigger → condition → agent → approval → patch → build → test → notify.") }
        item { DetailField("Recovery", "Automation state is durable and overlap-protected; use the existing Automation screen to edit triggers, schedules and actions.") }
    }
}

@Composable
private fun CodeIntelligencePage(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { DetailField("Current editor intelligence", "Language detection, syntax highlighting, folding, symbols, diagnostics, find/replace, quick open, command palette and direct file editing. AI is provided through Chat.") }
        item { DetailField("LSP-compatible intelligence", "DevForge now exposes a bounded internal LSP-compatible facade for diagnostics, symbols, definitions, references and safe text rename. External language-server processes remain optional and are not bundled.") }
    }
}

@Composable
private fun ExtensionsPage(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sandboxed extensions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Extension manifests can now be validated and registered with bounded capabilities. Execution still has to cross DevForge's capability, policy, approval, path-scope and audit boundaries; unrestricted extension/MCP execution is not enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { DetailField("Security contract", "Extension output is untrusted. Secrets remain in protected credential storage and workspace boundaries remain canonical.") }
    }
}

private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

@Composable
private fun DetailField(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value)
        }
    }
}
