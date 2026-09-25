package com.mrredhood.devforge.core.github

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildOutputSettings
import com.mrredhood.devforge.core.build.GitHubBuildSettingsStore

private val GITIGNORE_TEMPLATES = listOf(
    "" to "None", "Android" to "Android", "Kotlin" to "Kotlin", "Java" to "Java",
    "Node" to "Node", "Python" to "Python", "Go" to "Go", "Rust" to "Rust", "Dart" to "Dart",
)

private val LICENSE_TEMPLATES = listOf(
    "" to "None", "mit" to "MIT License", "apache-2.0" to "Apache 2.0",
    "gpl-3.0" to "GPL-3.0", "bsd-3-clause" to "BSD 3-Clause",
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GitHubRepositoryCreationScreen(
    onBack: () -> Unit,
    onCreated: (GitHubRepository) -> Unit,
    viewModel: GitHubRepositoryCreationViewModel = viewModel(),
) {
    val state = viewModel.state
    val form = state.form
    var gitignoreExpanded by remember { mutableStateOf(false) }
    var licenseExpanded by remember { mutableStateOf(false) }
    var ownerExpanded by remember { mutableStateOf(false) }
    var visibilityExpanded by remember { mutableStateOf(false) }
    var squashTitleExpanded by remember { mutableStateOf(false) }
    var squashMessageExpanded by remember { mutableStateOf(false) }
    var mergeTitleExpanded by remember { mutableStateOf(false) }
    var buildArtifact by remember { mutableStateOf(true) }
    var publishArtifacts by remember { mutableStateOf(true) }
    var lintReport by remember { mutableStateOf(true) }
    var unitTestReport by remember { mutableStateOf(true) }
    var dependencyReport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val buildSettingsStore = remember(context) { GitHubBuildSettingsStore(context) }

    LaunchedEffect(state.created?.fullName) {
        state.created?.let { repository ->
            buildSettingsStore.set(
                repository.owner,
                repository.name,
                BuildOutputSettings(
                    buildArtifact = buildArtifact,
                    publishArtifacts = publishArtifacts,
                    lintReport = lintReport,
                    unitTestReport = unitTestReport,
                    dependencyReport = dependencyReport,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create GitHub repository", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("github-repository-creation-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Create directly in GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "The repository is created through the connected GitHub account. After creation it can be opened as the active DevForge workspace.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                ExposedDropdownMenuBox(
                    expanded = ownerExpanded,
                    onExpandedChange = { ownerExpanded = !ownerExpanded },
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = form.organization ?: "Personal account",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("Repository owner") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ownerExpanded) },
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = ownerExpanded,
                        onDismissRequest = { ownerExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Personal account") },
                            onClick = {
                                viewModel.update(form.copy(organization = null, visibility = if (form.visibility == "internal") "private" else form.visibility))
                                ownerExpanded = false
                            },
                        )
                        state.organizations.forEach { organization ->
                            DropdownMenuItem(
                                text = { Text(organization) },
                                onClick = {
                                    viewModel.update(form.copy(
                                        organization = organization,
                                        visibility = if (form.visibility == "internal") "private" else form.visibility,
                                    ))
                                    ownerExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                ExposedDropdownMenuBox(
                    expanded = visibilityExpanded,
                    onExpandedChange = { visibilityExpanded = !visibilityExpanded },
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = form.visibility.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("Visibility") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = visibilityExpanded) },
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = visibilityExpanded,
                        onDismissRequest = { visibilityExpanded = false },
                    ) {
                        listOf("public", "private")
                            .plus(if (form.organization != null) listOf("internal") else emptyList())
                            .forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        viewModel.update(form.copy(visibility = value, private = value == "private"))
                                        visibilityExpanded = false
                                    },
                                )
                            }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { viewModel.update(form.copy(name = it.take(100))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repository name") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { viewModel.update(form.copy(description = it.take(5000))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 3,
                )
            }
            item {
                OutlinedTextField(
                    value = form.homepage,
                    onValueChange = { viewModel.update(form.copy(homepage = it.take(2000))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Homepage URL") },
                    singleLine = true,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Default merge settings", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(
                        "Squash title",
                        form.squashMergeTitle,
                        listOf("PR_TITLE", "COMMIT_OR_PR_TITLE"),
                        squashTitleExpanded,
                        { squashTitleExpanded = it },
                    ) { value ->
                        viewModel.update(form.copy(squashMergeTitle = value))
                        squashTitleExpanded = false
                    }
                    ChoiceRow(
                        "Squash message",
                        form.squashMergeMessage,
                        listOf("PR_BODY", "COMMIT_MESSAGES", "BLANK"),
                        squashMessageExpanded,
                        { squashMessageExpanded = it },
                    ) { value ->
                        viewModel.update(form.copy(squashMergeMessage = value))
                        squashMessageExpanded = false
                    }
                    ChoiceRow(
                        "Merge commit title",
                        form.mergeCommitTitle,
                        listOf("PR_TITLE", "MERGE_MESSAGE"),
                        mergeTitleExpanded,
                        { mergeTitleExpanded = it },
                    ) { value ->
                        viewModel.update(form.copy(mergeCommitTitle = value))
                        mergeTitleExpanded = false
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = form.defaultBranch,
                    onValueChange = { viewModel.update(form.copy(defaultBranch = it.take(255))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default branch") },
                    singleLine = true,
                )
            }
            if (form.organization != null) {
                item {
                    OutlinedTextField(
                        value = form.teamId,
                        onValueChange = { viewModel.update(form.copy(teamId = it.take(20))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Team ID (optional)") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = form.customPropertiesJson,
                        onValueChange = { viewModel.update(form.copy(customPropertiesJson = it.take(8_000))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom properties JSON (optional)") },
                        minLines = 3,
                    )
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CheckRow("Private repository", form.private, { viewModel.update(form.copy(private = it)) }, Icons.Default.Lock)
                        CheckRow("Initialize with README", form.autoInit, { viewModel.update(form.copy(autoInit = it)) }, Icons.Default.Info)
                        CheckRow("Issues", form.hasIssues, { viewModel.update(form.copy(hasIssues = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Projects", form.hasProjects, { viewModel.update(form.copy(hasProjects = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Wiki", form.hasWiki, { viewModel.update(form.copy(hasWiki = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Discussions", form.hasDiscussions, { viewModel.update(form.copy(hasDiscussions = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Downloads", form.hasDownloads, { viewModel.update(form.copy(hasDownloads = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Repository template", form.isTemplate, { viewModel.update(form.copy(isTemplate = it)) }, Icons.Default.Info)
                        CheckRow("Delete head branch after merge", form.deleteBranchOnMerge, { viewModel.update(form.copy(deleteBranchOnMerge = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Allow squash merge", form.allowSquashMerge, { viewModel.update(form.copy(allowSquashMerge = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Allow merge commit", form.allowMergeCommit, { viewModel.update(form.copy(allowMergeCommit = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Allow rebase merge", form.allowRebaseMerge, { viewModel.update(form.copy(allowRebaseMerge = it)) }, Icons.Default.CheckCircle)
                        CheckRow("Allow auto-merge", form.allowAutoMerge, { viewModel.update(form.copy(allowAutoMerge = it)) }, Icons.Default.CheckCircle)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("DevForge build outputs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Choose what this new repository should build and publish when DevForge starts its Android CI workflow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CreationBuildSettingRow(
                            "Build selected APK/AAB",
                            buildArtifact,
                        ) { buildArtifact = it }
                        CreationBuildSettingRow(
                            "Upload build artifact",
                            publishArtifacts,
                        ) { publishArtifacts = it }
                        CreationBuildSettingRow(
                            "Lint report",
                            lintReport,
                        ) { lintReport = it }
                        CreationBuildSettingRow(
                            "Unit-test report",
                            unitTestReport,
                        ) { unitTestReport = it }
                        CreationBuildSettingRow(
                            "Dependency report",
                            dependencyReport,
                        ) { dependencyReport = it }
                    }
                }
            }
            item {
                ExposedDropdownMenuBox(
                    expanded = gitignoreExpanded,
                    onExpandedChange = { gitignoreExpanded = !gitignoreExpanded },
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = GITIGNORE_TEMPLATES.firstOrNull { it.first == form.gitignoreTemplate }?.second ?: "None",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text(".gitignore template") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gitignoreExpanded) },
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = gitignoreExpanded,
                        onDismissRequest = { gitignoreExpanded = false },
                    ) {
                        GITIGNORE_TEMPLATES.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.update(form.copy(gitignoreTemplate = value))
                                    gitignoreExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Merge commit message", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.update(form.copy(mergeCommitMessage = "PR_TITLE")) },
                            enabled = form.mergeCommitMessage != "PR_TITLE",
                        ) { Text("PR title") }
                        OutlinedButton(
                            onClick = { viewModel.update(form.copy(mergeCommitMessage = "MERGE_MESSAGE")) },
                            enabled = form.mergeCommitMessage != "MERGE_MESSAGE",
                        ) { Text("Merge message") }
                    }
                }
            }
            item {
                ExposedDropdownMenuBox(
                    expanded = licenseExpanded,
                    onExpandedChange = { licenseExpanded = !licenseExpanded },
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = LICENSE_TEMPLATES.firstOrNull { it.first == form.licenseTemplate }?.second ?: "None",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("License template") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = licenseExpanded) },
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = licenseExpanded,
                        onDismissRequest = { licenseExpanded = false },
                    ) {
                        LICENSE_TEMPLATES.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.update(form.copy(licenseTemplate = value))
                                    licenseExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.created?.let { createdRepo ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Repository created", fontWeight = FontWeight.Bold)
                            Text(createdRepo.fullName)
                            Text("Default branch: " + createdRepo.defaultBranch)
                            Button(
                                onClick = {
                                    buildSettingsStore.set(
                                        createdRepo.owner,
                                        createdRepo.name,
                                        BuildOutputSettings(
                                            buildArtifact = buildArtifact,
                                            publishArtifacts = publishArtifacts,
                                            lintReport = lintReport,
                                            unitTestReport = unitTestReport,
                                            dependencyReport = dependencyReport,
                                        ),
                                    )
                                    onCreated(createdRepo)
                                },
                            ) {
                                Text("Open as workspace")
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = viewModel::create,
                        enabled = !state.isCreating && state.created == null,
                    ) { Text(if (state.isCreating) "Creating…" else "Create repository") }
                    OutlinedButton(onClick = onBack, enabled = !state.isCreating) { Text("Cancel") }
                }
            }
        }
    }
}


@Composable
private fun CreationBuildSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    title: String,
    selected: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange(!expanded) },
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selected,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(title)
    }
}
