package com.mrredhood.devforge.core.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DevForgeSettingsScreen(
    viewModel: DevForgeSettingsViewModel,
    section: String = "home",
    onSectionChange: (String) -> Unit = {},
) {

    when (section) {
        "routing" -> SettingsDetail("AI routing") {
            val settings = viewModel.settings
            SettingLabel("Routing mode")
            settings.aiRoutingMode.let { current ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiRoutingMode.entries.forEach { mode ->
                        SettingsChoiceRow(
                            title = mode.name.replace('_', ' '),
                            selected = current == mode,
                            onClick = { viewModel.setAiRouting(mode) },
                        )
                    }
                }
            }
        }

        "build" -> {
            val settings = viewModel.settings
            var buildPoll by remember(settings.buildPollSeconds) { mutableStateOf(settings.buildPollSeconds.toString()) }
            SettingsDetail("Build") {
                SettingsHint("Build polling controls how often DevForge checks remote build status.")
                NumberField("Build polling seconds", buildPoll) { buildPoll = it.take(3) }
                SettingsAction("Apply") { viewModel.setBuildPoll(buildPoll) }
            }
        }

        "terminal" -> {
            val settings = viewModel.settings
            var timeout by remember(settings.terminalTimeoutMs) { mutableStateOf(settings.terminalTimeoutMs.toString()) }

            SettingsDetail("Terminal") {
                SettingsHint("Default interactive timeout. The hard execution ceiling remains enforced by the terminal capability.")
                NumberField("Default timeout (ms)", timeout) { timeout = it.take(5) }
                SettingsAction("Apply") { viewModel.setTerminalTimeout(timeout) }
            }
        }

        "privacy" -> {
            val settings = viewModel.settings
            var auditRetention by remember(settings.auditRetentionDays) { mutableStateOf(settings.auditRetentionDays.toString()) }
            var chatRetention by remember(settings.chatRetentionDays) { mutableStateOf(settings.chatRetentionDays.toString()) }

            SettingsDetail("Privacy & retention") {
                SettingsHint("Local history is bounded. Secret redaction remains active independently.")
                NumberField("Audit retention (days)", auditRetention) { auditRetention = it.take(3) }
                NumberField("Chat retention (days)", chatRetention) { chatRetention = it.take(4) }
                SettingsAction("Apply") {
                    viewModel.setAuditRetention(auditRetention)
                    viewModel.setChatRetention(chatRetention)
                }
            }
        }

        "appearance" -> {
            SettingsDetail("Appearance") {
                SettingLabel("Theme")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        SettingsChoiceRow(
                            title = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = viewModel.settings.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                        )
                    }
                }
                SettingLabel("Density")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DensityMode.entries.forEach { mode ->
                        SettingsChoiceRow(
                            title = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = viewModel.settings.densityMode == mode,
                            onClick = { viewModel.setDensity(mode) },
                        )
                    }
                }
            }
        }

        "editor" -> {
            SettingsDetail("Editor") {
                SettingLabel("Font size")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorFontSize.entries.forEach { size ->
                        SettingsChoiceRow(
                            title = size.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = viewModel.settings.editorFontSize == size,
                            onClick = { viewModel.setEditorFont(size) },
                        )
                    }
                }
                PreferenceSwitch("Word wrap", viewModel.settings.wordWrap, viewModel::setWordWrap)
                PreferenceSwitch("Show invisibles", viewModel.settings.showInvisibles, viewModel::setInvisibles)
                PreferenceSwitch("Autosave", viewModel.settings.autoSaveEnabled, viewModel::setAutoSaveEnabled)
                var autosaveInterval by remember(viewModel.settings.autoSaveIntervalMs) {
                    mutableStateOf(viewModel.settings.autoSaveIntervalMs.toString())
                }
                NumberField("Autosave interval (ms)", autosaveInterval) {
                    autosaveInterval = it.filter(Char::isDigit).take(11)
                }
                SettingsAction("Apply autosave interval") {
                    viewModel.setAutoSaveIntervalMs(autosaveInterval)
                }
                Text(
                    "Allowed: 1,000 ms to 10,000,000,000 ms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "App settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Open only the section you need.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
            item { SimpleSettingsTile("AI routing", "How DevForge chooses a model", { onSectionChange("routing") }) }
            item { SimpleSettingsTile("Build", "Polling for remote build status", { onSectionChange("build") }) }
            item { SimpleSettingsTile("Terminal", "Default execution timeout", { onSectionChange("terminal") }) }
            item { SimpleSettingsTile("Privacy & retention", "Local history limits", { onSectionChange("privacy") }) }
            item { SimpleSettingsTile("Appearance", "Theme and information density", { onSectionChange("appearance") }) }
            item { SimpleSettingsTile("Editor", "Font size and editing behavior", { onSectionChange("editor") }) }
        }
    }
}

@Composable
private fun SettingsDetail(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun SimpleSettingsTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(title) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsHint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsAction(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

@Composable
private fun PreferenceSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
