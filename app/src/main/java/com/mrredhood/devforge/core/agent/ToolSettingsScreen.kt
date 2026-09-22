package com.mrredhood.devforge.core.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ToolSettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ToolSettingsStore(context) }
    var revision by remember { mutableStateOf(0) }
    val groups = remember { DevForgeToolCatalog.entries.groupBy { it.group } }

    LazyColumn(
        Modifier.fillMaxSize().testTag("tool-settings-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "AI Tools",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Enabled tools are available to every AI model used in DevForge, including models without native function-calling support. Mutating tools still pass through DevForge's workspace, policy, lease, precondition, and approval boundaries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        groups.forEach { (group, entries) ->
            item {
                Text(
                    group,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(entries, key = { it.id.wireName }) { entry ->
                val enabled = remember(revision, entry.id) { store.isEnabled(entry.id) }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                entry.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (entry.id == AgentToolId.DELETE_PATH) {
                                Text(
                                    "Disabled by default because it is destructive.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                store.setEnabled(entry.id, it)
                                revision++
                            },
                        )
                    }
                }
            }
        }

        item {
            TextButton(
                onClick = {
                    store.resetToDefaults()
                    revision++
                },
            ) {
                Text("Reset tool settings")
            }
        }
    }
}
