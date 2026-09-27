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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class IdeToolCard(
    val tool: IdeTool,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

private val ideToolCards = listOf(
    IdeToolCard(IdeTool.OVERVIEW, Icons.Default.Info, "Workspace overview", "See workspace, tabs, problems, approvals, files and recent build signals in one place."),
    IdeToolCard(IdeTool.PROBLEMS, Icons.Default.Error, "Problems", "Review diagnostics for the active file and quickly spot errors and warnings."),
    IdeToolCard(IdeTool.PROJECT_MAP, Icons.Default.Code, "Project map", "Browse indexed classes, functions, interfaces and other symbols by file."),
    IdeToolCard(IdeTool.DEPENDENCIES, Icons.Default.Build, "Dependencies", "Find the common Gradle dependency manifests in the current workspace."),
    IdeToolCard(IdeTool.ACTIVITY, Icons.Default.List, "Activity", "Review recent audit events and build receipts without leaving the project."),
    IdeToolCard(IdeTool.LOCAL_HISTORY, Icons.Default.History, "Local history", "Restore bounded local snapshots for the currently open file."),
    IdeToolCard(IdeTool.LOGS, Icons.Default.Terminal, "Logs", "Inspect a bounded device logcat snapshot for troubleshooting."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeToolsHubScreen(
    onBack: () -> Unit,
    onOpenTool: (IdeTool) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IDE tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.List, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("ide-tools-hub"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Engineering toolbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Focused inspection and recovery tools stay grouped here so the main navigation stays simple.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ideToolCards, key = { it.tool.name }) { item ->
                Card(
                    onClick = { onOpenTool(item.tool) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(item.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(14.dp))
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
