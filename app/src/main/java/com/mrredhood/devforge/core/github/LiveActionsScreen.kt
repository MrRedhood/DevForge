package com.mrredhood.devforge.core.github

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveActionsScreen(onBack: () -> Unit) {
    val vm: LiveActionsViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live GitHub Actions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Workflow status refreshes from GitHub every 5 seconds. Tap a run to open its job logs; the selected run stays expanded without a continuous log reload.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            vm.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }

            if (vm.isLoading && vm.runs.isEmpty()) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (vm.runs.isEmpty() && !vm.isLoading) {
                item { Text("No recent or running workflows found.") }
            }

            items(vm.runs, key = { it.run.id }) { item ->
                LiveRunCard(item, onClick = { vm.toggle(item.run.id) })
            }
        }
    }
}

@Composable
private fun LiveRunCard(item: LiveActionRunUi, onClick: () -> Unit) {
    val running = item.run.status in setOf(
        "queued",
        "in_progress",
        "waiting",
        "requested",
        "pending",
    )
    val transition = rememberInfiniteTransition(label = "run-" + item.run.id)
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.alpha(if (running) alpha else 1f),
                    shape = CircleShape,
                    color = if (running) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "#" + item.run.runNumber + " " + item.run.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        item.owner + "/" + item.repository,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        item.run.branch + " · " + item.run.status + " · " + (item.run.conclusion ?: "running"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (item.expanded) {
                when {
                    item.logsLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    item.logsError != null -> Text(item.logsError, color = MaterialTheme.colorScheme.error)
                    item.jobs.isEmpty() -> Text("Job logs are not available yet.")
                    else -> item.jobs.forEach { job ->
                        Text(job.jobName, style = MaterialTheme.typography.labelLarge)
                        Text(
                            job.status + " · " + (job.conclusion ?: "running"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            job.text.takeLast(9_000),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
