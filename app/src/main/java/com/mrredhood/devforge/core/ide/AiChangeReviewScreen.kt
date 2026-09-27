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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reviews
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.agent.AgentActivityViewModel
import com.mrredhood.devforge.core.editor.DiffKind
import com.mrredhood.devforge.core.git.GitDiffDocument
import com.mrredhood.devforge.core.git.GitDiffViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChangeReviewScreen(
    diffViewModel: GitDiffViewModel = viewModel(),
    agentViewModel: AgentActivityViewModel = viewModel(),
) {
    val reviewed = remember { mutableStateMapOf<String, Boolean>() }
    val documents = diffViewModel.documents
    val additions = documents.sumOf { doc -> doc.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.ADDED } } }
    val deletions = documents.sumOf { doc -> doc.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.REMOVED } } }
    val reviewedCount = documents.count { reviewed[it.path] == true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI change review", maxLines = 1) },
                actions = {
                    IconButton(onClick = diffViewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh changes")
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
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("AI-created workspace changes", fontWeight = FontWeight.Bold)
                                Text(
                                    "Review the current working tree before treating an AI execution as complete.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(documents.size.toString() + " files") })
                            FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("+" + additions) })
                            FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("-" + deletions) })
                            FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(reviewedCount.toString() + " reviewed") })
                        }
                    }
                }
            }

            item {
                val latest = agentViewModel.tasks.firstOrNull()
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Reviews, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Latest AI execution", fontWeight = FontWeight.SemiBold)
                            Text(
                                latest?.let { it.title + " · " + it.status.replace('_', ' ').lowercase(Locale.US) }
                                    ?: "No recent AI execution for this workspace.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            diffViewModel.message?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(message, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (documents.isEmpty() && diffViewModel.message == null) {
                item {
                    Text("Computing workspace changes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(documents, key = { it.path }) { document ->
                AiReviewFileCard(
                    document = document,
                    reviewed = reviewed[document.path] == true,
                    onToggleReviewed = {
                        reviewed[document.path] = reviewed[document.path] != true
                    },
                )
            }

            if (documents.isNotEmpty()) {
                item {
                    val allReviewed = documents.all { reviewed[it.path] == true }
                    if (allReviewed) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("All current changed files are marked reviewed.", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { documents.forEach { reviewed[it.path] = true } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Mark all files reviewed")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiReviewFileCard(
    document: GitDiffDocument,
    reviewed: Boolean,
    onToggleReviewed: () -> Unit,
) {
    val added = document.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.ADDED } }
    val removed = document.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.REMOVED } }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reviewed) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (reviewed) Icons.Default.CheckCircle else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (reviewed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(document.path, fontWeight = FontWeight.Bold)
                    Text(
                        document.status.name.lowercase(Locale.US) + " · +" + added + " / -" + removed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggleReviewed) {
                    Text(if (reviewed) "Unreview" else "Review")
                }
            }

            document.unavailableReason?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.size(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            document.sections.take(2).forEach { section ->
                Text(
                    section.lines.take(20).joinToString("\\n") { line ->
                        val prefix = when (line.kind) {
                            DiffKind.ADDED -> "+"
                            DiffKind.REMOVED -> "-"
                            DiffKind.CONTEXT -> " "
                        }
                        prefix + line.text
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
