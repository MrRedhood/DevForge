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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildFailureDiagnosis
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.diagnostics.DiagnosticSeverity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsCenterScreen(
    onBack: () -> Unit,
    editor: EditorViewModel = viewModel(),
    build: BuildViewModel = viewModel(),
) {
    val editorProblems = editor.diagnostics
    val buildFindings = BuildFailureDiagnosis.analyze(build.logs)

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Diagnostics", fontWeight = FontWeight.Bold) },
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
                        Text("Unified problem view", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            editorProblems.size.toString() + " editor diagnostics · " +
                                buildFindings.size.toString() + " structured build findings",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SectionHeader("Editor diagnostics", editorProblems.size)
            }
            if (editorProblems.isEmpty()) {
                item { EmptyDiagnosticsCard("No active editor diagnostics.") }
            } else {
                items(editorProblems.size) { index ->
                    val diagnostic = editorProblems[index]
                    val severity = diagnostic.severity
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    severity.name.lowercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (severity == DiagnosticSeverity.ERROR) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                diagnostic.code?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            }
                            Text(diagnostic.message)
                            diagnostic.location?.let {
                                Text(
                                    listOfNotNull(it.path, it.line?.toString(), it.column?.toString()).joinToString(":"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader("Build failure evidence", buildFindings.size)
            }
            if (buildFindings.isEmpty()) {
                item {
                    EmptyDiagnosticsCard(
                        if (build.logs.isEmpty()) {
                            "No build logs are loaded. Open Build Center and load the latest run logs after a failed workflow."
                        } else {
                            "No structured failure pattern was detected in the bounded build logs."
                        },
                    )
                }
            } else {
                items(buildFindings.size) { index ->
                    val finding = buildFindings[index]
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(finding.category, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(finding.severity, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(finding.summary)
                            Text("Job: " + finding.jobName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(finding.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                TextButton(onClick = build::reloadLogs, enabled = build.runSnapshot != null) {
                    Text("Load latest build logs")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyDiagnosticsCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(text, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
