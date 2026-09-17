package com.mrredhood.devforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.model.DevForgeDestination
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import com.mrredhood.devforge.ui.theme.DevForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DevForgeTheme { DevForgeApp() } }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun DevForgeApp() {
    val windowSize = calculateWindowSizeClass(androidx.compose.ui.platform.LocalContext.current as ComponentActivity)
    var destinationName by rememberSaveable { mutableStateOf(DevForgeDestination.Chat.name) }
    val current = DevForgeDestination.valueOf(destinationName)
    val expanded = windowSize.widthSizeClass != WindowWidthSizeClass.Compact
    val workspaceViewModel: WorkspaceViewModel = viewModel()
    val editorViewModel: EditorViewModel = viewModel()
    val editing = editorViewModel.activeTab != null || editorViewModel.isLoading

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ForgeTopBar(workspaceViewModel.workspace?.name ?: "No workspace", editing) },
        bottomBar = { if (!expanded && !editing) ForgeBottomBar(current) { destinationName = it.name } },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded && !editing) ForgeRail(current) { destinationName = it.name }
            if (editing) EditorScreen(editorViewModel)
            else ForgeContent(current, workspaceViewModel, editorViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgeTopBar(workspaceName: String, editing: Boolean) {
    TopAppBar(
        title = {
            Column {
                Text("DevForge", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(if (editing) "editor / $workspaceName" else workspaceName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            Surface(Modifier.padding(start = 10.dp).size(38.dp), RoundedCornerShape(13.dp), MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Code, "DevForge") }
            }
        },
        actions = {
            IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search") }
            BadgedBox(badge = { Badge { Text("1") } }) { IconButton(onClick = {}) { Icon(Icons.Default.NotificationsNone, "Activity") } }
            IconButton(onClick = {}) { Icon(Icons.Default.Security, "Permissions") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
    )
}

@Composable
private fun ForgeBottomBar(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
        DevForgeDestination.entries.forEach { item ->
            NavigationBarItem(current == item, { onSelect(item) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
        }
    }
}

@Composable
private fun ForgeRail(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight().width(88.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(18.dp))
        DevForgeDestination.entries.forEach { item ->
            NavigationRailItem(current == item, { onSelect(item) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
        }
    }
}

@Composable
private fun ForgeContent(destination: DevForgeDestination, workspace: WorkspaceViewModel, editor: EditorViewModel) {
    when (destination) {
        DevForgeDestination.Chat -> ChatScreen()
        DevForgeDestination.Files -> FilesScreen(workspace, editor)
        DevForgeDestination.Git -> GitScreen()
        DevForgeDestination.Build -> BuildScreen()
        DevForgeDestination.Settings -> SettingsScreen()
    }
}

@Composable
private fun ScreenFrame(content: @Composable (PaddingValues) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun ChatScreen() {
    ScreenFrame { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { HeroCard() }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(true, {}, label = { Text("@workspace") }); FilterChip(true, {}, label = { Text("@git-diff") }); FilterChip(false, {}, label = { Text("+ Add context") }) } }
            item { SectionLabel("Workspace pulse") }
            item { PulseCard("Git", "Repository-aware workflow is next", "Review") }
            item { PulseCard("Build", "Cloud build center ready", "Open") }
            item { PulseCard("Agent", "No pending approvals", "Activity") }
            item { SectionLabel("Ready when you are") }
            item { Card(RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(18.dp)) { Text("What should we work on?", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(14.dp)); Button({}) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Ask DevForge") } } } }
        }
    }
}

@Composable
private fun HeroCard() {
    Card(RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(.30f), MaterialTheme.colorScheme.secondary.copy(.10f), MaterialTheme.colorScheme.surfaceContainer)))) {
            Column(Modifier.padding(24.dp)) {
                AssistChip({}, label = { Text("AI control center") }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                Spacer(Modifier.height(16.dp))
                Text("Build. Review. Ship.", fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("A mobile engineering cockpit where AI proposes changes, you stay in control, and every action leaves a trail.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusPill("Online", Icons.Default.Wifi); StatusPill("Protected", Icons.Default.Security); StatusPill("Synced", Icons.Default.CloudDone) }
            }
        }
    }
}

@Composable
private fun FilesScreen(workspace: WorkspaceViewModel, editor: EditorViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(workspace::openWorkspace) }
    BackHandler(enabled = workspace.breadcrumbs.size > 1) { workspace.goUp() }

    ScreenFrame { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ScreenTitle("Files", if (workspace.workspace == null) "Choose a workspace to begin" else "Live workspace browser")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(null) }) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (workspace.workspace == null) "Choose workspace" else "Change")
                    }
                    if (workspace.workspace != null) IconButton(onClick = workspace::refresh) { Icon(Icons.Default.Refresh, "Refresh workspace") }
                }
            }
            if (workspace.workspace == null) {
                item { WorkspaceEmptyState() }
            } else {
                item { Breadcrumbs(workspace) }
                if (workspace.isLoading) item { LoadingCard("Reading folder…") }
                else if (workspace.entries.isEmpty()) item { EmptyFolderCard() }
                else {
                    item { SectionLabel("${workspace.currentName} • ${workspace.entries.size} entries") }
                    items(workspace.entries, key = { it.uri.toString() }) { entry ->
                        WorkspaceRow(entry) {
                            if (entry.isDirectory) workspace.openDirectory(entry) else editor.open(entry)
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
private fun WorkspaceEmptyState() {
    Card(RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, null, Modifier.size(42.dp)); Spacer(Modifier.height(14.dp))
            Text("Bring your code into DevForge", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("DevForge uses an Android document-tree permission for the folder you explicitly choose.", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
    Card(RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)); Text(text) } }
}

@Composable
private fun EmptyFolderCard() {
    Card(RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text("Nothing in this folder", fontWeight = FontWeight.Bold); Text("Create a file here and refresh.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) } }
}

@Composable
private fun WorkspaceRow(entry: WorkspaceEntry, onOpen: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, Modifier.size(23.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold)
                Text(if (entry.isDirectory) "Folder" else formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.alpha(.45f))
        }
    }
}

@Composable
private fun EditorScreen(editor: EditorViewModel) {
    val tab = editor.activeTab
    var showDiscard by remember { mutableStateOf(false) }
    BackHandler { if (tab?.isDirty == true) showDiscard = true else tab?.let { editor.close(it.uri) } }

    if (showDiscard && tab != null) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Your saved file is unchanged, but the current editing buffer will be removed.") },
            confirmButton = { TextButton(onClick = { editor.close(tab.uri, discard = true) }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (editor.isLoading) LoadingCard("Opening file…")
        editor.error?.let { message ->
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), RoundedCornerShape(16.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null); Spacer(Modifier.width(10.dp)); Text(message, Modifier.weight(1f)); TextButton(onClick = editor::dismissError) { Text("Dismiss") } }
            }
        }
        if (editor.tabs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editor.tabs.forEach { openTab ->
                    Surface(onClick = { editor.select(openTab.uri) }, shape = RoundedCornerShape(12.dp), color = if (openTab.uri == editor.activeUri) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (openTab.isDirty) "• ${openTab.name}" else openTab.name, style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { if (!openTab.isDirty) editor.close(openTab.uri) else if (openTab.uri == editor.activeUri) showDiscard = true }) { Icon(Icons.Default.Close, "Close") }
                        }
                    }
                }
            }
            val active = editor.activeTab
            if (active != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(active.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    if (active.isDirty) Text("Unsaved", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = editor::saveActive, enabled = active.isDirty) { Icon(Icons.Default.Save, "Save") }
                }
                Divider(Modifier.padding(vertical = 6.dp))
                BasicTextField(
                    value = active.content,
                    onValueChange = editor::updateContent,
                    modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onBackground),
                    decorationBox = { inner ->
                        if (active.content.isEmpty()) Text("Start editing…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        inner()
                    },
                )
            }
        }
    }
}

