package com.mrredhood.devforge.core.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyCenterScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
) {
    var manifests by remember(workspace.rootUri) { mutableStateOf<List<WorkspaceEntry>>(emptyList()) }

    LaunchedEffect(workspace.rootUri) {
        manifests = workspace.entries.filter { entry ->
            entry.name in setOf(
                "libs.versions.toml",
                "build.gradle",
                "build.gradle.kts",
                "settings.gradle",
                "settings.gradle.kts",
                "gradle.properties",
                "gradle-wrapper.properties",
            )
        }
    }

    val grouped = manifests.groupBy { entry ->
        when {
            entry.name == "libs.versions.toml" -> "Version catalog"
            entry.name.contains("wrapper") -> "Gradle wrapper"
            entry.name.contains("settings") -> "Project settings"
            entry.name == "gradle.properties" -> "Gradle properties"
            else -> "Build scripts"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dependencies", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { manifests = workspace.entries.filter { it.name in setOf("libs.versions.toml","build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts","gradle.properties","gradle-wrapper.properties") } }) {
                        Text("Refresh")
                    }
                },
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
                        Text("Dependency and build inputs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "These are the workspace files that define or influence Gradle dependency resolution and build configuration.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (manifests.isEmpty()) {
                item {
                    Text("No common Gradle dependency/configuration files were found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                grouped.forEach { (group, entries) ->
                    item {
                        Text(group, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(entries, key = { it.uri.toString() }) { entry ->
                        DependencyFileRow(entry)
                    }
                }
            }
            item {
                Text(
                    "Use Build Center → Dependency report for the resolved dependency graph. This screen stays lightweight and source-oriented so it remains responsive on Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DependencyFileRow(entry: WorkspaceEntry) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold)
                Text(entry.uri.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text("Source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
