package com.mrredhood.devforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrredhood.devforge.core.model.DevForgeDestination
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
    var destination by rememberSaveable { mutableStateOf(DevForgeDestination.Chat.name) }
    val current = DevForgeDestination.valueOf(destination)
    val expanded = windowSize.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ForgeTopBar(current) },
        bottomBar = {
            if (!expanded) {
                ForgeBottomBar(current) { destination = it.name }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded) ForgeRail(current) { destination = it.name }
            ForgeContent(current)
        }
    }
}

@Composable
private fun ForgeTopBar(destination: DevForgeDestination) {
    TopAppBar(
        title = {
            Column {
                Text("DevForge", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(
                    "workspace / android-nexus",
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
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Code, contentDescription = "DevForge")
                }
            }
        },
        actions = {
            IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search") }
            BadgedBox(badge = { Badge { Text("1") } }) {
                IconButton(onClick = {}) { Icon(Icons.Default.NotificationsNone, "Activity") }
            }
            IconButton(onClick = {}) { Icon(Icons.Default.Shield, "Permissions") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
    )
}

@Composable
private fun ForgeBottomBar(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
        DevForgeDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = current == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun ForgeRail(current: DevForgeDestination, onSelect: (DevForgeDestination) -> Unit) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().width(88.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
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
private fun ForgeContent(destination: DevForgeDestination) {
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        when (destination) {
            DevForgeDestination.Chat -> ChatScreen()
            DevForgeDestination.Files -> FilesScreen()
            DevForgeDestination.Git -> GitScreen()
            DevForgeDestination.Build -> BuildScreen()
            DevForgeDestination.Settings -> SettingsScreen()
        }
    }
}

@Composable
private fun ScreenFrame(content: @Composable (PaddingValues) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun ChatScreen() {
    ScreenFrame { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeroCard() }
            item { ContextStrip() }
            item { SectionLabel("Workspace pulse") }
            item { PulseGrid() }
            item { SectionLabel("Ready when you are") }
            item { ComposerPreview() }
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .30f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = .10f),
                        MaterialTheme.colorScheme.surfaceContainer,
                    )
                )
            )
        ) {
            Column(Modifier.padding(24.dp)) {
                AssistChip(onClick = {}, label = { Text("AI control center") }, leadingIcon = {
                    Icon(Icons.Default.AutoAwesome, null)
                })
                Spacer(Modifier.height(16.dp))
                Text("Build. Review. Ship.", fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(
                    "A mobile engineering cockpit where AI proposes changes, you stay in control, and every action leaves a trail.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("Online", Icons.Default.Wifi)
                    StatusPill("Protected", Icons.Default.Shield)
                    StatusPill("Synced", Icons.Default.CloudDone)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .70f)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ContextStrip() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = true, onClick = {}, label = { Text("@workspace") })
        FilterChip(selected = true, onClick = {}, label = { Text("@git-diff") })
        FilterChip(selected = false, onClick = {}, label = { Text("+ Add context") })
    }
}

@Composable
private fun PulseGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PulseCard("Git", "3 files changed", "Review changes")
        PulseCard("Build", "Last run passed", "Open build")
        PulseCard("Agent", "No pending approvals", "View activity")
    }
}

@Composable
private fun PulseCard(title: String, subtitle: String, action: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {}) { Text(action) }
        }
    }
}

@Composable
private fun ComposerPreview() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("What should we work on?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text("Explain code") })
                Spacer(Modifier.weight(1f))
                Button(onClick = {}) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Ask") }
            }
        }
    }
}

@Composable
private fun FilesScreen() {
    ScreenFrame { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            ScreenTitle("Files", "Your workspace, indexed and ready")
            Spacer(Modifier.height(18.dp))
            FilesRow("app/", "module", true)
            FilesRow("core/", "architecture", true)
            FilesRow("build.gradle.kts", "24 KB", false)
            FilesRow("README.md", "6 KB", false)
        }
    }
}

@Composable
private fun FilesRow(name: String, meta: String, folder: Boolean) {
    Card(
        modifier = Modifier.padding(bottom = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (folder) Icons.Default.Description else Icons.Default.Code, null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, Modifier.alpha(.5f))
        }
    }
}

@Composable
private fun GitScreen() {
    ScreenFrame { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            ScreenTitle("Git", "main • 3 changes • clean remote")
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("main", Icons.Default.Source)
                StatusPill("3 changed", Icons.Default.Code)
            }
            Spacer(Modifier.height(18.dp))
            SectionLabel("Changes")
            listOf("MainActivity.kt", "Theme.kt", "ActionPolicy.kt").forEach { path ->
                PulseCard(path, "Modified", "View diff")
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BuildScreen() {
    ScreenFrame { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ScreenTitle("Build", "Cloud builds without bundling the Android toolchain") }
            item { BuildHero() }
            item { SectionLabel("Recent runs") }
            items(listOf("Debug APK • passed", "Unit tests • passed", "Release AAB • waiting")) { run ->
                PulseCard("GitHub Actions", run, "Open run")
            }
        }
    }
}

@Composable
private fun BuildHero() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, null, Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Build Center", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("Dispatch a workflow, monitor jobs, inspect logs, and verify artifacts from the same control surface.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {}) { Text("Dispatch build") }
        }
    }
}

@Composable
private fun SettingsScreen() {
    ScreenFrame { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ScreenTitle("Settings", "Control how DevForge behaves") }
            item { SettingGroup("AI", "Provider, model, context budget, memory") }
            item { SettingGroup("Permissions", "Never • Some • Autonomous") }
            item { SettingGroup("Workspace", "Indexing, storage, snapshots, import/export") }
            item { SettingGroup("Git & GitHub", "Identity, repositories, Actions permissions") }
            item { SettingGroup("Security & Privacy", "Keystore, approvals, redaction, telemetry") }
            item { SettingGroup("Appearance", "Theme, density, motion, editor style") }
        }
    }
}

@Composable
private fun SettingGroup(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, Modifier.alpha(.55f))
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Divider(modifier = Modifier.alpha(.2f))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}