@Composable
private fun GitScreen() {
    ScreenFrame { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) { ScreenTitle("Git", "Repository state connects after editor/workspace foundations"); Spacer(Modifier.height(18.dp)); StatusPill("main", Icons.Default.Source); Spacer(Modifier.height(18.dp)); PulseCard("Repository model", "Branch, status, staged changes and remotes are next", "Planned") } }
}

@Composable
private fun BuildScreen() {
    ScreenFrame { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { ScreenTitle("Build", "Cloud builds without bundling the Android toolchain") }; item { PulseCard("Build Center", "GitHub Actions dispatch and artifact flow", "Coming next") }; item { PulseCard("Remote-first", "DevForge stays lightweight on-device", "Protected") } } }
}

@Composable
private fun SettingsScreen() {
    ScreenFrame { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { ScreenTitle("Settings", "Control how DevForge behaves") }; item { PulseCard("AI", "Provider, model, context and memory", "Planned") }; item { PulseCard("Workspace", "Indexing, recovery, snapshots and storage", "Active") }; item { PulseCard("Security", "Approvals, secrets and privacy controls", "Planned") }; item { PulseCard("Appearance", "Theme, density, motion and editor style", "Planned") } } }
}

@Composable
private fun StatusPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .70f)) { Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
private fun PulseCard(title: String, subtitle: String, action: String) {
    Card(RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton({}) { Text(action) } } }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) { Column { Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(4.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); Divider(Modifier.alpha(.2f)) } }

@Composable
private fun SectionLabel(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

private fun formatBytes(value: Long?): String = when { value == null || value < 0L -> "File"; value < 1024L -> "$value B"; value < 1024L * 1024L -> "${value / 1024L} KB"; else -> "${value / (1024L * 1024L)} MB" }
