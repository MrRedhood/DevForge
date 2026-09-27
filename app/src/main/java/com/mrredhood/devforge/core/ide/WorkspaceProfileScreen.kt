package com.mrredhood.devforge.core.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildTarget
import com.mrredhood.devforge.core.workspace.WorkspaceProfile
import com.mrredhood.devforge.core.workspace.WorkspaceProfileRepository
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceProfileScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
) {
    val active = workspace.workspace
    val repository = remember { WorkspaceProfileRepository(workspace.getApplication()) }
    val profile = remember(active?.id) { active?.id?.let(repository::load) ?: defaultProfile() }
    var displayName by remember(active?.id) { mutableStateOf(profile.displayName) }
    var target by remember(active?.id) { mutableStateOf(profile.preferredBuildTarget) }
    var workflow by remember(active?.id) { mutableStateOf(profile.validationWorkflow) }
    var previewPath by remember(active?.id) { mutableStateOf(profile.defaultPreviewPath) }
    var notes by remember(active?.id) { mutableStateOf(profile.notes) }
    var message by remember(active?.id) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspace profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(active?.name ?: "No workspace selected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "These preferences are stored by workspace ID and contain no credentials.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Workspace label") },
                    placeholder = { Text(active?.name ?: "My project") },
                    singleLine = true,
                    enabled = active != null,
                )
            }
            item {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Preferred build target") },
                    supportingText = { Text(BuildTarget.entries.joinToString(" · ") { it.name }) },
                    singleLine = true,
                    enabled = active != null,
                )
            }
            item {
                OutlinedTextField(
                    value = workflow,
                    onValueChange = { workflow = it.take(180) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Validation workflow") },
                    singleLine = true,
                    enabled = active != null,
                )
            }
            item {
                OutlinedTextField(
                    value = previewPath,
                    onValueChange = { previewPath = it.take(180) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default web preview file") },
                    singleLine = true,
                    enabled = active != null,
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(2000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Workspace notes") },
                    minLines = 4,
                    enabled = active != null,
                )
            }
            item {
                Button(
                    onClick = {
                        active?.let {
                            repository.save(
                                it.id,
                                WorkspaceProfile(
                                    displayName = displayName.trim(),
                                    preferredBuildTarget = target.trim().ifBlank { "DebugApk" },
                                    validationWorkflow = workflow.trim().ifBlank { ".github/workflows/android.yml" },
                                    defaultPreviewPath = previewPath.trim().ifBlank { "index.html" },
                                    notes = notes.trim(),
                                ),
                            )
                            message = "Workspace profile saved."
                        }
                    },
                    enabled = active != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save profile")
                }
            }
            item {
                TextButton(
                    onClick = {
                        active?.let {
                            repository.clear(it.id)
                            displayName = ""
                            target = "DebugApk"
                            workflow = ".github/workflows/android.yml"
                            previewPath = "index.html"
                            notes = ""
                            message = "Workspace profile reset."
                        }
                    },
                    enabled = active != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset profile")
                }
            }
            message?.let {
                item {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun defaultProfile() = WorkspaceProfile(
    displayName = "",
    preferredBuildTarget = "DebugApk",
    validationWorkflow = ".github/workflows/android.yml",
    defaultPreviewPath = "index.html",
    notes = "",
)
