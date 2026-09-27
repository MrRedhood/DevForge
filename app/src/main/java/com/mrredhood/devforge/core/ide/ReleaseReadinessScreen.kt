package com.mrredhood.devforge.core.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildState
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.quality.DevForgeReleaseQualityGate
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel

@Composable
fun ReleaseReadinessScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
    build: BuildViewModel = viewModel(),
    editor: EditorViewModel = viewModel(),
) {
    val latest = build.runSnapshot
    val configuration = build.configuration
    val latestSuccessful = build.history.firstOrNull { it.conclusion.equals("success", true) }
    val artifactsPresent = build.artifacts.any { !it.expired && (
        it.name.contains("apk", true) || it.name.contains("aab", true)
    ) }
    val logsAvailable = build.logs.isNotEmpty() || latest != null
    val noUnsaved = editor.tabs.none { it.isDirty }
    val diagnosticsClear = editor.diagnostics.none { it.severity.name.equals("ERROR", true) }
    val configurationComplete = configuration.githubOwner.isNotBlank() &&
        configuration.githubRepository.isNotBlank() &&
        configuration.workflowFile.isNotBlank()
    val gate = DevForgeReleaseQualityGate.evaluate(
        hasSuccessfulBuild = latestSuccessful != null,
        artifactsPresent = artifactsPresent,
        logsAvailable = logsAvailable,
        noUnsavedEditorChanges = noUnsaved,
        diagnosticsClear = diagnosticsClear,
        configurationComplete = configurationComplete,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Release readiness", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = build::refreshRun) { Text("Refresh") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (gate.ready) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                )) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            if (gate.ready) "Release gate passed" else "Release gate needs attention",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            gate.passed.toString() + " passed · " + gate.failed + " need attention",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            workspace.workspace?.name ?: "No workspace selected",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(gate.checks.size) { index ->
                val (label, passed) = gate.checks[index]
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(
                            if (passed) "PASS" else "CHECK",
                            color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(label, Modifier.weight(1f))
                    }
                }
            }
            item {
                Text(
                    "Release target: " + configuration.target.label +
                        " · " + configuration.githubOwner + "/" + configuration.githubRepository,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (build.state is BuildState.Failed) {
                item {
                    Text(
                        (build.state as BuildState.Failed).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
