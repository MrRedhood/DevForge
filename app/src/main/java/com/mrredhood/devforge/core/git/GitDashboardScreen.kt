package com.mrredhood.devforge.core.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GitDashboardScreen(viewModel: GitViewModel = viewModel()) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Git", fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("Repository metadata and staged/worktree status", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.inspectWorkspace() }) { Icon(Icons.Default.Refresh, "Refresh Git status") }
                }
            }
            when (val state = viewModel.state) {
                GitDetectionState.NotDetected -> item { GitEmptyCard("No Git repository detected in the active workspace.") }
                GitDetectionState.Detecting -> item { GitLoadingCard("Inspecting repository metadata…") }
                is GitDetectionState.Unsupported -> item { GitUnsupportedCard(state.reason) }
                is GitDetectionState.Detected -> {
                    item { RepositoryCard(state.repository) }
                    item { WorkspaceStatusCard(viewModel.workspaceStatus, viewModel.isInspectingStatus) }
                    item { GitOperationsCard() }
                    item { GitHistoryControlsCard() }
                    if (state.repository.branches.isNotEmpty()) {
                        item {
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Local branches", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Metadata discovered from refs and packed-refs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        items(state.repository.branches.take(MAX_BRANCHES_VISIBLE), key = { it.name }) { branch -> BranchRow(branch) }
                        if (state.repository.branches.size > MAX_BRANCHES_VISIBLE) {
                            item { Text("+${state.repository.branches.size - MAX_BRANCHES_VISIBLE} more branches in repository metadata", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoryCard(repository: GitRepositoryState) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Source, "Repository", Modifier.padding(10.dp)) }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(repository.branchName ?: "Detached HEAD", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text(statusAvailabilityLabel(repository.statusAvailability), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            GitMetadataRow("Root", repository.rootUri.lastPathSegment ?: "Workspace")
            GitMetadataRow("Origin", repository.remoteUrl ?: "Not configured")
            GitMetadataRow("HEAD", repository.headRevision?.take(12) ?: if (repository.detachedHead) "Detached" else repository.branchName ?: "Unavailable")
        }
    }
}

private fun statusAvailabilityLabel(availability: GitStatusAvailability): String = when (availability) {
    GitStatusAvailability.IndexAndHeadAware -> "HEAD + index + worktree status available"
    GitStatusAvailability.IndexAwareWorktree -> "Index/worktree status available"
    GitStatusAvailability.MetadataOnly -> "Repository metadata available"
}

@Composable
private fun GitMetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(.28f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(.72f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkspaceStatusCard(status: GitWorkspaceStatus?, inspecting: Boolean) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Git status", fontWeight = FontWeight.Bold)
                    Text(
                        when (status?.mode) {
                            GitStatusAvailability.IndexAndHeadAware -> "Compared HEAD, index, and working tree"
                            GitStatusAvailability.IndexAwareWorktree -> "Working tree compared with the Git index"
                            GitStatusAvailability.MetadataOnly, null -> "Bounded observation fallback; Git index status unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (inspecting) CircularProgressIndicator(Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            if (status == null) {
                Text("Waiting for repository status…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusMetric("Clean", status.files.count { it.gitStatus == GitFileStatus.Clean }.toString())
                    StatusMetric("Modified", status.files.count { it.gitStatus == GitFileStatus.Modified }.toString())
                    StatusMetric("Staged", status.files.count { it.gitStatus == GitFileStatus.Staged }.toString())
                    StatusMetric("Both", status.files.count { it.gitStatus == GitFileStatus.StagedAndModified }.toString())
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusMetric("Untracked", status.files.count { it.gitStatus == GitFileStatus.Untracked }.toString())
                    StatusMetric("Deleted", status.files.count { it.gitStatus == GitFileStatus.Deleted }.toString())
                    StatusMetric("Conflict", status.files.count { it.gitStatus == GitFileStatus.Conflict }.toString())
                    StatusMetric("Unchecked", status.files.count { it.gitStatus == GitFileStatus.Unchecked }.toString())
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    status.message ?: if (status.truncated) "Status scan reached its safety limit; results are partial." else "Status scan completed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.state == GitStatusConfidence.Partial || status.truncated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GitOperationsCard() {
    val model: GitViewModel = viewModel()
    var commitOpen by rememberSaveable { mutableStateOf(false) }
    var branchOpen by rememberSaveable { mutableStateOf(false) }
    var commitMessage by rememberSaveable { mutableStateOf("") }
    var branchName by rememberSaveable { mutableStateOf("") }
    val changed = model.workspaceStatus?.files
        ?.filter { it.gitStatus !in setOf(GitFileStatus.Clean, GitFileStatus.Unchecked) }
        ?.take(MAX_CHANGE_ROWS)
        .orEmpty()

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Commit, null)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Native Git operations", fontWeight = FontWeight.Bold)
                    Text("Bounded local Git mutations through SAF; no shell commands are used.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (model.isExecuting) CircularProgressIndicator(Modifier.size(21.dp))
            }

            if (changed.isEmpty()) {
                Text("No pending file changes were observed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                changed.forEach { file -> GitChangeRow(file, model) }
                if ((model.workspaceStatus?.files?.count { it.gitStatus !in setOf(GitFileStatus.Clean, GitFileStatus.Unchecked) } ?: 0) > MAX_CHANGE_ROWS) {
                    Text("Only the first $MAX_CHANGE_ROWS changes are shown; mutation remains bounded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { commitOpen = true },
                    enabled = model.capabilities.commit == CapabilityAvailability.Available && !model.isExecuting,
                ) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Commit")
                }
                OutlinedButton(
                    onClick = { branchOpen = true },
                    enabled = model.capabilities.createBranch == CapabilityAvailability.Available && !model.isExecuting,
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Branch")
                }
            }

            Text("Remote Git", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "HTTPS GitHub transport uses the connected Keystore credential, validates origin access before each operation, and never force-pushes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { model.fetchRemote() },
                    enabled = model.capabilities.fetchRemote == CapabilityAvailability.Available && !model.isExecuting,
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Fetch")
                }
                OutlinedButton(
                    onClick = { model.pullRemote() },
                    enabled = model.capabilities.pullRemote == CapabilityAvailability.Available && !model.isExecuting,
                ) {
                    Icon(Icons.Default.Sync, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Pull")
                }
                Button(
                    onClick = { model.pushRemote() },
                    enabled = model.capabilities.pushRemote == CapabilityAvailability.Available && !model.isExecuting,
                ) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Push")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapabilityChip("Stage", model.capabilities.stage)
                CapabilityChip("Unstage", model.capabilities.unstage)
                CapabilityChip("Commit", model.capabilities.commit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapabilityChip("Fetch", model.capabilities.fetchRemote)
                CapabilityChip("Pull", model.capabilities.pullRemote)
                CapabilityChip("Push", model.capabilities.pushRemote)
            }

            model.operationMessage?.let { message ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (commitOpen) {
        AlertDialog(
            onDismissRequest = { commitOpen = false },
            title = { Text("Create commit") },
            text = {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.commit(commitMessage)
                        commitMessage = ""
                        commitOpen = false
                    },
                    enabled = commitMessage.isNotBlank() && !model.isExecuting,
                ) { Text("Commit") }
            },
            dismissButton = { TextButton(onClick = { commitOpen = false }) { Text("Cancel") } },
        )
    }

    if (branchOpen) {
        AlertDialog(
            onDismissRequest = { branchOpen = false },
            title = { Text("Create local branch") },
            text = {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text("Branch name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.createBranch(branchName)
                        branchName = ""
                        branchOpen = false
                    },
                    enabled = branchName.isNotBlank() && !model.isExecuting,
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { branchOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GitChangeRow(file: GitWorkspaceFile, model: GitViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.path, fontWeight = FontWeight.SemiBold)
                Text(file.gitStatus.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (file.gitStatus) {
                GitFileStatus.Untracked, GitFileStatus.Modified, GitFileStatus.Deleted, GitFileStatus.StagedAndModified ->
                    TextButton(onClick = { model.stage(file.path) }, enabled = model.capabilities.stage == CapabilityAvailability.Available && !model.isExecuting) { Text("Stage") }
                GitFileStatus.Staged ->
                    TextButton(onClick = { model.unstage(file.path) }, enabled = model.capabilities.unstage == CapabilityAvailability.Available && !model.isExecuting) { Text("Unstage") }
                GitFileStatus.Conflict -> Text("Resolve first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }
}

@Composable
private fun CapabilityChip(label: String, availability: CapabilityAvailability) {
    FilterChip(
        selected = availability == CapabilityAvailability.Available,
        onClick = {},
        enabled = false,
        label = { Text(label) },
    )
}

@Composable
private fun BranchRow(branch: GitBranch) {
    val model: GitViewModel = viewModel()
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (branch.isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Source, null, Modifier.size(21.dp)); Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(branch.name, fontWeight = FontWeight.SemiBold)
                Text(when { branch.isCurrent -> "Current branch"; branch.revision != null -> branch.revision.take(12); else -> "Local branch" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!branch.isCurrent) {
                IconButton(
                    onClick = { model.deleteBranch(branch.name) },
                    enabled = model.capabilities.deleteBranch == CapabilityAvailability.Available && !model.isExecuting,
                ) { Icon(Icons.Default.Delete, "Delete branch") }
            } else {
                Icon(Icons.Default.ChevronRight, null, Modifier.alpha(.4f))
            }
        }
    }
}

@Composable
private fun GitEmptyCard(message: String) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Icon(Icons.Default.InsertDriveFile, null, Modifier.size(38.dp)); Spacer(Modifier.height(12.dp))
            Text("No repository here", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GitLoadingCard(message: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(23.dp)); Text(message, Modifier.padding(start = 14.dp)) }
    }
}

@Composable
private fun GitUnsupportedCard(message: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, null)
            Column(Modifier.padding(start = 12.dp)) { Text("Repository needs a supported access path", fontWeight = FontWeight.Bold); Text(message, Modifier.padding(top = 6.dp)) }
        }
    }
}

private const val MAX_BRANCHES_VISIBLE = 20
private const val MAX_CHANGE_ROWS = 30
