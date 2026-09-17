package com.mrredhood.devforge.core.build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.mrredhood.devforge.core.github.GitHubRepositoryScreen

@Composable
fun BuildCenterScreen() {
    val model: BuildViewModel = viewModel()
    var choosingRepository by rememberSaveable { mutableStateOf(false) }
    val configuration = model.configuration

    if (choosingRepository) {
        GitHubRepositoryScreen(
            buildViewModel = model,
            onBack = { choosingRepository = false },
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
                    Text(
                        "Remote-first Android builds through GitHub Actions. DevForge stays lightweight on-device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                BuildStatusCard(model.state)
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("GitHub source", fontWeight = FontWeight.Bold)
                        if (configuration.githubOwner.isBlank() || configuration.githubRepository.isBlank()) {
                            Text(
                                "No repository selected. Discover a repository and an active Actions workflow before remote dispatch can be configured.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { choosingRepository = true }) {
                                Text("Choose repository")
                            }
                        } else {
                            Text("${configuration.githubOwner}/${configuration.githubRepository}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Workflow: ${configuration.workflowFile}\nBranch: ${configuration.branch}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextButton(onClick = { choosingRepository = true }) { Text("Change") }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Text("  Build target", fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BuildTarget.entries.forEach { target ->
                                FilterChip(
                                    selected = configuration.target == target,
                                    onClick = { model.selectTarget(target) },
                                    label = { Text(target.label) },
                                )
                            }
                        }
                        Text(configuration.target.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
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

            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Text("  Remote capabilities", fontWeight = FontWeight.Bold)
                        }
                        CapabilityRow("Workflow dispatch", model.capabilities.githubDispatch)
                        CapabilityRow("Live logs", model.capabilities.liveLogs)
                        CapabilityRow("Artifact discovery", model.capabilities.artifacts)
                        Text(
                            "Repository and workflow discovery is now available. Dispatch execution remains separately gated until its authenticated mutation path is validated.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = model::requestDispatch) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text("Start remote build")
                    }
                    TextButton(onClick = model::resetToReady) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildStatusCard(state: BuildState) {
    val (title, detail) = when (state) {
        BuildState.Idle -> "Idle" to "No build is configured."
        is BuildState.Ready -> "Ready" to "Configuration prepared; no remote run has started."
        is BuildState.Dispatching -> "Dispatching" to "Submitting the workflow request…"
        is BuildState.Running -> "Running" to "GitHub Actions run #${state.runId} is active."
        is BuildState.Succeeded -> "Succeeded" to "Artifact ${state.artifactName} is available."
        is BuildState.Failed -> "Not started" to state.message
        is BuildState.Cancelled -> "Cancelled" to "Run #${state.runId} was cancelled."
    }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(if (state is BuildState.Succeeded) Icons.Default.CheckCircle else Icons.Default.Info, contentDescription = null)
                Text("  $title", fontWeight = FontWeight.Bold)
            }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
