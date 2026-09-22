package com.mrredhood.devforge.core.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.ai.AIChatViewModel
import com.mrredhood.devforge.core.terminal.TerminalViewModel

@Composable
fun EngineeringPowerScreen(
    terminal: TerminalViewModel = viewModel(),
    aiViewModel: AIChatViewModel = viewModel(),
) {
    val workflow = aiViewModel.aiWorkflow
    val context = LocalContext.current
    val taskStore = remember { EngineeringTaskStore(context) }
    val profileStore = remember { DevelopmentProfileStore(context) }
    val mcpStore = remember { McpServerStore(context) }
    val ruleStore = remember { LearnedRuleStore(context) }
    var tasks by remember { mutableStateOf(taskStore.list()) }
    var profile by remember { mutableStateOf(profileStore.current()) }
    var mcpServers by remember { mutableStateOf(mcpStore.list()) }
    var learnedRules by remember { mutableStateOf(ruleStore.list()) }
    var selectedSection by remember { mutableStateOf("mission") }
    val graph = remember(workflow) { EngineeringMissionGraph.from(workflow) }
    val proof = remember(workflow) { AiProofPackage.from(workflow) }

    LaunchedEffect(profile.id) {
        profileStore.select(profile.id)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Engineering Power", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(
                "VS Code, Vim, Android Studio, Cursor, Codex and Claude-style workflows unified around DevForge Mission and approvals.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "mission" to "Mission",
                    "tasks" to "Tasks",
                    "profiles" to "Profiles",
                    "integrations" to "Integrations",
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedSection == id,
                        onClick = { selectedSection = id },
                        label = { Text(label) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (selectedSection) {
                    "mission" -> {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        workflow?.summary ?: "No active AI Mission",
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        workflow?.changeSummary ?: "Start an AI mission from Chat to populate evidence.",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    )
                                    HorizontalDivider()
                                    graph.forEach { node ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(node.label)
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(node.state.name.lowercase()) },
                                            )
                                        }
                                    }
                                    proof?.let {
                                        HorizontalDivider()
                                        Text("Proof package", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                        Text("Changed paths: " + it.changedPaths.size)
                                        Text("Passed checks: " + it.passedChecks.size)
                                        Text("Failed checks: " + it.failedChecks.size)
                                    }
                                }
                            }
                        }
                    }

                    "tasks" -> {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Repeatable Tasks", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                    Text(
                                        "These tasks run inside the active workspace through the same sandboxed terminal used by agents.",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        items(tasks.filter { it.enabled }) { task ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(task.name, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                            Text(task.description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                            Text(task.command, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Switch(
                                                checked = task.enabled,
                                                onCheckedChange = {
                                                    taskStore.setEnabled(task.id, it)
                                                    tasks = taskStore.list()
                                                },
                                            )
                                            Button(
                                            onClick = {
                                                terminal.updateCommandLine(task.command)
                                                terminal.run()
                                            },
                                            enabled = !terminal.isRunning && terminal.workspaceId != null,
                                            ) {
                                                Text("Run")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "profiles" -> {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Development Profiles", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                    profileStore.list().forEach { candidate ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(candidate.name)
                                                Text(candidate.description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                            }
                                            Switch(
                                                checked = profile.id == candidate.id,
                                                onCheckedChange = {
                                                    if (it) {
                                                        profile = candidate
                                                        profileStore.select(candidate.id)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Integration & future-proofing", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                    Text("MCP servers", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                    mcpServers.forEach { server ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f)) {
                                                Text(server.name)
                                                Text(server.endpoint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                            }
                                            Switch(
                                                checked = server.enabled,
                                                onCheckedChange = {
                                                    mcpStore.setEnabled(server.id, it)
                                                    mcpServers = mcpStore.list()
                                                },
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                    Text("UI Journeys / Compose Preview / Device Matrix", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                    DeviceMatrixCatalog.defaults.forEach { target ->
                                        Text(target.label + " — " + target.notes, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    }
                                    HorizontalDivider()
                                    Text("Remote Development / Extensions", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                    Text("Controller/worker endpoint and native extension contracts are approval-scoped definitions; execution is never implicit.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    HorizontalDivider()
                                    Text("Learned Rules", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                    if (learnedRules.isEmpty()) {
                                        Text("No rule proposals yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    } else {
                                        learnedRules.forEachIndexed { index, rule ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(rule.rule)
                                                    Text(rule.evidence, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                                }
                                                if (!rule.accepted) {
                                                    TextButton(onClick = {
                                                        ruleStore.accept(index)
                                                        learnedRules = ruleStore.list()
                                                    }) { Text("Accept") }
                                                } else {
                                                    AssistChip(onClick = {}, label = { Text("Accepted") })
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                    Text("AI Memory / Routing / Automations", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                    Text("Memory keeps evidence with scope; routing selects fast/standard/deep cloud work; automation blueprints cover CI watch, issue triage, dependency review, nightly verification and release evidence.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        item {
                            Text(
                                "All mutations remain subject to the existing approval and security policy. External integrations are registry-first and do not silently execute.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                item {
                    if (terminal.statusMessage != null) {
                        Text(
                            terminal.statusMessage ?: "",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrationRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            TextButton(onClick = {}) { Text("Available") }
        }
        Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        HorizontalDivider()
    }
}
