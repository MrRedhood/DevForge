package com.mrredhood.devforge

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.ai.AIChatScreen
import com.mrredhood.devforge.core.ai.AISettingsScreen
import com.mrredhood.devforge.core.automation.AutomationCenterScreen
import com.mrredhood.devforge.core.agent.AgentCenterScreen
import com.mrredhood.devforge.core.build.BuildCenterScreen
import com.mrredhood.devforge.core.editor.ChainedVisualTransformation
import com.mrredhood.devforge.core.editor.CodeSyntaxVisualTransformation
import com.mrredhood.devforge.core.editor.EditorFolding
import com.mrredhood.devforge.core.editor.EditorLanguage
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.editor.FoldingVisualTransformation
import com.mrredhood.devforge.core.editor.VisibleWhitespaceVisualTransformation
import com.mrredhood.devforge.core.git.GitDashboardScreen
import com.mrredhood.devforge.core.git.GitDiffScreen
import com.mrredhood.devforge.core.model.DevForgeDestination
import com.mrredhood.devforge.core.policy.ApprovalCenterScreen
import com.mrredhood.devforge.core.settings.DevForgeSettingsScreen
import com.mrredhood.devforge.core.settings.DevForgeSettingsViewModel
import com.mrredhood.devforge.core.settings.DensityMode
import com.mrredhood.devforge.core.settings.ThemeMode
import com.mrredhood.devforge.core.terminal.TerminalScreen
import com.mrredhood.devforge.core.security.CredentialSecurityScreen
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import com.mrredhood.devforge.ui.theme.DevForgeTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val database = DevForgeDatabase.get(this@MainActivity)
            val approvals = ApprovalRepository(database.approvalDao())
            approvals.expireDue()
            approvals.recoverStaleExecuting()
            com.mrredhood.devforge.core.storage.DurableStateRepository(database)
                .reconcileWaitingAgentApprovals()
        }
        setContent {
            val settings: DevForgeSettingsViewModel = viewModel()
            DevForgeTheme(
                themeMode = settings.settings.themeMode,
                densityMode = settings.settings.densityMode,
            ) { DevForgeApp(settings) }
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun DevForgeApp(settings: DevForgeSettingsViewModel) {
    val context = LocalContext.current
    val windowSize = calculateWindowSizeClass(context as FragmentActivity)
    var destinationName by rememberSaveable { mutableStateOf(DevForgeDestination.Chat.name) }
    var destinationHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showGlobalSearch by rememberSaveable { mutableStateOf(false) }
    var globalSearchQuery by rememberSaveable { mutableStateOf("") }
    var unsavedEditorUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val destination = DevForgeDestination.valueOf(destinationName)
    val expanded = windowSize.widthSizeClass != WindowWidthSizeClass.Compact
    val workspace: WorkspaceViewModel = viewModel()
    val editor: EditorViewModel = viewModel()
    val editing = editor.activeTab != null || editor.isLoading

    fun navigateTo(next: DevForgeDestination) {
        if (next.name == destinationName) return
        destinationHistory = destinationHistory + destinationName
        destinationName = next.name
    }

    BackHandler(enabled = true) {
        val activeEditorTab = editor.activeTab
        when {
            activeEditorTab != null -> {
                if (activeEditorTab.isDirty) unsavedEditorUri = activeEditorTab.uri
                else editor.close(activeEditorTab.uri)
            }
            destinationHistory.isNotEmpty() -> {
                val previous = destinationHistory.last()
                destinationHistory = destinationHistory.dropLast(1)
                destinationName = previous
            }
            else -> showExitDialog = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DevForgeTopBar(
                workspaceName = workspace.workspace?.name ?: "No workspace",
                editing = editing,
                showAgents = destination == DevForgeDestination.Chat && !editing,
                onSearch = { showGlobalSearch = true },
                onSecurity = { navigateTo(DevForgeDestination.Approvals) },
                onAgents = { navigateTo(DevForgeDestination.Agents) },
            )
        },
        bottomBar = { if (!expanded && !editing) NavigationBottom(destination, ::navigateTo) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded && !editing) NavigationSide(destination, ::navigateTo)
            if (editing) EditorScreen(editor, settings) else DestinationScreen(destination, workspace, editor, settings)
        }
    }

    unsavedEditorUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { unsavedEditorUri = null },
            title = { Text("Unsaved changes") },
            text = { Text("This file has changes that have not been saved. What should happen before going back?") },
            confirmButton = {
                TextButton(onClick = {
                    unsavedEditorUri = null
                    editor.saveAndCloseActive()
                }) { Text("Save & go back") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        unsavedEditorUri = null
                        editor.close(uri, discard = true)
                    }) { Text("Discard") }
                    TextButton(onClick = { unsavedEditorUri = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (showGlobalSearch) {
        AlertDialog(
            onDismissRequest = { showGlobalSearch = false },
            title = { Text("Search workspace") },
            text = {
                OutlinedTextField(
                    value = globalSearchQuery,
                    onValueChange = { globalSearchQuery = it.take(240) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("File or folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGlobalSearch = false
                    if (globalSearchQuery.isNotBlank()) {
                        workspace.search(globalSearchQuery)
                        navigateTo(DevForgeDestination.Files)
                    }
                }) { Text("Search") }
            },
            dismissButton = { TextButton(onClick = { showGlobalSearch = false }) { Text("Cancel") } },
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit DevForge?") },
            text = { Text("You are already at the first screen. Do you want to exit the app?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? FragmentActivity)?.finish()
                }) { Text("Exit") }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevForgeTopBar(
    workspaceName: String,
    editing: Boolean,
    showAgents: Boolean,
    onSearch: () -> Unit,
    onSecurity: () -> Unit,
    onAgents: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text("DevForge", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(
                    if (editing) "Editor / $workspaceName" else workspaceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            Surface(
                modifier = Modifier.padding(start = 10.dp).size(38.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Default.Code, "DevForge", Modifier.padding(9.dp)) }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
            IconButton(onClick = onSecurity) { Icon(Icons.Default.Security, "Security") }
            if (showAgents) {
                IconButton(onClick = onAgents) {
                    Text("👤", fontSize = 21.sp)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun NavigationBottom(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    var moreExpanded by remember { mutableStateOf(false) }
    val primary = listOf(
        DevForgeDestination.Chat,
        DevForgeDestination.Files,
        DevForgeDestination.Build,
        DevForgeDestination.Agents,
        DevForgeDestination.Settings,
    )
    val secondary = listOf(
        DevForgeDestination.Git,
        DevForgeDestination.Diffs,
        DevForgeDestination.Terminal,
        DevForgeDestination.Automations,
        DevForgeDestination.Approvals,
    )

    NavigationBar {
        primary.forEach { item ->
            NavigationBarItem(
                selected = current == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label, maxLines = 1) },
            )
        }
        NavigationBarItem(
            selected = current in secondary,
            onClick = { moreExpanded = true },
            icon = {
                Box {
                    Icon(Icons.Default.MoreVert, "More")
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
                        secondary.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = {
                                    moreExpanded = false
                                    onSelect(item)
                                },
                            )
                        }
                    }
                }
            },
            label = { Text("More") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationSide(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight().width(88.dp)) {
        Spacer(Modifier.height(18.dp))
        DevForgeDestination.entries.forEach { item ->
            NavigationRailItem(
                selected = current == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun DestinationScreen(destination: DevForgeDestination, workspace: WorkspaceViewModel, editor: EditorViewModel, settings: DevForgeSettingsViewModel) {
    when (destination) {
        DevForgeDestination.Chat -> AIChatScreen()
        DevForgeDestination.Files -> FilesScreen(workspace, editor)
        DevForgeDestination.Git -> GitDashboardScreen()
        DevForgeDestination.Diffs -> GitDiffScreen()
        DevForgeDestination.Build -> BuildCenterScreen()
        DevForgeDestination.Terminal -> TerminalScreen()
        DevForgeDestination.Agents -> AgentCenterScreen()
        DevForgeDestination.Automations -> AutomationCenterScreen()
        DevForgeDestination.Approvals -> ApprovalCenterScreen()
        DevForgeDestination.Settings -> SettingsScreen(settings)
    }
}

@Composable
private fun FilesScreen(workspace: WorkspaceViewModel, editor: EditorViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(workspace::openWorkspace) }
    BackHandler(enabled = workspace.breadcrumbs.size > 1) { workspace.goUp() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Files", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (workspace.workspace == null) "Choose a workspace to begin" else "Workspace browser",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { picker.launch(null) }) {
                    Icon(Icons.Default.Folder, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (workspace.workspace == null) "Choose" else "Change")
                }
                if (workspace.workspace != null) {
                    IconButton(onClick = workspace::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            }
        }
        if (workspace.workspace == null) {
            item { InfoCard("Bring your code into DevForge", "DevForge uses an Android document-tree permission for the folder you explicitly choose.") }
        } else {
            item { Breadcrumbs(workspace) }
            item { WorkspaceIntelligenceCard(workspace) }
            if (workspace.isSearching || workspace.searchResults.isNotEmpty()) {
                item { WorkspaceSearchResults(workspace, editor) }
            } else if (workspace.isLoading) {
                item { LoadingCard("Reading folder…") }
            } else if (workspace.entries.isEmpty()) {
                item { InfoCard("Nothing in this folder", "Create a file here and refresh.") }
            } else {
                items(workspace.entries, key = { it.uri.toString() }) { entry ->
                    FileRow(entry) { if (entry.isDirectory) workspace.openDirectory(entry) else editor.open(entry) }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSearchResults(workspace: WorkspaceViewModel, editor: EditorViewModel) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Search results", fontWeight = FontWeight.Bold)
                    Text(
                        workspace.searchQuery,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = workspace::clearSearch) { Text("Clear") }
            }
            if (workspace.isSearching) {
                LoadingCard("Searching workspace…")
            } else if (workspace.searchResults.isEmpty()) {
                Text("No matching files or folders.")
            } else {
                workspace.searchResults.take(60).forEach { result ->
                    FileRow(
                        WorkspaceEntry(
                            uri = result.uri,
                            name = result.name,
                            isDirectory = result.isDirectory,
                            sizeBytes = result.sizeBytes,
                        )
                    ) {
                        if (result.isDirectory) {
                            workspace.openDirectory(
                                WorkspaceEntry(
                                    uri = result.uri,
                                    name = result.name,
                                    isDirectory = true,
                                    sizeBytes = result.sizeBytes,
                                )
                            )
                            workspace.clearSearch()
                        } else {
                            editor.open(
                                WorkspaceEntry(
                                    uri = result.uri,
                                    name = result.name,
                                    isDirectory = false,
                                    sizeBytes = result.sizeBytes,
                                )
                            )
                            workspace.clearSearch()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(workspace: WorkspaceViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        workspace.breadcrumbs.forEachIndexed { index, crumb ->
            if (index > 0) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp).alpha(.45f))
            FilterChip(
                selected = index == workspace.breadcrumbs.lastIndex,
                onClick = { workspace.goToBreadcrumb(index) },
                label = { Text(crumb.name, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun FileRow(entry: WorkspaceEntry, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold)
                Text(
                    if (entry.isDirectory) "Folder" else formatBytes(entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.alpha(.45f))
        }
    }
}

@Composable
private fun EditorScreen(editor: EditorViewModel, settings: DevForgeSettingsViewModel) {
    val active = editor.activeTab ?: return
    var fieldValue by remember(active.uri) { mutableStateOf(TextFieldValue(active.content)) }
    var showFind by remember(active.uri) { mutableStateOf(false) }
    var showGoToLine by remember(active.uri) { mutableStateOf(false) }
    var showSymbols by remember(active.uri) { mutableStateOf(false) }
    var findQuery by remember(active.uri) { mutableStateOf("") }
    var replaceQuery by remember(active.uri) { mutableStateOf("") }
    var replaceMessage by remember(active.uri) { mutableStateOf<String?>(null) }
    var lineQuery by remember(active.uri) { mutableStateOf("") }
    var collapsedStarts by remember(active.uri) { mutableStateOf(emptySet<Int>()) }
    val horizontalEditorScroll = rememberScrollState()

    LaunchedEffect(active.content) {
        if (fieldValue.text != active.content) {
            fieldValue = TextFieldValue(active.content, TextRange(active.content.length))
        }
        val validStarts = EditorFolding.ranges(active.content).map { it.startOffset }.toSet()
        collapsedStarts = collapsedStarts.intersect(validStarts)
    }

    val byteSize = active.content.toByteArray(Charsets.UTF_8).size
    val advanced = byteSize <= 256 * 1024
    val foldRanges = if (advanced) EditorFolding.ranges(active.content) else emptyList()
    val activeFolds = foldRanges.filter { it.startOffset in collapsedStarts }
    val language = EditorLanguage.detect(active.name)
    val syntax = CodeSyntaxVisualTransformation(
        language = language,
        keywordColor = MaterialTheme.colorScheme.primary,
        stringColor = MaterialTheme.colorScheme.tertiary,
        commentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        numberColor = MaterialTheme.colorScheme.secondary,
    )
    val baseTransformation: VisualTransformation =
        if (activeFolds.isEmpty()) syntax
        else ChainedVisualTransformation(FoldingVisualTransformation(active.content, activeFolds), syntax)
    val transformation: VisualTransformation =
        if (settings.settings.showInvisibles && advanced) {
            ChainedVisualTransformation(baseTransformation, VisibleWhitespaceVisualTransformation())
        } else {
            baseTransformation
        }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(active.name, fontWeight = FontWeight.Bold)
                Text(
                    language.name.replace('_', ' ') + " · " + (active.content.count { it == '\n' } + 1) + " lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (active.isDirty) Text("Unsaved", color = MaterialTheme.colorScheme.tertiary)
            IconButton(onClick = editor::undo, enabled = advanced) { Text("↶") }
            IconButton(onClick = editor::redo, enabled = advanced) { Text("↷") }
            IconButton(onClick = { showFind = true }) { Text("⌕") }
            IconButton(onClick = { showGoToLine = true }) { Text("#") }
            IconButton(onClick = { showSymbols = true }, enabled = advanced) { Text("⌘") }
            IconButton(onClick = editor::saveActive, enabled = active.isDirty) { Icon(Icons.Default.Save, "Save") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = {
                fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
            }) { Text("Select all") }
            TextButton(onClick = {
                val cursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
                val startLine = (fieldValue.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1).coerceAtLeast(0)
                val endLine = fieldValue.text.indexOf('\n', cursor).let { if (it < 0) fieldValue.text.length else it }
                fieldValue = fieldValue.copy(selection = TextRange(startLine, endLine))
            }) { Text("Select line") }
            if (foldRanges.isNotEmpty()) {
                TextButton(onClick = { collapsedStarts = foldRanges.map { it.startOffset }.toSet() }) { Text("Fold all") }
                TextButton(onClick = { collapsedStarts = emptySet() }) { Text("Unfold all") }
                foldRanges.take(10).forEach { range ->
                    FilterChip(
                        selected = range.startOffset in collapsedStarts,
                        onClick = {
                            collapsedStarts = if (range.startOffset in collapsedStarts) {
                                collapsedStarts - range.startOffset
                            } else {
                                collapsedStarts + range.startOffset
                            }
                        },
                        label = { Text(range.startLine.toString() + "–" + range.endLine.toString()) },
                    )
                }
            }
        }

        if (!advanced) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Text(
                    "Large file safeguard: syntax highlighting, folding and editor diagnostics pause above 256 KiB. Editing remains supported up to 8 MiB, with bounded undo history.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (editor.diagnostics.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Problems · " + editor.diagnostics.size, fontWeight = FontWeight.Bold)
                    editor.diagnostics.take(8).forEach { diagnostic ->
                        val location = diagnostic.location?.let { ":" + it.line + ":" + it.column }.orEmpty()
                        Text(
                            (diagnostic.code?.let { it + " · " }.orEmpty()) + diagnostic.message + location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        Divider()
        val editorModifier = if (settings.settings.wordWrap) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalEditorScroll)
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                editor.updateContent(it.text)
            },
            modifier = editorModifier,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = settings.settings.editorFontSize.sp.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            visualTransformation = transformation,
            decorationBox = { inner -> Box(Modifier.fillMaxSize()) { inner() } },
        )
    }

    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            title = { Text("Find & replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it.take(1_000); replaceMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Find") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it.take(4_000); replaceMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Replace with") },
                        singleLine = true,
                    )
                    replaceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val count = editor.replaceAll(findQuery, replaceQuery)
                    replaceMessage = count.toString() + " replacements"
                }) { Text("Replace all") }
            },
            dismissButton = { TextButton(onClick = { showFind = false }) { Text("Done") } },
        )
    }

    if (showGoToLine) {
        AlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = { Text("Go to line") },
            text = {
                OutlinedTextField(
                    value = lineQuery,
                    onValueChange = { lineQuery = it.take(10) },
                    label = { Text("Line number") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val line = lineQuery.toIntOrNull() ?: 1
                    val offset = editor.lineStartOffset(line)
                    fieldValue = fieldValue.copy(selection = TextRange(offset))
                    showGoToLine = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text("Cancel") } },
        )
    }

    if (showSymbols) {
        AlertDialog(
            onDismissRequest = { showSymbols = false },
            title = { Text("Symbols") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(editor.symbolCandidates(), key = { it.name + ":" + it.line }) { symbol ->
                        TextButton(onClick = {
                            val offset = editor.lineStartOffset(symbol.line)
                            fieldValue = fieldValue.copy(selection = TextRange(offset))
                            showSymbols = false
                        }) {
                            Text(
                                symbol.name + " · " + symbol.kind + " · line " + symbol.line,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSymbols = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun SettingsScreen(settings: DevForgeSettingsViewModel) {
    var section by rememberSaveable { mutableStateOf("home") }

    BackHandler(enabled = section != "home") {
        section = "home"
    }

    when (section) {
        "ai" -> SimpleSettingsSection(
            title = "AI & models",
            onBack = { section = "home" },
        ) {
            AISettingsScreen()
        }

        "security" -> SimpleSettingsSection(
            title = "Security",
            onBack = { section = "home" },
        ) {
            CredentialSecurityScreen()
        }

        "app" -> SimpleSettingsSection(
            title = "App settings",
            onBack = { section = "home" },
        ) {
            DevForgeSettingsScreen(settings)
        }

        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Keep setup and maintenance in a few clear sections.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                SimpleSettingsTile(
                    title = "AI & models",
                    subtitle = "Provider keys and model connections",
                    onClick = { section = "ai" },
                )
            }
            item {
                SimpleSettingsTile(
                    title = "Security",
                    subtitle = "Keystore and biometric protection",
                    onClick = { section = "security" },
                )
            }
            item {
                SimpleSettingsTile(
                    title = "App settings",
                    subtitle = "GitHub, build, terminal, privacy, appearance and editor",
                    onClick = { section = "app" },
                )
            }
        }
    }
}

@Composable
private fun SimpleSettingsSection(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
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
private fun InfoCard(title: String, message: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(message)
        }
    }
}

private fun formatBytes(value: Long?): String = when {
    value == null || value < 0L -> "File"
    value < 1024L -> "$value B"
    value < 1024L * 1024L -> "${value / 1024L} KB"
    else -> "${value / (1024L * 1024L)} MB"
}


@Composable
private fun WorkspaceIntelligenceCard(workspace: WorkspaceViewModel) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Workspace intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                if (workspace.isIndexing) "Building bounded symbol index…" else workspace.indexedSymbolCount.toString() + " symbols indexed",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = workspace::rebuildSymbolIndex, enabled = !workspace.isIndexing) {
                Text(if (workspace.isIndexing) "Indexing…" else "Build index")
            }
            OutlinedTextField(
                value = workspace.symbolQuery,
                onValueChange = workspace::searchSymbols,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find symbol") },
                singleLine = true,
            )
            workspace.symbolResults.take(8).forEach { symbol ->
                Text(
                    symbol.name + " · " + symbol.kind + " · " + symbol.path + ":" + symbol.line,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Workspace knowledge", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = workspace.knowledgeTitle,
                onValueChange = { workspace.knowledgeTitle = it.take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = workspace.knowledgeContent,
                onValueChange = { workspace.knowledgeContent = it.take(4_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What should DevForge remember?") },
                minLines = 2,
                maxLines = 5,
            )
            TextButton(
                onClick = workspace::rememberKnowledge,
                enabled = workspace.knowledgeTitle.isNotBlank() && workspace.knowledgeContent.isNotBlank(),
            ) { Text("Save knowledge") }
            workspace.knowledge.take(5).forEach { note ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(note.title, fontWeight = FontWeight.SemiBold)
                        Text(note.content.take(220), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { workspace.removeKnowledge(note.id) }) { Text("Remove") }
                }
            }
            workspace.knowledgeMessage?.let { message ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = workspace::clearKnowledgeMessage) { Text("Dismiss") }
                }
            }
        }
    }
}
