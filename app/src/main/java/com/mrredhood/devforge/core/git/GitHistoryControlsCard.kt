package com.mrredhood.devforge.core.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun GitHistoryControlsCard(viewModel: GitHistoryViewModel = viewModel()) {
    var cherryPickRevision by rememberSaveable { mutableStateOf("") }
    val repository = viewModel.repository
    val branches = repository?.branches.orEmpty()
    val selected = viewModel.selectedBranch
    val canRun = selected != null && !viewModel.isExecuting && viewModel.conflictSession == null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Branch & history", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Checkout, merge, rebase and cherry-pick run only against a clean, fully inspected workspace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (viewModel.isExecuting || viewModel.isLoadingHistory) CircularProgressIndicator(Modifier.size(22.dp))
                }

                if (branches.none { !it.isCurrent }) {
                    Text("No alternate local branches are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Target branch", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(branches.filter { !it.isCurrent }.take(MAX_HISTORY_BRANCHES), key = { it.name }) { branch ->
                            FilterChip(selected = branch.name == selected, onClick = { viewModel.selectBranch(branch.name) }, label = { Text(branch.name) }, enabled = viewModel.conflictSession == null)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { viewModel.switchToSelectedBranch() }, enabled = canRun) { IconText(Icons.Default.CallSplit, "Switch") }
                        OutlinedButton(onClick = { viewModel.mergeSelectedBranch() }, enabled = canRun) { IconText(Icons.Default.MergeType, "Merge") }
                        OutlinedButton(onClick = { viewModel.rebaseOntoSelectedBranch() }, enabled = canRun) { IconText(Icons.Default.Replay, "Rebase") }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text("Cherry-pick commit", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = cherryPickRevision, onValueChange = { cherryPickRevision = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("40-character commit SHA") }, enabled = viewModel.conflictSession == null)
                Button(onClick = { viewModel.cherryPick(cherryPickRevision); cherryPickRevision = "" }, enabled = cherryPickRevision.trim().length == 40 && !viewModel.isExecuting && viewModel.conflictSession == null) {
                    IconText(Icons.Default.Check, "Cherry-pick")
                }

                Text("Recent commits", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (viewModel.commits.isEmpty()) {
                    Text("No readable commit history is available through the selected workspace access path.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    viewModel.commits.take(MAX_HISTORY_COMMITS).forEach { commit ->
                        CommitHistoryRow(commit = commit, selected = commit.commitId == viewModel.selectedCommit?.commitId, onClick = { viewModel.selectCommit(commit) })
                    }
                    if (viewModel.commits.size > MAX_HISTORY_COMMITS) Text("Showing the newest $MAX_HISTORY_COMMITS commits; history is bounded for mobile performance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                viewModel.selectedCommit?.let { commit ->
                    Text("Files changed in ${commit.shortId}", fontWeight = FontWeight.Bold)
                    if (viewModel.selectedCommitFiles.isEmpty()) {
                        Text("No changed files could be resolved for this commit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        viewModel.selectedCommitFiles.take(MAX_CHANGED_FILES_VISIBLE).forEach { file -> ChangedFileRow(file) { viewModel.selectFile(file.path) } }
                        if (viewModel.selectedCommitFiles.size > MAX_CHANGED_FILES_VISIBLE) Text("Only the first $MAX_CHANGED_FILES_VISIBLE changed paths are shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                viewModel.selectedFilePath?.let { path ->
                    Text("File history: $path", fontWeight = FontWeight.Bold)
                    if (viewModel.selectedFileHistory.isEmpty()) {
                        Text("No historical changes were resolved for this path.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        viewModel.selectedFileHistory.take(MAX_FILE_HISTORY_VISIBLE).forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.changeType.name, Modifier.weight(.24f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                Column(Modifier.weight(.76f)) {
                                    Text(item.subject, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("${item.commitId.take(12)} · ${item.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                viewModel.message?.let { message -> Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("When Git reports conflicts, DevForge keeps them inside a bounded isolated session. Nothing is copied back to the workspace until the conflicts are resolved and the final state is revalidated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        if (viewModel.conflictSession != null) {
            GitConflictResolverCard(viewModel)
        }
    }
}

@Composable
private fun CommitHistoryRow(commit: GitCommitHistoryEntry, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(commit.subject, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${commit.shortId} · ${commit.author} · ${commit.changedFileCount} files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                commit.authoredAtEpochMs?.let { Text(formatHistoryTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun ChangedFileRow(file: GitFileHistoryEntry, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Text(file.changeType.name, Modifier.weight(.22f), style = MaterialTheme.typography.labelSmall)
        Text(file.path, Modifier.weight(.78f), maxLines = 1)
    }
}

private fun formatHistoryTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

@Composable
private fun IconText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    androidx.compose.material3.Icon(icon, null)
    Spacer(Modifier.size(6.dp))
    Text(text)
}

private const val MAX_HISTORY_BRANCHES = 12
private const val MAX_HISTORY_COMMITS = 12
private const val MAX_CHANGED_FILES_VISIBLE = 20
private const val MAX_FILE_HISTORY_VISIBLE = 12
