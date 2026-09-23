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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildOutputSettings
import com.mrredhood.devforge.core.build.BuildTarget
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.build.GitHubBuildSettingsStore

@Composable
fun GitHubRepositoryScreen(
    buildViewModel: BuildViewModel,
    onBack: () -> Unit,
    viewModel: GitHubRepositoryViewModel = viewModel(),
    connectionViewModel: GitHubConnectionViewModel = viewModel(),
) {
    val state = viewModel.state
    val connected = connectionViewModel.snapshot.state is GitHubConnectionState.Connected
    val context = LocalContext.current
    val settingsStore = remember(context) { GitHubBuildSettingsStore(context) }
    var outputSettings by remember { mutableStateOf(BuildOutputSettings()) }
    var deleteTarget by remember { mutableStateOf<GitHubRepository?>(null) }
    var deleteConfirmation by remember { mutableStateOf("") }
    var workflowDeleteSelection by remember(state.selectedRepository?.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var workflowDeleteTargets by remember { mutableStateOf<List<GitHubWorkflow>?>(null) }
    var editingRepository by remember { mutableStateOf<GitHubRepository?>(null) }

    LaunchedEffect(connected) {
        if (connected && state.repositories.isEmpty()) viewModel.refreshRepositories()
    }

    LaunchedEffect(state.selectedRepository?.fullName) {
        state.selectedRepository?.let { repository ->
            outputSettings = settingsStore.get(repository.owner, repository.name)
        }
    }

    LaunchedEffect(state.selectedRepository?.id, state.workflows.map { it.id }) {
        val ids = state.workflows.map { it.id }.toSet()
        workflowDeleteSelection = workflowDeleteSelection.intersect(ids)
    }

    if (editingRepository != null) {
        GitHubRepositoryEditScreen(
            repository = editingRepository!!,
            onBack = { editingRepository = null },
            onSaved = {
                viewModel.applyRepositoryUpdate(it)
                editingRepository = null
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
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
                        "The repository list reached DevForge's 10,000-repository safety ceiling. Refine the search to narrow the current list.",
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
                    RepositoryRow(
                        repository = repository,
                        selected = state.selectedRepository?.id == repository.id,
                        deleting = viewModel.deletingRepositoryId == repository.id,
                        onClick = { viewModel.selectRepository(repository) },
                        onDelete = {
                            deleteConfirmation = ""
                            deleteTarget = repository
                        },
                    )
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
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        enabled = false,
                                        label = { Text(if (repository.isPrivate) "Private" else "Public") },
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = { editingRepository = repository },
                                        enabled = viewModel.deletingRepositoryId == null,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            "Edit repository",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            deleteConfirmation = ""
                                            deleteTarget = repository
                                        },
                                        enabled = viewModel.deletingRepositoryId == null,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Delete repository",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
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
                            Column(
                                Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Build outputs & reports", fontWeight = FontWeight.Bold)
                                Text(
                                    "These settings are saved separately for this repository and are sent to the DevForge GitHub Actions workflow when you build.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text("Build target", fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    BuildTarget.entries.forEach { target ->
                                        FilterChip(
                                            selected = outputSettings.target == target,
                                            onClick = {
                                                outputSettings = outputSettings.copy(target = target)
                                                settingsStore.set(repository.owner, repository.name, outputSettings)
                                            },
                                            label = { Text(target.label) },
                                        )
                                    }
                                }
                                Text(
                                    "Release APK/AAB builds require the configured DevForge release-signing secrets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                BuildSettingRow(
                                    "Build selected APK/AAB",
                                    "Actually build the selected output on GitHub Actions.",
                                    outputSettings.buildArtifact,
                                ) {
                                    outputSettings = outputSettings.copy(buildArtifact = it)
                                    settingsStore.set(repository.owner, repository.name, outputSettings)
                                }
                                BuildSettingRow(
                                    "Upload build artifact",
                                    "Keep the APK/AAB available in GitHub Actions for direct in-app download.",
                                    outputSettings.publishArtifacts,
                                ) {
                                    outputSettings = outputSettings.copy(publishArtifacts = it)
                                    settingsStore.set(repository.owner, repository.name, outputSettings)
                                }
                                BuildSettingRow(
                                    "Lint report",
                                    "Run Android lint and upload its HTML/XML report as a downloadable artifact.",
                                    outputSettings.lintReport,
                                ) {
                                    outputSettings = outputSettings.copy(lintReport = it)
                                    settingsStore.set(repository.owner, repository.name, outputSettings)
                                }
                                BuildSettingRow(
                                    "Unit-test report",
                                    "Run unit tests and upload the generated test results/reports.",
                                    outputSettings.unitTestReport,
                                ) {
                                    outputSettings = outputSettings.copy(unitTestReport = it)
                                    settingsStore.set(repository.owner, repository.name, outputSettings)
                                }
                                BuildSettingRow(
                                    "Dependency report",
                                    "Generate a Gradle dependency report and upload it for in-app download.",
                                    outputSettings.dependencyReport,
                                ) {
                                    outputSettings = outputSettings.copy(dependencyReport = it)
                                    settingsStore.set(repository.owner, repository.name, outputSettings)
                                }
                                Text(
                                    if (outputSettings.publishArtifacts) {
                                        "Build files and enabled reports will appear under Build Center after the run."
                                    } else {
                                        "Build files are not uploaded when artifact publishing is disabled."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Workflows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Select one workflow or choose multiple for deletion.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (workflowDeleteSelection.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        workflowDeleteTargets = state.workflows.filter { it.id in workflowDeleteSelection }
                                    },
                                    enabled = viewModel.deletingWorkflowIds.isEmpty(),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(5.dp))
                                    Text("Delete " + workflowDeleteSelection.size)
                                }
                            }
                        }
                    }
                    if (state.workflows.isEmpty() && !state.isLoading) {
                        item {
                            Text(
                                "No GitHub Actions workflow was found in this repository.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.workflows, key = { it.id }) { workflow ->
                        WorkflowRow(
                            workflow = workflow,
                            selected = state.selectedWorkflow?.id == workflow.id,
                            deleteSelected = workflow.id in workflowDeleteSelection,
                            deleting = workflow.id in viewModel.deletingWorkflowIds,
                            onClick = { viewModel.selectWorkflow(workflow) },
                            onDeleteToggle = { checked ->
                                workflowDeleteSelection = if (checked) {
                                    workflowDeleteSelection + workflow.id
                                } else {
                                    workflowDeleteSelection - workflow.id
                                }
                            },
                            onDeleteSingle = {
                                workflowDeleteTargets = listOf(workflow)
                            },
                        )
                    }
                    item {
                        Text(
                            "Deleting a workflow removes its .github/workflows/*.yml or *.yaml definition file by committing that deletion to the repository branch. Historical workflow runs remain on GitHub.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                val workflowFile = state.selectedWorkflow?.path ?: ".github/workflows/android.yml"
                                buildViewModel.configureGitHubRepository(
                                    owner = repository.owner,
                                    repository = repository.name,
                                    defaultBranch = repository.defaultBranch,
                                    workflowFile = workflowFile,
                                    outputSettings = outputSettings,
                                )
                                onBack()
                            },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.selectedWorkflow?.state == "active") {
                                    "Use repository & workflow"
                                } else {
                                    "Use repository"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    workflowDeleteTargets?.let { targets ->
        val repository = state.selectedRepository
        if (repository != null) {
            AlertDialog(
                onDismissRequest = {
                    if (viewModel.deletingWorkflowIds.isEmpty()) workflowDeleteTargets = null
                },
                title = { Text(if (targets.size == 1) "Delete workflow?" else "Delete workflows?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (targets.size == 1) {
                                "Delete the selected GitHub Actions workflow from " + repository.fullName + "?"
                            } else {
                                "Delete " + targets.size + " selected workflows from " + repository.fullName + "?"
                            },
                        )
                        Text(
                            "DevForge deletes the workflow definition files from the repository branch. Existing workflow run history is not removed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        targets.forEach { workflow ->
                            Text("• " + workflow.name + " — " + workflow.path, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteWorkflows(
                                repository.owner,
                                repository.name,
                                repository.defaultBranch,
                                targets,
                            ) {
                                workflowDeleteSelection = emptySet()
                                workflowDeleteTargets = null
                            }
                        },
                        enabled = viewModel.deletingWorkflowIds.isEmpty(),
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { workflowDeleteTargets = null },
                        enabled = viewModel.deletingWorkflowIds.isEmpty(),
                    ) { Text("Cancel") }
                },
            )
        }
    }

    if (deleteTarget != null) {
        val repository = deleteTarget!!
        AlertDialog(
            onDismissRequest = {
                if (viewModel.deletingRepositoryId == null) {
                    deleteTarget = null
                    deleteConfirmation = ""
                }
            },
            title = { Text("Delete repository permanently?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This permanently deletes ${repository.fullName} from GitHub. DevForge cannot restore it.",
                    )
                    Text(
                        "Type ${repository.name} to confirm.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = deleteConfirmation,
                        onValueChange = { deleteConfirmation = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Repository name") },
                        enabled = viewModel.deletingRepositoryId == null,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRepository(repository) {
                            deleteTarget = null
                            deleteConfirmation = ""
                        }
                    },
                    enabled = deleteConfirmation.trim() == repository.name &&
                        viewModel.deletingRepositoryId == null,
                ) {
                    if (viewModel.deletingRepositoryId == repository.id) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete permanently")
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        deleteConfirmation = ""
                    },
                    enabled = viewModel.deletingRepositoryId == null,
                ) { Text("Cancel") }
            },
        )
    }
}


@Composable
private fun BuildSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun RepositoryRow(
    repository: GitHubRepository,
    selected: Boolean,
    deleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (repository.isPrivate) Icons.Default.Lock else Icons.Default.Source, null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(repository.fullName, fontWeight = FontWeight.SemiBold)
                Text(
                    repository.defaultBranch,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = !deleting,
            ) {
                if (deleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Delete, contentDescription = "Delete repository")
                }
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun WorkflowRow(
    workflow: GitHubWorkflow,
    selected: Boolean,
    deleteSelected: Boolean,
    deleting: Boolean,
    onClick: () -> Unit,
    onDeleteToggle: (Boolean) -> Unit,
    onDeleteSingle: () -> Unit,
) {
    val active = workflow.state == "active"
    Card(
        onClick = { if (active) onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(
                checked = deleteSelected,
                onCheckedChange = onDeleteToggle,
                enabled = !deleting,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.AccountTree, null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(workflow.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    workflow.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    workflow.state,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onDeleteSingle, enabled = !deleting) {
                Icon(Icons.Default.Delete, contentDescription = "Delete workflow")
            }
        }
    }
}

