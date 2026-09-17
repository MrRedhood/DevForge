package com.mrredhood.devforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.build.BuildCenterScreen
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.git.GitDashboardScreen
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
    val destination = DevForgeDestination.valueOf(destinationName)
    val expanded = windowSize.widthSizeClass != WindowWidthSizeClass.Compact
    val workspace: WorkspaceViewModel = viewModel()
    val editor: EditorViewModel = viewModel()
    val editing = editor.activeTab != null || editor.isLoading

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { DevForgeTopBar(workspace.workspace?.name ?: "No workspace", editing) },
        bottomBar = { if (!expanded && !editing) NavigationBottom(destination) { destinationName = it.name } },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded && !editing) NavigationSide(destination) { destinationName = it.name }
            if (editing) EditorScreen(editor) else DestinationScreen(destination, workspace, editor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevForgeTopBar(workspaceName: String, editing: Boolean) {
    TopAppBar(
        title = { Column { Text("DevForge", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp); Text(if (editing) "Editor / $workspaceName" else workspaceName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        navigationIcon = { Surface(modifier = Modifier.padding(start = 10.dp).size(38.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Code, "DevForge", Modifier.padding(9.dp)) } },
        actions = { IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search") }; IconButton(onClick = {}) { Icon(Icons.Default.Security, "Security") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun NavigationBottom(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    NavigationBar { DevForgeDestination.entries.forEach { item -> NavigationBarItem(selected = current == item, onClick = { onSelect(item) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationSide(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight().width(88.dp)) { Spacer(Modifier.height(18.dp)); DevForgeDestination.entries.forEach { item -> NavigationRailItem(selected = current == item, onClick = { onSelect(item) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }) } }
}

@Composable
private fun DestinationScreen(destination: DevForgeDestination, workspace: WorkspaceViewModel, editor: EditorViewModel) {
    when (destination) {
        DevForgeDestination.Chat -> ChatScreen()
        DevForgeDestination.Files -> FilesScreen(workspace, editor)
        DevForgeDestination.Git -> GitDashboardScreen()
        DevForgeDestination.Build -> BuildCenterScreen()
        DevForgeDestination.Settings -> SettingsScreen()
    }
}

@Composable
private fun ChatScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { Column(Modifier.padding(24.dp)) { Text("Build. Review. Ship.", fontSize = 32.sp, fontWeight = FontWeight.Black); Text("A mobile engineering cockpit where AI proposes changes and every action leaves a trail.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusPill("Online", Icons.Default.Wifi); StatusPill("Protected", Icons.Default.Security) } } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = true, onClick = {}, label = { Text("@workspace") }); FilterChip(selected = true, onClick = {}, label = { Text("@git-diff") }) } }
        item { PulseCard("Git", "Repository-aware workflow is next", "Review") }
        item { PulseCard("Build", "Cloud build center ready", "Open") }
        item { PulseCard("Agent", "No pending approvals", "Activity") }
    }
}

@Composable
private fun FilesScreen(workspace: WorkspaceViewModel, editor: EditorViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(workspace::openWorkspace) }
    BackHandler(enabled = workspace.breadcrumbs.size > 1) { workspace.goUp() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Files", fontSize = 30.sp, fontWeight = FontWeight.Black); Text(if (workspace.workspace == null) "Choose a workspace to begin" else "Workspace browser", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button(onClick = { picker.launch(null) }) { Icon(Icons.Default.Folder, null); Spacer(Modifier.width(6.dp)); Text(if (workspace.workspace == null) "Choose" else "Change") }; if (workspace.workspace != null) IconButton(onClick = workspace::refresh) { Icon(Icons.Default.Refresh, "Refresh") } } }
        if (workspace.workspace == null) item { InfoCard("Bring your code into DevForge", "DevForge uses an Android document-tree permission for the folder you explicitly choose.") }
        else {
            item { Breadcrumbs(workspace) }
            if (workspace.isLoading) item { LoadingCard("Reading folder…") }
            else if (workspace.entries.isEmpty()) item { InfoCard("Nothing in this folder", "Create a file here and refresh.") }
            else items(workspace.entries, key = { it.uri.toString() }) { entry -> FileRow(entry) { if (entry.isDirectory) workspace.openDirectory(entry) else editor.open(entry) } }
        }
    }
}

@Composable
private fun Breadcrumbs(workspace: WorkspaceViewModel) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { workspace.breadcrumbs.forEachIndexed { index, crumb -> if (index > 0) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp).alpha(.45f)); FilterChip(selected = index == workspace.breadcrumbs.lastIndex, onClick = { workspace.goToBreadcrumb(index) }, label = { Text(crumb.name, maxLines = 1) }) } } }

@Composable
private fun FileRow(entry: WorkspaceEntry, onOpen: () -> Unit) { Card(onClick = onOpen, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(entry.name, fontWeight = FontWeight.SemiBold); Text(if (entry.isDirectory) "Folder" else formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null, Modifier.alpha(.45f)) } } }

@Composable
private fun EditorScreen(editor: EditorViewModel) {
    val active = editor.activeTab
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (editor.isLoading) LoadingCard("Opening file…")
        active?.let { tab ->
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tab.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); if (tab.isDirty) Text("Unsaved", color = MaterialTheme.colorScheme.tertiary); IconButton(onClick = editor::saveActive, enabled = tab.isDirty) { Icon(Icons.Default.Save, "Save") } }
            Divider(Modifier.padding(vertical = 8.dp))
            BasicTextField(value = tab.content, onValueChange = editor::updateContent, modifier = Modifier.fillMaxSize(), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onBackground))
        }
    }
}

@Composable
private fun SettingsScreen() { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Black) }; item { PulseCard("AI", "Provider, model, context and memory", "Planned") }; item { PulseCard("Workspace", "Indexing, recovery, snapshots and storage", "Active") }; item { PulseCard("Security", "Approvals, secrets and privacy controls", "Planned") }; item { PulseCard("Appearance", "Theme, density, motion and editor style", "Planned") } } }

@Composable
private fun StatusPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface) { Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium) } } }

@Composable
private fun PulseCard(title: String, subtitle: String, action: String) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = {}) { Text(action) } } } }

@Composable
private fun InfoCard(title: String, message: String) { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { Column(Modifier.fillMaxWidth().padding(22.dp)) { Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text(message, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable
private fun LoadingCard(message: String) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)); Text(message) } } }

private fun formatBytes(value: Long?): String = when { value == null || value < 0L -> "File"; value < 1024L -> "$value B"; value < 1024L * 1024L -> "${value / 1024L} KB"; else -> "${value / (1024L * 1024L)} MB" }
