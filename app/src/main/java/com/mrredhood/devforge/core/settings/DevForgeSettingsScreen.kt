package com.mrredhood.devforge.core.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DevForgeSettingsScreen(viewModel: DevForgeSettingsViewModel = viewModel()) {
    val settings = viewModel.settings
    var owner by remember(settings.githubOwner) { mutableStateOf(settings.githubOwner) }
    var repository by remember(settings.githubRepository) { mutableStateOf(settings.githubRepository) }
    var branch by remember(settings.githubBranch) { mutableStateOf(settings.githubBranch) }
    var workflow by remember(settings.githubWorkflow) { mutableStateOf(settings.githubWorkflow) }
    var buildPoll by remember(settings.buildPollSeconds) { mutableStateOf(settings.buildPollSeconds.toString()) }
    var terminalTimeout by remember(settings.terminalTimeoutMs) { mutableStateOf(settings.terminalTimeoutMs.toString()) }
    var automationInterval by remember(settings.automationEventIntervalMinutes) { mutableStateOf(settings.automationEventIntervalMinutes.toString()) }
    var auditRetention by remember(settings.auditRetentionDays) { mutableStateOf(settings.auditRetentionDays.toString()) }
    var chatRetention by remember(settings.chatRetentionDays) { mutableStateOf(settings.chatRetentionDays.toString()) }

    LaunchedEffect(settings) {
        buildPoll = settings.buildPollSeconds.toString()
        terminalTimeout = settings.terminalTimeoutMs.toString()
        automationInterval = settings.automationEventIntervalMinutes.toString()
        auditRetention = settings.auditRetentionDays.toString()
        chatRetention = settings.chatRetentionDays.toString()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard("AI defaults & routing", "Choose how DevForge selects models when a specific model is not forced.") {
            SettingLabel("Routing mode")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AiRoutingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.aiRoutingMode == mode,
                        onClick = { viewModel.setAiRouting(mode) },
                        label = { Text(mode.name.replace('_', ' ')) },
                    )
                }
            }
        }

        SettingsCard("GitHub repository", "Persist the repository/workflow used by Build Center. Credentials remain in the secure credential store.") {
            OutlinedTextField(owner, { owner = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Owner") }, singleLine = true)
            OutlinedTextField(repository, { repository = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Repository") }, singleLine = true)
            OutlinedTextField(branch, { branch = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Default branch") }, singleLine = true)
            OutlinedTextField(workflow, { workflow = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Workflow file") }, singleLine = true)
            TextButton(onClick = { viewModel.saveGithub(owner, repository, branch, workflow) }) { Text("Save repository defaults") }
        }

        SettingsCard("Build & automation", "Bounded polling and event-monitor intervals. Remote work remains subject to existing policy and approval.") {
            NumberField("Build polling seconds", buildPoll) { buildPoll = it.take(3) }
            NumberField("Automation event interval (minutes)", automationInterval) { automationInterval = it.take(4) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.setBuildPoll(buildPoll) }) { Text("Apply build") }
                TextButton(onClick = { viewModel.setAutomationInterval(automationInterval) }) { Text("Apply automation") }
            }
        }

        SettingsCard("Terminal", "Controls the default interactive timeout; the hard 15-second execution ceiling is enforced by the terminal capability.") {
            NumberField("Default timeout (ms)", terminalTimeout) { terminalTimeout = it.take(5) }
            TextButton(onClick = { viewModel.setTerminalTimeout(terminalTimeout) }) { Text("Apply terminal") }
        }

        SettingsCard("Privacy & retention", "Local durable data is bounded and can be retained for a shorter window. Secret redaction remains enforced independently.") {
            NumberField("Audit retention (days)", auditRetention) { auditRetention = it.take(3) }
            NumberField("Chat retention (days)", chatRetention) { chatRetention = it.take(4) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.setAuditRetention(auditRetention) }) { Text("Apply audit") }
                TextButton(onClick = { viewModel.setChatRetention(chatRetention) }) { Text("Apply chat") }
            }
        }

        SettingsCard("Appearance", "Original DevForge presentation with configurable theme and information density.") {
            SettingLabel("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            SettingLabel("Density")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DensityMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.densityMode == mode,
                        onClick = { viewModel.setDensity(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        SettingsCard("Editor preferences", "These preferences apply to the editor surface without changing the SAF workspace boundary.") {
            SettingLabel("Font size")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorFontSize.entries.forEach { size ->
                    FilterChip(
                        selected = settings.editorFontSize == size,
                        onClick = { viewModel.setEditorFont(size) },
                        label = { Text(size.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            PreferenceSwitch("Word wrap", settings.wordWrap, viewModel::setWordWrap)
            PreferenceSwitch("Show invisibles", settings.showInvisibles, viewModel::setInvisibles)
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
