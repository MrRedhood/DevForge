package com.mrredhood.devforge.core.git

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.editor.DiffKind

@Composable
fun GitDiffScreen(viewModel: GitDiffViewModel = viewModel()) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Diffs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("Read-only HEAD/index/worktree review", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Refresh diffs") }
                }
            }
            if (viewModel.isLoading) {
                item { Text("Computing bounded diffs…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            viewModel.message?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(message, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(viewModel.documents, key = { it.path }) { document ->
                DiffDocumentCard(document)
            }
        }
    }
}

@Composable
private fun DiffDocumentCard(document: GitDiffDocument) {
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(document.path, fontWeight = FontWeight.Bold)
                    Text(document.status.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            document.sections.forEach { section ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(12.dp)) {
                        Text(section.title, fontWeight = FontWeight.SemiBold)
                        Text("${section.beforeLabel} → ${section.afterLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.horizontalScroll(rememberScrollState())) {
                            section.lines.forEach { line ->
                                val prefix = when (line.kind) {
                                    DiffKind.ADDED -> "+"
                                    DiffKind.REMOVED -> "-"
                                    DiffKind.CONTEXT -> " "
                                }
                                Text(
                                    "$prefix${line.text}",
                                    modifier = Modifier.padding(vertical = 1.dp),
                                    fontFamily = FontFamily.Monospace,
                                    color = when (line.kind) {
                                        DiffKind.ADDED -> MaterialTheme.colorScheme.primary
                                        DiffKind.REMOVED -> MaterialTheme.colorScheme.error
                                        DiffKind.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            document.unavailableReason?.let { reason ->
                Text(reason, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
