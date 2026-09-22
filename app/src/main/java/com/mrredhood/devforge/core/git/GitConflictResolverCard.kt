package com.mrredhood.devforge.core.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GitConflictResolverCard(viewModel: GitHistoryViewModel = viewModel()) {
    val session = viewModel.conflictSession ?: return
    var draftPath by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val selectedPath = session.selectedPath ?: session.paths.firstOrNull()
    val file = session.files.firstOrNull { it.path == selectedPath }

    LaunchedEffect(selectedPath, file?.merged) {
        draftPath = selectedPath
        draft = file?.merged.orEmpty()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MergeType, null, Modifier.size(26.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conflict resolver", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(
                        "${operationLabel(session.operation)} · ${session.paths.size} unresolved path(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (viewModel.isResolvingConflict) CircularProgressIndicator(Modifier.size(22.dp))
            }

            Text(
                "The real workspace remains untouched until every conflict is resolved and the final operation passes a fresh clean-state check.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )

            if (session.paths.isNotEmpty()) {
                Text("Conflicted files", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    session.paths.take(MAX_VISIBLE_PATHS).forEach { path ->
                        FilterChip(
                            selected = path == selectedPath,
                            onClick = { viewModel.selectConflictFile(path) },
                            label = { Text(path, maxLines = 1) },
                            enabled = !viewModel.isResolvingConflict,
                        )
                    }
                }
            }

            file?.let { conflictFile ->
                Text(conflictFile.path, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                if (conflictFile.binary || conflictFile.oversized) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(7.dp))
                        Text(
                            if (conflictFile.oversized) "This file exceeds the 256 KiB conflict-editor limit; choose a side or delete it."
                            else "This file is binary; text editing is disabled. Choose a side or delete it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text("Ours / base / theirs", style = MaterialTheme.typography.labelLarge)
                    ConflictPreview("Ours", conflictFile.ours)
                    ConflictPreview("Base", conflictFile.base)
                    ConflictPreview("Theirs", conflictFile.theirs)

                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8,
                        maxLines = 18,
                        label = { Text("Resolved content") },
                        enabled = !viewModel.isResolvingConflict && draftPath == conflictFile.path,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.resolveConflict(conflictFile.path, draft) },
                            enabled = !viewModel.isResolvingConflict && draftPath == conflictFile.path,
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Save resolution")
                        }
                        OutlinedButton(
                            onClick = { viewModel.resolveConflict(conflictFile.path, null) },
                            enabled = !viewModel.isResolvingConflict && draftPath == conflictFile.path,
                        ) {
                            Icon(Icons.Default.DeleteSweep, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Delete")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SideButton("Use ours", conflictFile.ours, conflictFile, viewModel)
                    SideButton("Use base", conflictFile.base, conflictFile, viewModel)
                    SideButton("Use theirs", conflictFile.theirs, conflictFile, viewModel)
                }
            }

            if (session.paths.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(7.dp))
                    Text("All conflicts are staged as resolved. Continue to finish the Git operation.")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.continueConflict() },
                    enabled = session.paths.isEmpty() && !viewModel.isResolvingConflict,
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Continue")
                }
                OutlinedButton(
                    onClick = { viewModel.abortConflict() },
                    enabled = !viewModel.isResolvingConflict,
                ) {
                    Icon(Icons.Default.Block, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Abort")
                }
            }
        }
    }
}

@Composable
private fun SideButton(
    label: String,
    content: String?,
    file: GitConflictFile,
    viewModel: GitHistoryViewModel,
) {
    val supported = !file.binary && !file.oversized
    val displayLabel = if (content == null) "$label · delete" else label
    OutlinedButton(
        onClick = { viewModel.resolveConflict(file.path, content) },
        enabled = supported && !viewModel.isResolvingConflict,
    ) { Text(displayLabel) }
}

@Composable
private fun ConflictPreview(label: String, content: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                preview(content),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun operationLabel(operation: String): String = when (operation) {
    "merge" -> "Merge"
    "rebase" -> "Rebase"
    "cherry-pick" -> "Cherry-pick"
    else -> operation
}

private fun preview(content: String?): String = when {
    content == null -> "<not present on this side>"
    content.length <= MAX_PREVIEW_CHARS -> content
    else -> content.take(MAX_PREVIEW_CHARS) + "\n… preview truncated for mobile performance"
}

private const val MAX_VISIBLE_PATHS = 8
private const val MAX_PREVIEW_CHARS = 18_000
