package com.mrredhood.devforge.core.github

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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildViewModel

@Composable
fun GitHubRepositoryScreen(
    buildViewModel: BuildViewModel,
    onBack: () -> Unit,
    viewModel: GitHubRepositoryViewModel = viewModel(),
    connectionViewModel: GitHubConnectionViewModel = viewModel(),
) {
    val state = viewModel.state
    val connected = connectionViewModel.snapshot.state is GitHubConnectionState.Connected

    LaunchedEffect(connected) {
        if (connected && state.repositories.isEmpty()) viewModel.refreshRepositories()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("GitHub repository", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    state.accountName?.let { "Signed in as @$it" } ?: "Select a repository for remote builds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (connected) {
                IconButton(onClick = viewModel::refreshRepositories, enabled = !state.isLoading) {
                    Icon(Icons.Default.Refresh, "Refresh repositories")
                }
            }
        }

        if (!connected) {
            GitHubConnectionScreen(
                viewModel = connectionViewModel,
                onConnectionChanged = { viewModel.refreshRepositories() },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::updateQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search repositories") },
                        singleLine = true,
                    )
                }

                state.error?.let { message ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                if (state.isLoading && state.repositories.isEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }

                if (state.truncated) item {
                    Text(
                        "Showing the newest 300 accessible repositories. Refine the search to choose from this list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val filtered = state.repositories.filter { repository ->
                    val query = state.query.trim()
                    query.isEmpty() || repository.fullName.contains(query, ignoreCase = true)
                }

                if (filtered.isEmpty() && !state.isLoading) {
                    item {
                        Text(
                            "No accessible repositories match this search.",
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(filtered, key = { it.id }) { repository ->
                    RepositoryRow(repository, selected = state.selectedRepository?.id == repository.id) {
                        viewModel.selectRepository(repository)
                    }
                }

                state.selectedRepository?.let { repository ->
                    item {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Validated repository", fontWeight = FontWeight.Bold)
                                Text(repository.fullName, style = MaterialTheme.typography.titleMedium)
                                Text("Default branch: ${repository.defaultBranch}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = true, onClick = {}, label = { Text(if (repository.isPrivate) "Private" else "Public") })
                                }
                            }
                        }
                    }

                    item {
                        Text("Workflow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    if (state.workflows.isEmpty() && !state.isLoading) {
                        item {
                            Text(
                                "No GitHub Actions workflows were found in this repository.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(state.workflows, key = { it.id }) { workflow ->
                        WorkflowRow(workflow, selected = state.selectedWorkflow?.id == workflow.id) {
                            viewModel.selectWorkflow(workflow)
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                val workflow = state.selectedWorkflow ?: return@Button
                                buildViewModel.configureGitHubRepository(
                                    owner = repository.owner,
                                    repository = repository.name,
                                    defaultBranch = repository.defaultBranch,
                                    workflowFile = workflow.path,
                                )
                                onBack()
                            },
                            enabled = state.selectedWorkflow?.state == "active" && !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Use repository and workflow")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoryRow(
    repository: GitHubRepository,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (repository.isPrivate) Icons.Default.Lock else Icons.Default.Source, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(repository.fullName, fontWeight = FontWeight.SemiBold)
                Text(repository.defaultBranch, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun WorkflowRow(
    workflow: GitHubWorkflow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val active = workflow.state == "active"
    Card(
        onClick = { if (active) onClick() },
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        enabled = active,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountTree, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(workflow.name, fontWeight = FontWeight.SemiBold)
                Text(workflow.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.small, color = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer) {
                Text(workflow.state, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
