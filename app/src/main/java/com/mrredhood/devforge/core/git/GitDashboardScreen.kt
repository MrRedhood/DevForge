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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GitDashboardScreen(viewModel: GitViewModel = viewModel()) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Git", fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("Repository metadata and index-aware worktree status", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    item { WorkspaceObservationCard(viewModel.workspaceStatus, viewModel.isInspectingStatus) }
                    item { GitOperationsCard() }
                    if (state.repository.branches.isNotEmpty()) {
                        item {
                            Column {
                                Text("Local branches", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Metadata discovered from refs and packed-refs", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            GitMetadataRow("HEAD", when { repository.detachedHead && repository.headRevision != null -> repository.headRevision.take(12); repository.detachedHead -> "Detached"; else -> repository.branchName ?: "Unavailable" })
        }
    }
}

private fun statusAvailabilityLabel(availability: GitStatusAvailability): String = when (availability) {
    GitStatusAvailability.IndexAwareWorktree -> "Index/worktree status available"
    GitStatusAvailability.MetadataOnly -> "Repository metadata available"
    GitStatusAvailability.NotImplemented -> "Git status unavailable"
}

@Composable
private fun GitMetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(.28f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(.72f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkspaceObservationCard(status: GitWorkspaceStatus?, inspecting: Boolean) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Git status", fontWeight = FontWeight.Bold)
                    Text(
                        when (status?.mode) {
                            GitStatusAvailability.IndexAwareWorktree -> "Working tree compared with the Git index"
                            GitStatusAvailability.MetadataOnly, null -> "Bounded observation fallback; index status unavailable"
                            GitStatusAvailability.NotImplemented -> "Git status is unavailable"
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
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatusMetric("Clean", status.files.count { it.gitStatus == GitFileStatus.Clean }.toString())
                    StatusMetric("Modified", status.files.count { it.gitStatus == GitFileStatus.Modified }.toString())
                    StatusMetric("Untracked", status.files.count { it.gitStatus == GitFileStatus.Untracked }.toString())
                    StatusMetric("Deleted", status.files.count { it.gitStatus == GitFileStatus.Deleted }.toString())
                }
                val unchecked = status.files.count { it.gitStatus == GitFileStatus.Unchecked }
                if (unchecked > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text("$unchecked file(s) could not be verified within the mobile hash/read limits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
        Text(value, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BranchRow(branch: GitBranch) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (branch.isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Source, null, Modifier.size(21.dp)); Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(branch.name, fontWeight = FontWeight.SemiBold)
                Text(when { branch.isCurrent -> "Current branch"; branch.revision != null -> branch.revision.take(12); else -> "Local branch" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.alpha(.4f))
        }
    }
}

@Composable
private fun GitOperationsCard() {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text("Native Git operations", fontWeight = FontWeight.Bold)
            Text("Index/worktree status is now available. Stage, unstage, commit, branch mutation, fetch, pull and push remain behind the capability-controlled execution layer.", modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}, enabled = false) { Text("Stage") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Commit") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Push") }
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
