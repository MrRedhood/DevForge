package com.mrredhood.devforge.core.ai.workflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AiWorkflowCard(workflow: AiWorkflowSnapshot?) {
    if (workflow == null) return
    var expanded by remember(workflow.workflowId) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("AI Mission", style = MaterialTheme.typography.titleSmall)
                    Text(
                        workflow.phase.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } +
                            (workflow.currentStep?.let { " · " + it } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (workflow.status) {
                    AiWorkflowSnapshot.Status.RUNNING -> CircularProgressIndicator()
                    AiWorkflowSnapshot.Status.WAITING -> Icon(Icons.Default.HourglassTop, null)
                    AiWorkflowSnapshot.Status.COMPLETED -> Icon(Icons.Default.CheckCircle, null)
                    AiWorkflowSnapshot.Status.FAILED,
                    AiWorkflowSnapshot.Status.CANCELLED -> Icon(Icons.Default.Error, null)
                }
            }

            if (expanded) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(workflow.activities.takeLast(16), key = { it.id }) { activity ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "• " + activity.title,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                activity.status.name.lowercase().replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            workflow.verification?.let { verification ->
                Text(
                    "Verification · " +
                        verification.passedCount + " passed · " +
                        verification.failedCount + " failed · " +
                        verification.checks.count { it.status == AiVerificationCheck.Status.SKIPPED } + " skipped",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide activity" else "Show activity")
            }
        }
    }
}
