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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Reviews
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    IdeToolCard(IdeTool.ENVIRONMENT, Icons.Default.Info, "Environment", "Inspect device, Android, app and workspace details when troubleshooting."),
    IdeToolCard(IdeTool.LOGS, Icons.Default.Terminal, "Logs", "Inspect a bounded device logcat snapshot for troubleshooting."),
    IdeToolCard(IdeTool.AI_EXECUTION, Icons.Default.AutoAwesome, "AI execution", "Observe plans, step progress, tools, approvals, model details and verification evidence from AI-managed work."),
    IdeToolCard(IdeTool.AI_CHANGE_REVIEW, Icons.Default.Reviews, "AI change review", "Review current workspace changes with additions, removals and per-file review state before considering AI work complete."),
    IdeToolCard(IdeTool.TEST_CENTER, Icons.Default.Science, "Test center", "Discover test sources and review authoritative DevForge build/test receipts from the current project."),
    IdeToolCard(IdeTool.ENVIRONMENT_DOCTOR, Icons.Default.HealthAndSafety, "Environment doctor", "Check workspace, Gradle, Git, Android, AI and preview readiness before troubleshooting deeper issues."),
    IdeToolCard(IdeTool.RUN_DEBUG, Icons.Default.PlayArrow, "Run & Debug", "Start the configured debug APK workflow and keep build state, target and device readiness together."),
    IdeToolCard(IdeTool.CI_CD, Icons.Default.Cloud, "CI/CD", "Review recent pipeline health, live workflows and the repository build contract."),
    IdeToolCard(IdeTool.SECURITY_CENTER, Icons.Default.Security, "Security Center", "Review credential protection, workspace boundaries and sensitive-action safeguards."),
    IdeToolCard(IdeTool.DIAGNOSTICS, Icons.Default.Error, "Diagnostics", "Combine editor problems with structured build-failure evidence in one troubleshooting surface."),
    IdeToolCard(IdeTool.CODE_INTELLIGENCE, Icons.Default.Code, "Code Intelligence", "Search indexed symbols and inspect definitions and references through the built-in LSP-compatible facade."),
    IdeToolCard(IdeTool.WORKSPACE_PROFILE, Icons.Default.Info, "Workspace profile", "Store project-specific build, validation, preview and notes preferences without mixing them into global settings."),
    IdeToolCard(IdeTool.RELEASE_READINESS, Icons.Default.Info, "Release readiness", "Evaluate the existing release-quality gate against real build, artifact, log, editor and configuration evidence."),
    IdeToolCard(IdeTool.PROJECT_MEMORY, Icons.Default.Info, "Project memory", "Inspect durable AI memory and agent handoffs for the current workspace; remove stale memory without changing agent execution controls."),
    IdeToolCard(IdeTool.PROJECT_HEALTH, Icons.Default.Info, "Project health", "Run a bounded health pass across workspace integrity, build evidence, diagnostics, unsaved changes and approvals."),
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
        var query by remember { mutableStateOf("") }
        val normalizedQuery = query.trim()
        val visibleTools = ideToolCards.filter { item ->
            normalizedQuery.isBlank() ||
                item.title.contains(normalizedQuery, ignoreCase = true) ||
                item.subtitle.contains(normalizedQuery, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("ide-tools-hub"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    modifier = Modifier.fillMaxWidth().testTag("ide-tools-search"),
                    label = { Text("Search IDE tools") },
                    placeholder = { Text("Try logs, environment, problems…") },
                    singleLine = true,
                )
            }
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
            if (visibleTools.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("No IDE tools found", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Try a keyword such as logs, environment, problems, or project.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(visibleTools, key = { it.tool.name }) { item ->
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
}
