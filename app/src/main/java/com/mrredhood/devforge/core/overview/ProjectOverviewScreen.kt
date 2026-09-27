package com.mrredhood.devforge.core.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.build.BuildState
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.github.GitHubPendingChangeBatch
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectOverviewScreen(
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    build: BuildViewModel,
    pendingBatch: GitHubPendingChangeBatch?,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenBuild: () -> Unit,
    onOpenGit: () -> Unit,
) {
    val dirtyCount = editor.tabs.count { it.isDirty }
    val openCount = editor.tabs.size
    val workspaceName = workspace.workspace?.name
    val remote = workspace.remoteWorkspace
    val buildStatus = when (val state = build.state) {
        BuildState.Idle -> "Not run yet"
        is BuildState.Ready -> "Ready"
        is BuildState.AwaitingApproval -> "Waiting for approval"
        is BuildState.Dispatching -> "Dispatching"
        is BuildState.Cancelling -> "Cancelling"
        is BuildState.Running -> "Running · #${state.runId}"
        is BuildState.Succeeded -> "Succeeded · ${state.artifactName}"
        is BuildState.Failed -> "Failed"
        is BuildState.Cancelled -> "Cancelled"
    }
    val hasProblem = dirtyCount > 0 || pendingBatch != null || build.state is BuildState.Failed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project overview", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize()
                .padding(padding)
                .testTag("project-overview"),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasProblem) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (hasProblem) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = null,
                        )
                        Column(
                            Modifier.weight(1f).padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                workspaceName ?: "No workspace selected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when {
                                    workspaceName == null -> "Open a project to unlock the editor, Git and builds."
                                    remote != null -> "GitHub · ${remote.owner}/${remote.repository} · ${remote.branch}"
                                    workspace.localGitHubLink != null ->
                                        "GitHub-linked local project · ${workspace.localGitHubLink!!.owner}/${workspace.localGitHubLink!!.repository}"
                                    else -> "Local workspace",
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "At a glance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OverviewRow(Icons.Default.Code, "Open files", openCount.toString())
                        OverviewRow(
                            Icons.Default.Code,
                            "Unsaved files",
                            dirtyCount.toString(),
                            valueColor = if (dirtyCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                        OverviewRow(
                            if (pendingBatch != null) Icons.Default.Cloud else Icons.Default.Folder,
                            "Remote changes",
                            pendingBatch?.changes?.size?.toString() ?: "0",
                            valueColor = if (pendingBatch != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                        OverviewRow(Icons.Default.Build, "Build", buildStatus)
                    }
                }
            }

            item {
                Text(
                    "Quick actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenEditor, modifier = Modifier.fillMaxWidth()) {
                        Text("Open editor")
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onOpenFiles, modifier = Modifier.weight(1f)) {
                            Text("Files")
                        }
                        OutlinedButton(onClick = onOpenBuild, modifier = Modifier.weight(1f)) {
                            Text("Build")
                        }
                        OutlinedButton(onClick = onOpenGit, modifier = Modifier.weight(1f)) {
                            Text("Git")
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Before you ship", fontWeight = FontWeight.SemiBold)
                        when {
                            workspaceName == null -> Text("Select or create a workspace first.")
                            dirtyCount > 0 -> Text("Save ${dirtyCount} unsaved file${if (dirtyCount == 1) "" else "s"} before building or leaving.")
                            pendingBatch != null -> Text("Review and commit ${pendingBatch.changes.size} pending GitHub change${if (pendingBatch.changes.size == 1) "" else "s"}.")
                            build.state is BuildState.Failed -> Text("Open Build Center to inspect the latest failure.")
                            else -> Text("Workspace is ready for the next edit, test, commit, or build.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}
