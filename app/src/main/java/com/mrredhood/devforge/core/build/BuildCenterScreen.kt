package com.mrredhood.devforge.core.build

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
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
import com.mrredhood.devforge.core.github.GitHubRepositoryScreen

@Composable
fun BuildCenterScreen() {
    val model: BuildViewModel = viewModel()
    var choosingRepository by rememberSaveable { mutableStateOf(false) }
    val configuration = model.configuration
    val dispatchEnabled = model.capabilities.githubDispatch == CapabilityAvailability.Available &&
        model.state !is BuildState.Dispatching &&
        model.state !is BuildState.Running &&
        model.state !is BuildState.AwaitingApproval
    val context = LocalContext.current

    if (choosingRepository) {
        GitHubRepositoryScreen(buildViewModel = model, onBack = { choosingRepository = false })
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
            item { BuildStatusCard(model) }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("GitHub source", fontWeight = FontWeight.Bold)
                        if (configuration.githubOwner.isBlank() || configuration.githubRepository.isBlank()) {
                            Text("No repository selected. Discover a repository and an active Actions workflow before remote dispatch can be configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { choosingRepository = true }) { Text("Choose repository") }
                        } else {
                            Text("${configuration.githubOwner}/${configuration.githubRepository}", style = MaterialTheme.typography.titleMedium)
                            Text("Workflow: ${configuration.workflowFile}\nBranch: ${configuration.branch}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { choosingRepository = true }) { Text("Change") }
                        }
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Build target", fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BuildTarget.entries.forEach { target ->
                                FilterChip(
                                    selected = configuration.target == target,
                                    onClick = { model.selectTarget(target) },
                                    enabled = model.state !is BuildState.Running && model.state !is BuildState.Dispatching && model.state !is BuildState.AwaitingApproval,
                                    label = { Text(target.label) },
                                )
                            }
                        }
                        Text(configuration.target.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (configuration.target != BuildTarget.DebugApk) {
                            Text("Release dispatch is protected by the Approval Center and requires the repository's configured signing contract.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        }
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
            if (model.runSnapshot != null) {
                item { RunSummaryCard(model, onOpen = { url -> openUrl(context, url) }) }
                item { ArtifactCard(model.artifacts) }
                item { LogsCard(model.logs, model.logsTruncated) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = model::requestDispatch, enabled = dispatchEnabled) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (model.state) {
                                is BuildState.AwaitingApproval -> "Awaiting approval…"
                                is BuildState.Dispatching -> "Dispatching…"
                                else -> "Start remote build"
                            },
                        )
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
                        enabled = model.state !is BuildState.Dispatching && model.state !is BuildState.Running && model.state !is BuildState.AwaitingApproval,
                    ) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun BuildStatusCard(model: BuildViewModel) {
    val state = model.state
    val (title, detail) = when (state) {
        BuildState.Idle -> "Idle" to "No build is configured."
        is BuildState.Ready -> "Ready" to "Configuration prepared; no remote run has started."
        is BuildState.AwaitingApproval -> "Awaiting approval" to "Review this build in Approval Center before DevForge dispatches it."
        is BuildState.Dispatching -> "Dispatching" to "Submitting the authenticated GitHub Actions request…"
        is BuildState.Running -> "Running" to "GitHub Actions run #${state.runId} is active."
        is BuildState.Succeeded -> "Succeeded" to "Artifact ${state.artifactName} is available."
        is BuildState.Failed -> "Failed" to state.message
        is BuildState.Cancelled -> "Cancelled" to "Run #${state.runId} was cancelled."
    }
    val active = state is BuildState.Dispatching || state is BuildState.Running
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state is BuildState.Succeeded) Icons.Default.CheckCircle else Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
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
private fun ArtifactCard(artifacts: List<GitHubArtifact>) {
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Artifacts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (artifacts.isEmpty()) Text("No artifacts are available for this run yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else artifacts.forEach { artifact -> ArtifactRow(artifact) }
        }
    }
}

@Composable
private fun ArtifactRow(artifact: GitHubArtifact) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(artifact.name, fontWeight = FontWeight.SemiBold)
            Text("${formatBytes(artifact.sizeBytes)} • ${if (artifact.expired) "Expired" else "Available"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = MaterialTheme.shapes.small, color = if (artifact.expired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
            Text(if (artifact.expired) "Expired" else "Ready", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LogsCard(logs: List<GitHubJobLog>, truncated: Boolean) {
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (logs.isEmpty()) {
                Text("No job logs are available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
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
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "0 B"
    value < 1024L -> "$value B"
    value < 1024L * 1024L -> "${value / 1024L} KB"
    else -> "${value / (1024L * 1024L)} MB"
}
