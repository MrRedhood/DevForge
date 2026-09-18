package com.mrredhood.devforge.core.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.CapabilityGrantEntity
import com.mrredhood.devforge.core.storage.capabilityOrNull
import com.mrredhood.devforge.core.storage.riskOrNull
import java.text.DateFormat
import java.util.Date

@Composable
fun ApprovalCenterScreen(viewModel: ApprovalCenterViewModel = viewModel()) {
    val pending = viewModel.pending
    val message = viewModel.actionMessage
    val activeGrantCapabilities = viewModel.grants.mapNotNull { it.capabilityOrNull() }.toSet()
    var grantScopeText by remember { mutableStateOf("") }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text("Approval Center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(
                        "Review side-effecting actions, persistent grants, and recent policy activity.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pending actions", fontWeight = FontWeight.Bold)
                            Text("${pending.size} waiting for your decision", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilterChip(selected = pending.isNotEmpty(), onClick = {}, enabled = false, label = { Text(pending.size.toString()) })
                    }
                }
            }

            if (pending.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp)) {
                            Text("Nothing needs approval", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "High-risk Git and build actions will appear here before execution.",
                                Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(pending, key = { it.approvalId }) { action ->
                    ApprovalActionCard(action, viewModel::approve, viewModel::reject)
                }
            }

            item { SectionHeader(Icons.Default.Settings, "Persistent capability grants", "Grants can cover the entire workspace or selected workspace-relative paths. Approval bypass never exceeds R2 and never covers protected capabilities.") }

            item {
                OutlinedTextField(
                    value = grantScopeText,
                    onValueChange = { grantScopeText = it.take(4_096) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Grant path scope") },
                    placeholder = { Text("Blank = entire workspace; e.g. src/main, app/src") },
                    supportingText = { Text("Use workspace-relative paths separated by commas or new lines.") },
                    singleLine = false,
                    minLines = 2,
                )
            }

            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Active workspace", fontWeight = FontWeight.Bold)
                        Text(viewModel.activeWorkspaceId ?: "No workspace is currently active", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (viewModel.grants.isEmpty()) {
                            Text("No persistent grants are active for this workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            viewModel.grants.forEach { grant -> GrantRow(grant, viewModel::revoke) }
                        }
                    }
                }
            }

            items(viewModel.grantableCapabilities, key = { it.name }) { capability ->
                val active = capability in activeGrantCapabilities
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(capability.name.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                            Text("Approval bypass ceiling: R2", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { viewModel.grant(capability, grantScopeText) }, enabled = !active && viewModel.activeWorkspaceId != null) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (active) "Enabled" else "Grant")
                        }
                    }
                }
            }

            item { SectionHeader(Icons.Default.History, "Audit history", "Recent approval, execution, expiry, and grant events are retained locally with bounded history.") }

            if (viewModel.auditHistory.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text("No audit events yet.", Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(viewModel.auditHistory, key = { it.eventId }) { event -> AuditEventCard(event) }
            }

            message?.let { text ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalActionCard(action: ApprovalEntity, onApprove: (ApprovalEntity) -> Unit, onReject: (ApprovalEntity) -> Unit) {
    val risk = action.riskOrNull()?.name ?: action.risk
    val capability = action.capabilityOrNull()?.name ?: action.capability
    var patchReady by remember(action.approvalId) { mutableStateOf(capability != Capability.PATCH_FILE.name) }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(action.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(risk, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(capability, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("Workspace: ${action.workspaceId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Created: ${formatTime(action.createdAtEpochMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Expires: ${formatTime(action.expiresAtEpochMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (capability == Capability.PATCH_FILE.name) {
                PatchApprovalPreview(action) { patchReady = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onApprove(action) }, enabled = patchReady, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Approve")
                }
                TextButton(onClick = { onReject(action) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun PatchApprovalPreview(action: ApprovalEntity, onReadyChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val state by produceState<AgentPatchPreviewState>(AgentPatchPreviewState.Loading, action.approvalId) {
        value = runCatching {
            com.mrredhood.devforge.core.agent.AgentPatchPreviewService(
                context.contentResolver,
                com.mrredhood.devforge.core.storage.DevForgeDatabase.get(context).workspaceDao(),
            ).preview(action)
        }.fold(
            onSuccess = { AgentPatchPreviewState.Ready(it) },
            onFailure = { AgentPatchPreviewState.Error(it.message ?: "Unable to render patch preview.") },
        )
    }
    LaunchedEffect(state) {
        onReadyChanged(state is AgentPatchPreviewState.Ready && (state as AgentPatchPreviewState.Ready).preview.preconditionMatches)
    }

    when (state) {
        AgentPatchPreviewState.Loading -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                Text("Preparing diff preview…", style = MaterialTheme.typography.bodySmall)
            }
        }
        is AgentPatchPreviewState.Error -> {
            val error = (state as AgentPatchPreviewState.Error).message
            Text("Patch preview unavailable: " + error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        is AgentPatchPreviewState.Ready -> {
            val preview = (state as AgentPatchPreviewState.Ready).preview
            val additions = preview.diff.count { it.kind == com.mrredhood.devforge.core.editor.DiffKind.ADDED }
            val removals = preview.diff.count { it.kind == com.mrredhood.devforge.core.editor.DiffKind.REMOVED }
            Card(colors = CardDefaults.cardColors(
                containerColor = if (preview.preconditionMatches) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
            )) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (preview.isNewFile) "New file" else "Patch preview", fontWeight = FontWeight.Bold)
                    Text(preview.path, style = MaterialTheme.typography.bodySmall)
                    if (!preview.preconditionMatches) {
                        Text(
                            "The file changed after this patch was prepared. Reject it and regenerate the patch.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(additions.toString() + " additions · " + removals.toString() + " removals", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.diff.isNotEmpty()) {
                        Column {
                            preview.diff.forEach { line ->
                                val prefix = when (line.kind) {
                                    com.mrredhood.devforge.core.editor.DiffKind.ADDED -> "+"
                                    com.mrredhood.devforge.core.editor.DiffKind.REMOVED -> "-"
                                    com.mrredhood.devforge.core.editor.DiffKind.CONTEXT -> " "
                                }
                                Text(prefix + line.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface AgentPatchPreviewState {
    data object Loading : AgentPatchPreviewState
    data class Ready(val preview: com.mrredhood.devforge.core.agent.AgentPatchPreview) : AgentPatchPreviewState
    data class Error(val message: String) : AgentPatchPreviewState
}
@Composable
private fun GrantRow(grant: CapabilityGrantEntity, onRevoke: (CapabilityGrantEntity) -> Unit) {
    val scope = grant.pathScopeOrNull()?.canonicalPrefixes()
    val scopeLabel = when {
        scope == null -> "Invalid scope"
        scope.any { it.isEmpty() } -> "Entire workspace"
        else -> scope.joinToString(", ").take(160)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(grant.capability.replace('_', ' '), fontWeight = FontWeight.SemiBold)
            Text("Up to ${grant.maxRisk} · $scopeLabel · ${grant.expiresAtEpochMs?.let(::formatTime) ?: "No expiry"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { onRevoke(grant) }) { Text("Revoke") }
    }
}

@Composable
private fun AuditEventCard(event: AuditEventEntity) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(event.eventType.replace('_', ' '), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(formatTime(event.createdAtEpochMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(event.summary)
            val detail = listOfNotNull(event.capability?.replace('_', ' '), event.risk, event.metadataJson).joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
