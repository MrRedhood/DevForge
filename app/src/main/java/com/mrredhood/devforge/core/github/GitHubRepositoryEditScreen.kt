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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GitHubRepositoryEditScreen(
    repository: GitHubRepository,
    onBack: () -> Unit,
    onSaved: (GitHubRepository) -> Unit,
    viewModel: GitHubRepositoryEditViewModel = viewModel(),
) {
    val state = viewModel.state
    var visibilityOpen by remember { mutableStateOf(false) }

    LaunchedEffect(repository.id) { viewModel.load(repository) }
    LaunchedEffect(state.saved?.id, state.saved?.name) {
        state.saved?.let(onSaved)
    }

    val form = state.form
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = !state.saving) { Text("Cancel") }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("Edit repository", fontWeight = FontWeight.Bold)
                Text(repository.fullName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { viewModel.update(form.copy(name = it.take(100))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repository name") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { viewModel.update(form.copy(description = it.take(5000))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 3,
                )
            }
            item {
                OutlinedTextField(
                    value = form.homepage,
                    onValueChange = { viewModel.update(form.copy(homepage = it.take(2000))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Homepage URL") },
                    singleLine = true,
                )
            }
            item {
                Column {
                    OutlinedButton(onClick = { visibilityOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Visibility: " + form.visibility.replaceFirstChar { it.uppercase() })
                    }
                    DropdownMenu(
                        expanded = visibilityOpen,
                        onDismissRequest = { visibilityOpen = false },
                    ) {
                        listOf("public", "private").forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    viewModel.update(form.copy(visibility = value))
                                    visibilityOpen = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = form.defaultBranch,
                    onValueChange = { viewModel.update(form.copy(defaultBranch = it.take(255))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default branch") },
                    singleLine = true,
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EditSwitchRow("Issues", form.hasIssues) { viewModel.update(form.copy(hasIssues = it)) }
                        EditSwitchRow("Projects", form.hasProjects) { viewModel.update(form.copy(hasProjects = it)) }
                        EditSwitchRow("Wiki", form.hasWiki) { viewModel.update(form.copy(hasWiki = it)) }
                        EditSwitchRow("Discussions", form.hasDiscussions) { viewModel.update(form.copy(hasDiscussions = it)) }
                        EditSwitchRow("Downloads", form.hasDownloads) { viewModel.update(form.copy(hasDownloads = it)) }
                        EditSwitchRow("Delete head branch after merge", form.deleteBranchOnMerge) { viewModel.update(form.copy(deleteBranchOnMerge = it)) }
                        EditSwitchRow("Allow squash merge", form.allowSquashMerge) { viewModel.update(form.copy(allowSquashMerge = it)) }
                        EditSwitchRow("Allow merge commit", form.allowMergeCommit) { viewModel.update(form.copy(allowMergeCommit = it)) }
                        EditSwitchRow("Allow rebase merge", form.allowRebaseMerge) { viewModel.update(form.copy(allowRebaseMerge = it)) }
                        EditSwitchRow("Allow auto-merge", form.allowAutoMerge) { viewModel.update(form.copy(allowAutoMerge = it)) }
                    }
                }
            }
            item { Text("Merge defaults", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                OutlinedTextField(
                    value = form.squashMergeTitle,
                    onValueChange = { viewModel.update(form.copy(squashMergeTitle = it.take(80))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Squash merge title") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = form.squashMergeMessage,
                    onValueChange = { viewModel.update(form.copy(squashMergeMessage = it.take(80))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Squash merge message") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = form.mergeCommitTitle,
                    onValueChange = { viewModel.update(form.copy(mergeCommitTitle = it.take(80))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merge commit title") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = form.mergeCommitMessage,
                    onValueChange = { viewModel.update(form.copy(mergeCommitMessage = it.take(80))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merge commit message") },
                    singleLine = true,
                )
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.saving && form.name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.saving) "Saving…" else "Save repository")
                    }
                    OutlinedButton(onClick = onBack, enabled = !state.saving) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun EditSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
