package com.mrredhood.devforge.core.workspace

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.github.GitHubConnectionState
import com.mrredhood.devforge.core.github.GitHubConnectionViewModel
import com.mrredhood.devforge.core.github.GitHubRepository
import com.mrredhood.devforge.core.github.GitHubRepositoryViewModel
import kotlinx.coroutines.launch

@Composable
fun GitHubWorkspaceImportScreen(
    workspaceViewModel: WorkspaceViewModel,
    onBack: () -> Unit,
    repositoryViewModel: GitHubRepositoryViewModel = viewModel(),
    connectionViewModel: GitHubConnectionViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    var opening by rememberSaveable { mutableStateOf(false) }
    var savingOnDevice by rememberSaveable { mutableStateOf(false) }
    var openMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRepository by remember { mutableStateOf<GitHubRepository?>(null) }
    val connected = connectionViewModel.snapshot.state is GitHubConnectionState.Connected
    val state = repositoryViewModel.state
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveToDeviceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { parentUri ->
        val repository = selectedRepository ?: return@rememberLauncherForActivityResult
        if (parentUri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                parentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        savingOnDevice = true
        openMessage = "Saving " + repository.fullName + " on this device…"
        scope.launch {
            workspaceViewModel.saveGitHubRepositoryLocally(
                parentUri = parentUri,
                owner = repository.owner,
                repositoryName = repository.name,
                branch = repository.defaultBranch,
            ).onSuccess {
                savingOnDevice = false
                openMessage = "Saved " + repository.fullName + " on this device."
                onBack()
            }.onFailure {
                savingOnDevice = false
                openMessage = it.message ?: "Unable to save the repository on this device."
            }
        }
    }


    LaunchedEffect(connected) {
        if (connected && state.repositories.isEmpty()) repositoryViewModel.refreshRepositories()
    }

    if (!connected) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Open GitHub repository", style = MaterialTheme.typography.titleLarge)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GitHub connection required", style = MaterialTheme.typography.titleMedium)
                    Text("Connect a GitHub credential in Settings before opening a repository.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (!opening) onBack() }, enabled = !opening) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Open GitHub repository", style = MaterialTheme.typography.titleLarge)
                Text("Browse the repository directly from GitHub.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = repositoryViewModel::refreshRepositories, enabled = !state.isLoading && !opening) {
                Icon(Icons.Default.Refresh, "Refresh repositories")
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = repositoryViewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search repositories") },
                    enabled = !opening,
                )
            }
            openMessage?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = if (opening) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (opening) CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(message, Modifier.weight(1f))
                        }
                    }
                }
            }
            state.error?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (state.isLoading && state.repositories.isEmpty()) {
                item { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            }
            val filtered = state.repositories.filter { repository ->
                val query = state.query.trim()
                query.isBlank() || repository.fullName.contains(query, true)
            }
            items(filtered, key = { it.id }) { repository ->
                Card(
                    onClick = { if (!opening) selectedRepository = repository },
                    enabled = !opening,
                    colors = CardDefaults.cardColors(containerColor = if (selectedRepository?.id == repository.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(repository.fullName, style = MaterialTheme.typography.titleMedium)
                        Text("Default branch: " + repository.defaultBranch + " • " + if (repository.isPrivate) "Private" else "Public", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            selectedRepository?.let { repository ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ready to open", style = MaterialTheme.typography.titleMedium)
                            Text(repository.fullName)
                            Text(
                                "Branch: " + repository.defaultBranch +
                                    ". Open from GitHub keeps the project remote; Save on device clones it into a local DevForge workspace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        if (opening || savingOnDevice) return@Button
                                    opening = true
                                    openMessage = "Opening " + repository.fullName + " directly from GitHub…"
                                    scope.launch {
                                        val opened = workspaceViewModel.openGitHubRepository(
                                            owner = repository.owner,
                                            repositoryName = repository.name,
                                            branch = repository.defaultBranch,
                                        )
                                        opened.onSuccess {
                                            opening = false
                                            openMessage = "Opened " + repository.fullName + " directly from GitHub."
                                            onBack()
                                        }.onFailure {
                                            opening = false
                                            openMessage = it.message ?: "Unable to open the GitHub repository."
                                        }
                                    }
                                    },
                                    enabled = !opening && !savingOnDevice,
                                ) {
                                    Icon(Icons.Default.Cloud, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open from GitHub")
                                }
                                TextButton(
                                    onClick = { saveToDeviceLauncher.launch(null) },
                                    enabled = !opening && !savingOnDevice,
                                ) {
                                    Text(if (savingOnDevice) "Saving…" else "Save on device")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}