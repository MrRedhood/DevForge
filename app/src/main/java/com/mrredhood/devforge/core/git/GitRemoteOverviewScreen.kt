package com.mrredhood.devforge.core.git

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.github.GitHubBranchesResult
import com.mrredhood.devforge.core.github.GitHubCommitDetails
import com.mrredhood.devforge.core.github.GitHubCommitDetailsResult
import com.mrredhood.devforge.core.github.GitHubCommitHistoryEntry
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.github.GitHubPendingChanges
import com.mrredhood.devforge.core.github.GitHubRepositoryResult
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceRemote
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GitRemoteOverviewViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaces = WorkspaceDatabaseRepository(application)
    private val store = GitHubWorkspaceStore(application)
    private val gateway = GitHubRepositoryGateway(CredentialSecurityStore(application))

    var remote by mutableStateOf<GitHubWorkspaceRemote?>(null)
        private set
    var repositoryDescription by mutableStateOf<String?>(null)
        private set
    var branches by mutableStateOf<List<String>>(emptyList())
        private set
    var commits by mutableStateOf<List<GitHubCommitHistoryEntry>>(emptyList())
        private set
    var selectedBranch by mutableStateOf("main")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var selectedCommitDetails by mutableStateOf<GitHubCommitDetails?>(null)
        private set
    var isLoadingCommitDetails by mutableStateOf(false)
        private set
    var isRevertingCommit by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            workspaces.activeWorkspace.collectLatest { workspace ->
                remote = workspace?.let { store.get(it.id) }
                refresh()
            }
        }
    }

    fun refresh() {
        val active = remote ?: run {
            branches = emptyList()
            commits = emptyList()
            repositoryDescription = null
            message = "No GitHub-backed workspace is active."
            return
        }
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val repository = gateway.getRepository(active.owner, active.repository)
            val branchResult = gateway.listBranches(active.owner, active.repository)
            val commitResult = gateway.listCommits(active.owner, active.repository, selectedBranch.ifBlank { active.branch }, 50)
            withContext(Dispatchers.Main.immediate) {
                repositoryDescription = when (repository) {
                    is GitHubRepositoryResult.Success -> null
                    is GitHubRepositoryResult.Failure -> repository.message
                }
                branches = when (branchResult) {
                    is GitHubBranchesResult.Success -> branchResult.branches
                    is GitHubBranchesResult.Failure -> emptyList()
                }
                commits = when (commitResult) {
                    is com.mrredhood.devforge.core.github.GitHubCommitHistoryResult.Success -> commitResult.commits
                    is com.mrredhood.devforge.core.github.GitHubCommitHistoryResult.Failure -> emptyList()
                }
                message = when {
                    repositoryDescription != null -> repositoryDescription
                    branchResult is GitHubBranchesResult.Failure -> branchResult.message
                    commitResult is com.mrredhood.devforge.core.github.GitHubCommitHistoryResult.Failure -> commitResult.message
                    else -> null
                }
                isLoading = false
            }
        }
    }

    fun selectBranch(branch: String) {
        selectedBranch = branch
        val active = remote ?: return
        store.save(active.copy(branch = branch))
        selectedCommitDetails = null
        refresh()
    }

    fun selectCommit(commit: GitHubCommitHistoryEntry) {
        val active = remote ?: return
        selectedCommitDetails = null
        isLoadingCommitDetails = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = gateway.getCommitDetails(active.owner, active.repository, commit.sha)
            withContext(Dispatchers.Main.immediate) {
                isLoadingCommitDetails = false
                selectedCommitDetails = when (result) {
                    is GitHubCommitDetailsResult.Success -> result.value
                    is GitHubCommitDetailsResult.Failure -> {
                        message = result.message
                        null
                    }
                }
            }
        }
    }

    fun dismissCommitDetails() {
        selectedCommitDetails = null
    }

    fun revertSelectedCommit() {
        val active = remote ?: return
        val details = selectedCommitDetails ?: return
        if (isRevertingCommit) return
        isRevertingCommit = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = gateway.revertCommit(
                owner = active.owner,
                repository = active.repository,
                branch = active.branch,
                commitSha = details.sha,
                message = "Revert " + details.sha.take(12) + ": " + details.subject.take(220),
            )
            withContext(Dispatchers.Main.immediate) {
                isRevertingCommit = false
                when (result) {
                    is com.mrredhood.devforge.core.github.GitHubCommitResult.Success -> {
                        message = "Reverted " + details.sha.take(12) + " and pushed the reverse commit to " + active.branch + "."
                        selectedCommitDetails = null
                        refresh()
                    }
                    is com.mrredhood.devforge.core.github.GitHubCommitResult.Failure -> message = result.message
                }
            }
        }
    }
}

@Composable
fun GitRemoteOverviewScreen(
    viewModel: GitRemoteOverviewViewModel = viewModel(),
    onCommitPending: () -> Unit = {},
    onOpenCommitHistory: () -> Unit = {},
    onOpenDiffs: () -> Unit = {},
) {
    val remote = viewModel.remote
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Git", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text(
                            if (remote == null) "GitHub repository activity" else remote.owner + "/" + remote.repository,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = viewModel::refresh, enabled = !viewModel.isLoading) {
                        Icon(Icons.Default.Refresh, "Refresh GitHub activity")
                    }
                }
            }

            if (remote == null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(
                            "Open a GitHub repository from Files to see commits, branches, and remote activity here.",
                            Modifier.padding(18.dp),
                        )
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Source, null, Modifier.size(38.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(remote.owner + "/" + remote.repository, fontWeight = FontWeight.ExtraBold)
                                Text("Direct GitHub workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Current branch: " + remote.branch, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::refresh,
                            enabled = !viewModel.isLoading,
                        ) {
                            Text("Pull")
                        }
                        val pending = GitHubPendingChanges.batch(remote.workspaceId)
                        Button(
                            onClick = onCommitPending,
                            enabled = pending != null,
                        ) {
                            Text("Push")
                        }
                        OutlinedButton(
                            onClick = onOpenDiffs,
                        ) {
                            Text("View diffs")
                        }
                    }
                }

                item {
                    Text("Branches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Commit history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Open the full history and commit actions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                        } else {
                            Button(
                                onClick = onOpenCommitHistory,
                                enabled = viewModel.commits.isNotEmpty(),
                            ) {
                                Text("View all")
                            }
                        }
                    }
                }

                if (viewModel.commits.isEmpty() && !viewModel.isLoading) {
                    item { Text("No commits were returned for this branch.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                viewModel.message?.let { message ->
                    item {
                        OutlinedButton(onClick = viewModel::refresh, enabled = !viewModel.isLoading) {
                            Text(message)
                        }
                    }
                }
            }
        }
    }
}
