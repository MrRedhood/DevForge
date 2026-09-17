package com.mrredhood.devforge.core.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GitHistoryControlsCard(viewModel: GitHistoryViewModel = viewModel()) {
    var cherryPickRevision by rememberSaveable { mutableStateOf("") }
    val repository = viewModel.repository
    val branches = repository?.branches.orEmpty()
    val selected = viewModel.selectedBranch
    val canRun = selected != null && !viewModel.isExecuting

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Branch & history", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(
                        "Checkout, merge, rebase and cherry-pick run only against a clean, fully inspected workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (viewModel.isExecuting) CircularProgressIndicator(Modifier.size(22.dp))
            }

            if (branches.none { !it.isCurrent }) {
                Text("No alternate local branches are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Target branch", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(branches.filter { !it.isCurrent }.take(MAX_HISTORY_BRANCHES), key = { it.name }) { branch ->
                        FilterChip(
                            selected = branch.name == selected,
                            onClick = { viewModel.selectBranch(branch.name) },
                            label = { Text(branch.name) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.switchToSelectedBranch() }, enabled = canRun) {
                        IconText(Icons.Default.CallSplit, "Switch")
                    }
                    OutlinedButton(onClick = { viewModel.mergeSelectedBranch() }, enabled = canRun) {
                        IconText(Icons.Default.MergeType, "Merge")
                    }
                    OutlinedButton(onClick = { viewModel.rebaseOntoSelectedBranch() }, enabled = canRun) {
                        IconText(Icons.Default.Replay, "Rebase")
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Text("Cherry-pick commit", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = cherryPickRevision,
                onValueChange = { cherryPickRevision = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("40-character commit SHA") },
            )
            Button(
                onClick = {
                    viewModel.cherryPick(cherryPickRevision)
                    cherryPickRevision = ""
                },
                enabled = cherryPickRevision.trim().length == 40 && !viewModel.isExecuting,
            ) {
                IconText(Icons.Default.Check, "Cherry-pick")
            }

            viewModel.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                "Conflicts are detected before anything is copied back to the workspace. DevForge discards conflicted temporary state instead of silently leaving a partial merge or rebase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun IconText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    androidx.compose.material3.Icon(icon, null)
    Spacer(Modifier.size(6.dp))
    Text(text)
}

private const val MAX_HISTORY_BRANCHES = 12
