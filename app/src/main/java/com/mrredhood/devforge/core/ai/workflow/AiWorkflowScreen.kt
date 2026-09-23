package com.mrredhood.devforge.core.ai.workflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun AiPlanCard(workflow: AiWorkflowSnapshot?) {
    if (
        workflow == null ||
        workflow.generatedPlan.isEmpty() ||
        workflow.status !in setOf(AiWorkflowSnapshot.Status.RUNNING, AiWorkflowSnapshot.Status.WAITING)
    ) return

    var expanded by remember(workflow.workflowId) { mutableStateOf(true) }
    val steps = workflow.generatedPlan
    val completed = workflow.completedPlanSteps
    val current = steps.firstOrNull {
        it.status == AiPlanStepStatus.RUNNING || it.status == AiPlanStepStatus.FAILED
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    Modifier.weight(1f).padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("AI's plan", style = MaterialTheme.typography.titleSmall)
                    Text(
                        current?.title ?: workflow.currentStep ?: "Working",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    completed.toString() + "/" + steps.size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse AI plan" else "Expand AI plan",
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (expanded) 180f else 0f
                        },
                    )
                }
            }

            if (expanded) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    steps.forEach { step ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            when (step.status) {
                                AiPlanStepStatus.COMPLETED -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Completed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                AiPlanStepStatus.RUNNING -> Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = "In progress",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                AiPlanStepStatus.FAILED -> Icon(
                                    Icons.Default.Error,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                AiPlanStepStatus.PENDING -> Icon(
                                    Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Pending",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Column(
                                Modifier.weight(1f).padding(start = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(step.title, style = MaterialTheme.typography.bodyMedium)
                                if (step.detail.isNotBlank()) {
                                    Text(
                                        step.detail,
                                        style = MaterialTheme.typography.bodySmall,
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
}
