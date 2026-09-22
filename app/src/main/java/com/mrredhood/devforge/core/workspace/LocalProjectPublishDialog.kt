package com.mrredhood.devforge.core.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.github.GitHubRepository
import com.mrredhood.devforge.core.github.GitHubRepositoryViewModel
import kotlinx.coroutines.launch

@Composable
fun LocalProjectPublishDialog(
    workspace: WorkspaceViewModel,
    github: GitHubRepositoryViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<GitHubRepository?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!publishing) onDismiss() },
        title = { Text(if (selected == null) "Upload local project" else "Confirm upload") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    workspace.workspace?.name?.let { "Source: " + it } ?: "No local workspace selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (github.state.repositories.isEmpty() && !github.state.isLoading) {
                    Text(
                        "No accessible repositories are loaded. Connect GitHub or refresh the repository list.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (selected == null) {
                    LazyColumn(
                        Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(github.state.repositories, key = { it.id }) { repository ->
                            TextButton(
                                onClick = { selected = repository; error = null },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    Text(repository.fullName)
                                    Text(
                                        repository.defaultBranch + " · " + if (repository.isPrivate) "Private" else "Public",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "This creates a normal commit on " + selected!!.fullName + " that makes its selected branch match the current local project. " +
                            "Remote files absent from the local project may be deleted by that commit. History is preserved; DevForge never force-pushes.",
                    )
                    TextButton(
                        onClick = { selected = null; error = null },
                        enabled = !publishing,
                    ) { Text("Choose another repository") }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (selected == null) {
                TextButton(
                    onClick = { github.refreshRepositories() },
                    enabled = !github.state.isLoading,
                ) { Text("Refresh") }
            } else {
                Button(
                    onClick = {
                        val target = selected ?: return@Button
                        publishing = true
                        error = null
                        scope.launch {
                            workspace.publishLocalWorkspaceToGitHub(
                                owner = target.owner,
                                repositoryName = target.name,
                                branch = target.defaultBranch,
                            ).onSuccess {
                                publishing = false
                                workspace.showWorkspaceMessage(it)
                                onDismiss()
                            }.onFailure {
                                publishing = false
                                error = it.message ?: "Unable to upload the local project."
                            }
                        }
                    },
                    enabled = !publishing && workspace.workspace != null && workspace.remoteWorkspace == null,
                ) {
                    if (publishing) {
                        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (publishing) "Uploading…" else "Upload project")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !publishing) { Text("Cancel") }
        },
    )
}
