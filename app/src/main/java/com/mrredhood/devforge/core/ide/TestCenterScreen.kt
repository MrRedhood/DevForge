package com.mrredhood.devforge.core.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.workspace.WorkspaceSearch
import com.mrredhood.devforge.core.workspace.WorkspaceSearchResult
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestCenterScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
) {
    val root = workspace.rootUri
    val builds = remember {
        DevForgeDatabase.get(workspace.getApplication()).buildReceiptDao().observeRecent(12)
    }.collectAsState(initial = emptyList())
    var tests by remember(root) { mutableStateOf<List<WorkspaceSearchResult>>(emptyList()) }
    var scanning by remember(root) { mutableStateOf(false) }

    LaunchedEffect(root, workspace.remoteWorkspace?.repository) {
        if (root == null || workspace.remoteWorkspace != null) {
            tests = emptyList()
            scanning = false
        } else {
            scanning = true
            tests = withContext(Dispatchers.IO) {
                val service = WorkspaceSearch(workspace.getApplication<android.app.Application>().contentResolver)
                listOf("Test.kt", "Test.java", "androidTest", "test_")
                    .flatMap { query -> service.search(root, query, 60) }
                    .distinctBy { it.uri.toString() }
                    .take(120)
            }
            scanning = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test center", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = workspace::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh workspace")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Science, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Test discovery", fontWeight = FontWeight.Bold)
                                Text(
                                    workspace.workspace?.name ?: "No workspace selected",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        when {
                            workspace.workspace == null -> Text("Select a workspace to discover test sources.")
                            workspace.remoteWorkspace != null -> Text(
                                "GitHub-backed workspaces use remote build/test receipts; local SAF scanning is intentionally skipped.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            scanning -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            tests.isEmpty() -> Text(
                                "No conventional test files were discovered in the bounded workspace scan.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> Text(
                                tests.size.toString() + " test-related files/directories discovered.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text("CI evidence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (builds.value.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text(
                            "No DevForge build receipts exist yet. Run a validated CI build from Build Center to establish authoritative test/build evidence.",
                            Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(builds.value, key = { it.runId }) { receipt ->
                    val success = receipt.conclusion.equals("success", true)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("#" + receipt.runNumber + " · " + receipt.target, fontWeight = FontWeight.SemiBold)
                                Text(
                                    receipt.githubOwner + "/" + receipt.githubRepository + " · " + (receipt.conclusion ?: receipt.state),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Discovered tests", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (tests.isEmpty()) {
                item {
                    Text(
                        if (workspace.remoteWorkspace != null) "Remote test discovery is represented by CI receipts."
                        else "No matching test files found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(tests, key = { it.uri.toString() }) { result ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(result.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (result.isDirectory) "Directory" else (result.sizeBytes?.toString()?.plus(" bytes") ?: "Source file"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
