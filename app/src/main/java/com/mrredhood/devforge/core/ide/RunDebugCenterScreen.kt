package com.mrredhood.devforge.core.ide

import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildState
import com.mrredhood.devforge.core.build.BuildTarget
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDebugCenterScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
    build: BuildViewModel = viewModel(),
) {
    val configuration = build.configuration
    val state = build.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run & Debug", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("Back")
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
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(workspace.workspace?.name ?: "No workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (configuration.githubOwner.isBlank()) {
                                "Connect a GitHub-backed workspace to dispatch a remote debug build."
                            } else {
                                configuration.githubOwner + "/" + configuration.githubRepository + " · " + configuration.branch
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Debug target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(BuildTarget.DebugApk.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { build.startBuild(BuildTarget.DebugApk) },
                            enabled = state !is BuildState.Dispatching &&
                                state !is BuildState.Running &&
                                state !is BuildState.Cancelling &&
                                state !is BuildState.AwaitingApproval,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Build debug APK")
                        }
                        Text(
                            "DevForge uses the configured Android workflow so the resulting APK, logs and test evidence stay tied to the same build record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Execution state", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(buildStateLabel(state))
                        build.monitoringMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = build::refreshRun, modifier = Modifier.weight(1f)) { Text("Refresh") }
                            OutlinedButton(
                                onClick = build::resetToReady,
                                enabled = state !is BuildState.Running &&
                                    state !is BuildState.Dispatching &&
                                    state !is BuildState.Cancelling &&
                                    state !is BuildState.AwaitingApproval,
                                modifier = Modifier.weight(1f),
                            ) { Text("Reset") }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Device runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text((Build.MANUFACTURER + " " + Build.MODEL).trim().ifBlank { "Unknown Android device" })
                        Text(
                            "Android " + (Build.VERSION.RELEASE ?: "Unknown") + " · API " + Build.VERSION.SDK_INT,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "APK install/launch remains bridge-dependent; when no device bridge is available, use the downloaded build artifact and the existing Android package installer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun buildStateLabel(state: BuildState): String = when (state) {
    BuildState.Idle -> "Idle"
    is BuildState.Ready -> "Ready"
    is BuildState.AwaitingApproval -> "Waiting for approval"
    is BuildState.Dispatching -> "Dispatching workflow"
    is BuildState.Cancelling -> "Cancelling run #" + state.runId
    is BuildState.Running -> "Running workflow #" + state.runId
    is BuildState.Succeeded -> "Completed · artifact " + state.artifactName
    is BuildState.Failed -> "Failed · " + state.message
    is BuildState.Cancelled -> "Cancelled run #" + state.runId
}
