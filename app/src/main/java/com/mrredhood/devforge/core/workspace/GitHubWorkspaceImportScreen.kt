package com.mrredhood.devforge.core.workspace

import android.content.Intent
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
import androidx.compose.material.icons.filled.CloudDownload
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.github.GitHubConnectionState
import com.mrredhood.devforge.core.github.GitHubConnectionViewModel
import com.mrredhood.devforge.core.github.GitHubRepository
import com.mrredhood.devforge.core.github.GitHubRepositoryViewModel
import com.mrredhood.devforge.core.picker.PickerBridge
import com.mrredhood.devforge.core.picker.SystemPickerActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun GitHubWorkspaceImportScreen(
    workspaceViewModel: WorkspaceViewModel,
    onBack: () -> Unit,
    repositoryViewModel: GitHubRepositoryViewModel = viewModel(),
    connectionViewModel: GitHubConnectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by rememberSaveable { mutableStateOf(false) }
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRepository by remember { mutableStateOf<GitHubRepository?>(null) }
    val connected = connectionViewModel.snapshot.state is GitHubConnectionState.Connected
    val state = repositoryViewModel.state

    LaunchedEffect(connected) {
        if (connected && state.repositories.isEmpty()) repositoryViewModel.refreshRepositories()
    }

    LaunchedEffect(Unit) {
        PickerBridge.results.collectLatest { result ->
            if (result.kind != SystemPickerActivity.KIND_WORKSPACE || result.cancelled || selectedRepository == null) return@collectLatest
            val destination = result.uris.firstOrNull() ?: return@collectLatest
            val repository = selectedRepository ?: return@collectLatest
            importing = true
            importMessage = "Cloning " + repository.fullName + "…"
            scope.launch {
                val imported = workspaceViewModel.importGitHubRepository(
                    parentUri = destination,
                    owner = repository.owner,
                    repositoryName = repository.name,
                    branch = repository.defaultBranch,
                )
                imported.onSuccess {
                    importing = false
                    importMessage = "Imported " + repository.fullName + " as workspace."
                    onBack()
                }.onFailure {
                    importing = false
                    importMessage = it.message ?: "Repository import failed."
                }
            }
        }
    }

    if (!connected) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Import GitHub repository", style = MaterialTheme.typography.titleLarge)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GitHub connection required", style = MaterialTheme.typography.titleMedium)
                    Text("Connect a GitHub credential in Settings before importing a repository.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onBack) { Text("Back to Files") }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (!importing) onBack() }, enabled = !importing) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Import GitHub repository", style = MaterialTheme.typography.titleLarge)
                Text("Clone the repository into a new DevForge workspace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = repositoryViewModel::refreshRepositories, enabled = !state.isLoading && !importing) {
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
                    enabled = !importing,
                )
            }
            importMessage?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = if (importing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (importing) CircularProgressIndicator(Modifier.size(18.dp))
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
                    onClick = { if (!importing) selectedRepository = repository },
                    enabled = !importing,
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
                            Text("Ready to import", style = MaterialTheme.typography.titleMedium)
                            Text(repository.fullName)
                            Text("Branch: " + repository.defaultBranch + ". DevForge will create a new workspace folder and preserve the Git repository metadata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = {
                                    val activity = context as? FragmentActivity
                                    if (activity == null) {
                                        importMessage = "Unable to open the destination folder picker."
                                        return@Button
                                    }
                                    runCatching {
                                        activity.startActivityForResult(
                                            Intent(context, SystemPickerActivity::class.java).putExtra(
                                                SystemPickerActivity.EXTRA_KIND, SystemPickerActivity.KIND_WORKSPACE,
                                            ),
                                            SystemPickerActivity.PICKER_REQUEST_CODE,
                                        )
                                    }.onFailure { importMessage = it.message ?: "Unable to open the destination folder picker." }
                                },
                                enabled = !importing,
                            ) {
                                Icon(Icons.Default.CloudDownload, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Choose destination & import")
                            }
                        }
                    }
                }
            }
        }
    }
}