package com.mrredhood.devforge.core.git

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitHistoryScreen(
    initialCommitSha: String? = null,
    onBack: () -> Unit,
    viewModel: GitRemoteOverviewViewModel = viewModel(),
) {
    var reverseDialogOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(initialCommitSha, viewModel.commits) {
        val sha = initialCommitSha?.trim().orEmpty()
        if (sha.isNotBlank()) {
            viewModel.commits.firstOrNull { it.sha == sha }?.let(viewModel::selectCommit)
        }
    }

    val remote = viewModel.remote
    val selected = viewModel.selectedCommitDetails

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text("Commit history", fontWeight = FontWeight.ExtraBold)
                        Text(
                            remote?.let { it.owner + "/" + it.repository } ?: "GitHub repository",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back to Git")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !viewModel.isLoading && !viewModel.isLoadingCommitDetails && !viewModel.isRevertingCommit,
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh commits")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (remote != null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Branch", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                viewModel.branches.forEach { branch ->
                                    FilterChip(
                                        selected = branch == remote.branch,
                                        onClick = { viewModel.selectBranch(branch) },
                                        label = { Text(branch, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.message != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(viewModel.message.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = viewModel::refresh) { Text("Retry") }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Commits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "All commits returned for the selected branch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (viewModel.commits.isEmpty() && !viewModel.isLoading) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text("No commits were returned for this branch.", Modifier.padding(16.dp))
                    }
                }
            } else {
                items(viewModel.commits, key = { it.sha }) { commit ->
                    Card(
                        onClick = { viewModel.selectCommit(commit) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected?.sha == commit.sha) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(commit.subject.ifBlank { "(no commit message)" }, fontWeight = FontWeight.SemiBold)
                            Text(
                                commit.sha.take(12) + " · " + commit.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            commit.authoredAt?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (viewModel.isLoadingCommitDetails) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Loading commit details…")
                        }
                    }
                }
            }

            selected?.let { details ->
                item {
                    val created = details.changedFiles.count { it.status == "added" }
                    val modified = details.changedFiles.count { it.status == "modified" }
                    val deleted = details.changedFiles.count { it.status == "deleted" }
                    val folders = details.changedFiles
                        .flatMap { file ->
                            listOfNotNull(
                                file.path.substringBeforeLast('/', "").takeIf { it.isNotBlank() },
                                file.previousPath?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() },
                            )
                        }
                        .flatMap { path ->
                            generateSequence(path) {
                                it.substringBeforeLast('/', "").takeIf(String::isNotBlank)
                            }.toList()
                        }
                        .toSet()
                        .size

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Selected commit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(details.subject.ifBlank { "(no commit message)" }, fontWeight = FontWeight.ExtraBold)
                            Text(
                                details.sha,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                details.author + (details.authoredAt?.let { " · " + it } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CommitMetric("Files", details.changedFiles.size)
                                CommitMetric("Created", created)
                                CommitMetric("Modified", modified)
                                CommitMetric("Deleted", deleted)
                                CommitMetric("Folders", folders)
                            }

                            Text(
                                "Code changes: +" + details.additions + " / -" + details.deletions,
                                style = MaterialTheme.typography.bodySmall,
                            )

                            details.fullMessage
                                .takeIf { it.isNotBlank() && it.trim() != details.subject.trim() }
                                ?.let {
                                    Text(it.take(2000), style = MaterialTheme.typography.bodySmall)
                                }

                            if (details.parents.isNotEmpty()) {
                                Text("Parent commit", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                details.parents.take(4).forEach { parent ->
                                    Text(
                                        parent,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Text("Changed files", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            details.changedFiles.take(120).forEach { file ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            file.status.uppercase(),
                                            Modifier.weight(.24f),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                        Column(Modifier.weight(.76f)) {
                                            Text(file.path, maxLines = 1)
                                            file.previousPath?.let {
                                                Text(
                                                    "from " + it,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                "+" + file.additions + " / -" + file.deletions,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { reverseDialogOpen = true },
                                enabled = !viewModel.isRevertingCommit,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (viewModel.isRevertingCommit) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Reverse this commit & push")
                            }
                        }
                    }
                }
            }
        }
    }

    if (reverseDialogOpen && selected != null) {
        AlertDialog(
            onDismissRequest = { reverseDialogOpen = false },
            title = { Text("Reverse commit?") },
            text = {
                Text(
                    "This creates a new reverse commit on " +
                        (remote?.branch ?: "the selected branch") +
                        " and pushes it to GitHub. It does not rewrite existing history.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reverseDialogOpen = false
                        viewModel.revertSelectedCommit()
                    },
                ) { Text("Reverse & push") }
            },
            dismissButton = {
                TextButton(onClick = { reverseDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CommitMetric(label: String, value: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label + " " + value,
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
