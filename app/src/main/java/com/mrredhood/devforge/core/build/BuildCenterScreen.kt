package com.mrredhood.devforge.core.build

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.github.BuildHistoryEntry
import com.mrredhood.devforge.core.github.GitHubArtifact
import com.mrredhood.devforge.core.github.GitHubJobLog
import com.mrredhood.devforge.core.github.GitHubWorkflowRun
import com.mrredhood.devforge.core.github.GitHubRepositoryScreen

@Composable
fun BuildCenterScreen(
    repositoryPickerOpen: Boolean = false,
    onRepositoryPickerChange: (Boolean) -> Unit = {},
) {
    val model: BuildViewModel = viewModel()
    val configuration = model.configuration
    val dispatchEnabled = model.capabilities.githubDispatch == CapabilityAvailability.Available &&
        model.state !is BuildState.Dispatching &&
        model.state !is BuildState.Running &&
        model.state !is BuildState.Cancelling &&
        model.state !is BuildState.AwaitingApproval
    val context = LocalContext.current

    if (repositoryPickerOpen) {
        GitHubRepositoryScreen(
            buildViewModel = model,
            onBack = { onRepositoryPickerChange(false) },
        )
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text("Build Center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Remote-first Android builds through GitHub Actions. DevForge stays lightweight on-device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Workflow setup", fontWeight = FontWeight.Bold)
                        Text(
                            "Create or replace the CI, Android UI-test, or release validation workflow directly in the selected GitHub repository.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
    onClick = model::createCiWorkflow,
    enabled = !model.workflowSetupBusy &&
        configuration.githubOwner.isNotBlank() &&
        configuration.githubRepository.isNotBlank(),
) {
                                Text("Create CI")
                            }
                            Button(
    onClick = model::createUiWorkflow,
    enabled = !model.workflowSetupBusy &&
        configuration.githubOwner.isNotBlank() &&
        configuration.githubRepository.isNotBlank(),
) {
                                Text("Create UI")
                            }
                            Button(
    onClick = model::createReleaseWorkflow,
    enabled = !model.workflowSetupBusy &&
        configuration.githubOwner.isNotBlank() &&
        configuration.githubRepository.isNotBlank(),
) {
                                Text("Create release")
                            }
                        }
                        model.workflowSetupMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.contains("failed", true) || it.contains("invalid", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("GitHub source", fontWeight = FontWeight.Bold)
                        if (configuration.githubOwner.isBlank() || configuration.githubRepository.isBlank()) {
                            Text("No repository selected. Choose a repository first; CI, UI-test and release workflows can be created here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { onRepositoryPickerChange(true) }) { Text("Choose repository") }
                        } else {
                            Text("${configuration.githubOwner}/${configuration.githubRepository}", style = MaterialTheme.typography.titleMedium)
                            Text("Workflow: ${configuration.workflowFile}\nBranch: ${configuration.branch}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onRepositoryPickerChange(true) }) { Text("Change") }
                        }
                    }
                }
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Build target", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BuildTarget.entries.forEach { target ->
                                FilterChip(
                                    selected = configuration.target == target,
                                    onClick = { model.selectTarget(target) },
                                    enabled = model.state !is BuildState.Running &&
                                        model.state !is BuildState.Dispatching &&
                                        model.state !is BuildState.Cancelling &&
                                        model.state !is BuildState.AwaitingApproval,
                                    label = { Text(target.label) },
                                )
                            }
                        }
                        Text(
                            configuration.target.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (configuration.target != BuildTarget.DebugApk) {
                            Text(
                                "Release APK/AAB builds require the DevForge release-signing secrets. The build fails before Gradle if signing is not configured.",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Repository build outputs", fontWeight = FontWeight.Bold)
                        Text(
                            "Artifact: " +
                                (if (configuration.buildArtifact) "enabled" else "disabled") +
                                " · Upload: " +
                                (if (configuration.publishArtifacts) "enabled" else "disabled"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Reports: " +
                                listOfNotNull(
                                    if (configuration.lintReport) "lint" else null,
                                    if (configuration.unitTestReport) "unit tests" else null,
                                    if (configuration.dependencyReport) "dependencies" else null,
                                ).ifEmpty { listOf("none") }.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Change these options when choosing the repository. They are sent to the DevForge Actions workflow for each build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Execution plan", fontWeight = FontWeight.Bold)
                        BuildDetailRow("Repository", if (configuration.githubOwner.isBlank()) "Not selected" else "${configuration.githubOwner}/${configuration.githubRepository}")
                        BuildDetailRow("Workflow", configuration.workflowFile)
                        BuildDetailRow("Branch", configuration.branch)
                        BuildDetailRow("Gradle task", configuration.buildTask)
                        BuildDetailRow("Artifact", configuration.artifactName)
                    }
                }
            }
            model.ciHealth?.let { health ->
                item { CiHealthCard(health, onRefresh = model::refreshRunningWorkflows) }
            }
            item {
                RunningWorkflowsCard(
                    runs = model.runningWorkflows,
                    onRefresh = model::refreshRunningWorkflows,
                    onOpen = { url -> openUrl(context, url) },
                )
            }
            if (model.runSnapshot != null) {
                item { RunSummaryCard(model, onOpen = { url -> openUrl(context, url) }) }
                item { ArtifactCard(model) }
                item { LogsCard(model) }
            }
            if (model.monitoringMessage != null) {
                item {
                    val pending = model.state is BuildState.AwaitingApproval
                    Card(colors = CardDefaults.cardColors(containerColor = if (pending) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                        Text(model.monitoringMessage.orEmpty(), modifier = Modifier.padding(16.dp), color = if (pending) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Remote capabilities", fontWeight = FontWeight.Bold)
                        }
                        CapabilityRow("Workflow dispatch", model.capabilities.githubDispatch)
                        CapabilityRow("Build cancellation", model.capabilities.cancelBuild)
                        CapabilityRow("Live logs", model.capabilities.liveLogs)
                        CapabilityRow("Artifact discovery", model.capabilities.artifacts)
                    }
                }
            }
            if (model.history.isNotEmpty()) {
                item { HistoryHeader() }
                items(model.history, key = { it.runId }) { entry -> HistoryRow(entry, onOpen = { url -> openUrl(context, url) }) }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = model::startSelectedBuild,
                        enabled = dispatchEnabled && configuration.buildArtifact,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                model.state is BuildState.AwaitingApproval -> "Awaiting approval…"
                                model.state is BuildState.Dispatching -> "Dispatching…"
                                model.state is BuildState.Running -> "Building…"
                                else -> "Build ${configuration.target.label}"
                            },
                        )
                    }
                    if (model.state is BuildState.Running && model.runSnapshot != null) {
                        TextButton(
                            onClick = model::cancelRun,
                            enabled = model.capabilities.cancelBuild == CapabilityAvailability.Available,
                        ) { Text("Cancel build") }
                    }
                    if (model.runSnapshot != null) {
                        TextButton(onClick = model::refreshRun) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }
                    TextButton(
                        onClick = model::resetToReady,
                        enabled = model.state !is BuildState.Dispatching && model.state !is BuildState.Running && model.state !is BuildState.Cancelling && model.state !is BuildState.AwaitingApproval,
                    ) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun CiHealthCard(
    health: CiHealthSnapshot,
    onRefresh: () -> Unit,
) {
    val label = when (health.state) {
        CiHealthState.HEALTHY -> "Healthy"
        CiHealthState.RUNNING -> "Running"
        CiHealthState.FAILED -> "Failed"
        CiHealthState.MIXED -> "Mixed"
        CiHealthState.UNKNOWN -> "Unknown"
    }
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CI health", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                health.total.toString() + " recent runs · " +
                    health.successful + " passed · " +
                    health.failed + " failed · " +
                    health.running + " running · " +
                    health.cancelled + " cancelled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            health.latestWorkflow?.let {
                Text("Latest workflow: " + it, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RunningWorkflowsCard(
    runs: List<GitHubWorkflowRun>,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Running workflows", fontWeight = FontWeight.Bold)
                    Text("Live run numbers from GitHub Actions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            if (runs.isEmpty()) {
                Text("No workflow runs are currently queued or in progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                runs.forEach { run ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#" + run.runNumber + " · " + run.name, fontWeight = FontWeight.SemiBold)
                            Text(run.status + " · " + run.branch + " · " + run.event, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        run.htmlUrl?.let { url ->
                            IconButton(onClick = { onOpen(url) }) {
                                Icon(Icons.Default.OpenInNew, "Open workflow run")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunSummaryCard(model: BuildViewModel, onOpen: (String) -> Unit) {
    val run = model.runSnapshot ?: return
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Run #${run.runNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${run.name} • ${run.branch}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                    Text(run.conclusion ?: run.status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            BuildDetailRow("Event", run.event)
            BuildDetailRow("Updated", run.updatedAt ?: "Unknown")
            run.htmlUrl?.let { url ->
                TextButton(onClick = { onOpen(url) }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Open run on GitHub")
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(model: BuildViewModel) {
    val outputs = model.artifacts.filter {
        it.name.contains("build-output", true) ||
            it.name.contains("apk", true) ||
            it.name.contains("aab", true)
    }
    val reports = model.artifacts.filter { it !in outputs }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Build artifacts & reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "GitHub Actions artifacts are pulled directly into DevForge. Download extracts APK/AAB files and enabled reports to the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Build files", fontWeight = FontWeight.SemiBold)
            if (outputs.isEmpty()) {
                Text("No APK/AAB artifact is available for this run.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                outputs.forEach { artifact -> ArtifactRow(artifact, model) }
            }
            Text("Reports", fontWeight = FontWeight.SemiBold)
            if (reports.isEmpty()) {
                Text("No report artifact is available for this run.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                reports.forEach { artifact -> ArtifactRow(artifact, model) }
            }
            model.artifactMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.contains("failed", true) || message.contains("error", true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ArtifactRow(artifact: GitHubArtifact, model: BuildViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(artifact.name, fontWeight = FontWeight.SemiBold)
            Text(
                formatBytes(artifact.sizeBytes) + " • " + if (artifact.expired) "Expired" else "Available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (artifact.expired) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    "Expired",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            TextButton(
                onClick = { model.downloadArtifact(artifact) },
                enabled = model.downloadingArtifactId == null,
            ) {
                if (model.downloadingArtifactId == artifact.id) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("Download to device")
            }
        }
    }
}

@Composable
private fun LogsCard(model: BuildViewModel) {
    val logs = model.logs
    val truncated = model.logsTruncated
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (logs.isEmpty()) "No job log output loaded yet." else "Job output",
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = model::reloadLogs) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reload")
                }
            }
            if (logs.isNotEmpty()) {
                logs.forEach { job ->
                    Text("${job.jobName} • ${job.conclusion ?: job.status}", fontWeight = FontWeight.SemiBold)
                    SelectionContainer {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                            Text(job.text.ifBlank { "No log output yet." }, modifier = Modifier.fillMaxWidth().height(220.dp).verticalScroll(rememberScrollState()).padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (truncated) Text("Log output is bounded for mobile performance; older output may be omitted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun HistoryHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.History, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Build history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Current DevForge session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(entry: BuildHistoryEntry, onOpen: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("#${entry.runNumber} • ${entry.configuration.target.label}", fontWeight = FontWeight.SemiBold)
                Text("${entry.configuration.githubOwner}/${entry.configuration.githubRepository} • ${entry.conclusion ?: entry.state}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.htmlUrl?.let { url -> IconButton(onClick = { onOpen(url) }) { Icon(Icons.Default.OpenInNew, "Open run") } }
        }
    }
}

@Composable
private fun BuildDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.65f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CapabilityRow(label: String, availability: CapabilityAvailability) {
    val status = when (availability) {
        CapabilityAvailability.Available -> "Available"
        CapabilityAvailability.Unavailable -> "Not connected"
        CapabilityAvailability.NotConfigured -> "Not configured"
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(status, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
    if (!uri.scheme.equals("https", ignoreCase = true) || !uri.host.equals("github.com", ignoreCase = true)) return
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "0 B"
    value < 1024L -> "$value B"
    value < 1024L * 1024L -> "${value / 1024L} KB"
    else -> "${value / (1024L * 1024L)} MB"
}
