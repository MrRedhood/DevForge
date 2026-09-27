package com.mrredhood.devforge.core.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiCdCenterScreen(
    onBack: () -> Unit,
    build: BuildViewModel = viewModel(),
) {
    val health = build.ciHealth
    val running = build.runningWorkflows

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CI/CD", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
                        Text("Pipeline health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                health == null -> "No recent CI data has been loaded."
                                health.failed == 0 && health.running == 0 -> "Recent workflow history has no recorded failures."
                                health.failed > 0 -> "Recent workflow history contains failed runs."
                                else -> "Workflows are currently running."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (health != null) {
                            Text(
                                health.total.toString() + " recent · " +
                                    health.successful + " passed · " +
                                    health.failed + " failed · " +
                                    health.running + " running",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            health.latestWorkflow?.let { Text("Latest workflow: " + it, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("Live workflows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = build::refreshRunningWorkflows) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Text("Refresh")
                            }
                        }
                        if (running.isEmpty()) {
                            Text("No queued or in-progress workflows.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            running.take(10).forEach { run ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("#" + run.runNumber + " · " + run.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        run.status + " · " + run.branch + " · " + run.event,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Configured workflow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(build.configuration.workflowFile)
                        Text(
                            "Target: " + build.configuration.target.label + " · reports: " +
                                listOfNotNull(
                                    if (build.configuration.lintReport) "lint" else null,
                                    if (build.configuration.unitTestReport) "unit tests" else null,
                                    if (build.configuration.dependencyReport) "dependencies" else null,
                                ).ifEmpty { listOf("none") }.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
