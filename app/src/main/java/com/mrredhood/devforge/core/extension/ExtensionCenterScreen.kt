package com.mrredhood.devforge.core.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private enum class MarketplaceMode { DISCOVER, INSTALLED }

private val marketplaceCategories = listOf(
    "all" to "All",
    "extension" to "Extensions",
    "aiAgent" to "AI Agents",
    "aiTool" to "AI Tools",
    "workflow" to "Workflows",
    "automation" to "Automations",
    "theme" to "Themes",
    "iconPack" to "Icon packs",
    "language" to "Languages",
    "template" to "Templates",
    "toolPack" to "Tool packs",
    "project" to "Projects",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionCenterScreen(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val config = remember { DevForgeMarketplaceConfig(context) }
    val store = remember { ExtensionPackageStore(context) }
    val installer = remember { ExtensionPackageInstaller(context, store) }
    val service = remember(config) { ExtensionMarketplaceService(config) }

    var mode by rememberSaveable { mutableStateOf(MarketplaceMode.DISCOVER.name) }
    var category by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var online by remember { mutableStateOf<List<MarketplaceExtension>>(emptyList()) }
    var installed by remember { mutableStateOf(store.list()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var configureOpen by remember { mutableStateOf(false) }
    val installing = remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            service.searchAll(query.trim(), type = category).onSuccess {
                online = it
            }.onFailure {
                online = emptyList()
                error = it.message ?: "Unable to load DevForge Marketplace."
            }
            loading = false
        }
    }

    LaunchedEffect(query, category, config.getBaseUrl()) {
        delay(300)
        if (config.isConfigured()) refresh()
    }

    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { Text("Marketplace") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (mode == MarketplaceMode.DISCOVER.name && config.isConfigured()) {
                            IconButton(onClick = ::refresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh marketplace")
                            }
                        }
                        IconButton(onClick = { configureOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Marketplace settings")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == MarketplaceMode.DISCOVER.name,
                        onClick = { mode = MarketplaceMode.DISCOVER.name },
                        label = { Text("Discover") },
                    )
                    FilterChip(
                        selected = mode == MarketplaceMode.INSTALLED.name,
                        onClick = {
                            installed = store.list()
                            mode = MarketplaceMode.INSTALLED.name
                        },
                        label = { Text("Installed (" + installed.size + ")") },
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search Marketplace") },
                    placeholder = { Text("Extension, AI agent, workflow, theme, tool…") },
                )

                if (mode == MarketplaceMode.DISCOVER.name) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        marketplaceCategories.forEach { (value, label) ->
                            FilterChip(
                                selected = category == value,
                                onClick = { category = value },
                                label = { Text(label) },
                            )
                        }
                    }

                    if (!config.isConfigured()) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("DevForge Marketplace is not connected", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Connect the first-party Marketplace API to discover and install DevForge packages.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { configureOpen = true }) { Text("Connect Marketplace") }
                            }
                        }
                    }

                    status?.let { message ->
                        Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(12.dp)) }
                    }

                    error?.let { message ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Marketplace unavailable", style = MaterialTheme.typography.titleMedium)
                                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (config.isConfigured()) {
                                    OutlinedButton(onClick = ::refresh) { Text("Retry") }
                                }
                            }
                        }
                    }

                    if (loading) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(Modifier.height(22.dp))
                        }
                    }

                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(online, key = { it.id }) { packageInfo ->
                            MarketplacePackageCard(
                                packageInfo = packageInfo,
                                installed = installed.firstOrNull { it.manifest.id == packageInfo.id },
                                installing = packageInfo.id in installing.value,
                                onInstall = {
                                    installing.value = installing.value + packageInfo.id
                                    scope.launch {
                                        status = "Downloading and validating " + packageInfo.name + "…"
                                        installer.installRemote(packageInfo)
                                            .onSuccess {
                                                installed = store.list()
                                                status = it.message
                                            }
                                            .onFailure {
                                                status = it.message ?: "Installation failed."
                                            }
                                        installing.value = installing.value - packageInfo.id
                                    }
                                },
                            )
                        }
                        if (!loading && config.isConfigured() && online.isEmpty() && error == null) {
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("No matching packages", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Try another search or category.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    status?.let { message ->
                        Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(12.dp)) }
                    }

                    val filtered = installed.filter { packageInfo ->
                        query.isBlank() || listOf(
                            packageInfo.manifest.id,
                            packageInfo.manifest.name,
                            packageInfo.manifest.version,
                            packageInfo.manifest.kind.name,
                            packageInfo.manifest.source.name,
                        ).any { it.contains(query.trim(), ignoreCase = true) }
                    }

                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.manifest.id }) { installedPackage ->
                            InstalledPackageCard(
                                installed = installedPackage,
                                onEnabled = {
                                    store.setEnabled(installedPackage.manifest.id, it)
                                    installed = store.list()
                                },
                                onUninstall = {
                                    installer.uninstall(installedPackage.manifest.id)
                                    installed = store.list()
                                    status = installedPackage.manifest.name + " uninstalled."
                                },
                            )
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("No packages installed", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Discover DevForge packages from the Marketplace or install a trusted .devforge package through the package flow.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (configureOpen) {
        MarketplaceSettingsDialog(
            currentUrl = config.getBaseUrl(),
            onDismiss = { configureOpen = false },
            onSave = { value ->
                config.setBaseUrl(value).onSuccess {
                    configureOpen = false
                    error = null
                    status = if (it.isBlank()) "Marketplace server cleared." else "Marketplace server connected."
                    if (it.isNotBlank() && mode == MarketplaceMode.DISCOVER.name) refresh()
                }.onFailure {
                    error = it.message ?: "Invalid marketplace URL."
                }
            },
        )
    }
}

@Composable
private fun MarketplacePackageCard(
    packageInfo: MarketplaceExtension,
    installed: InstalledExtension?,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(packageInfo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        packageInfo.publisher + " • " + packageInfo.packageType + " • v" + packageInfo.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    installing -> CircularProgressIndicator(Modifier.height(22.dp))
                    installed?.manifest?.version == packageInfo.version -> Text("Installed", color = MaterialTheme.colorScheme.primary)
                    packageInfo.installable -> Button(onClick = onInstall) {
                        Text(if (installed == null) "Install" else "Update")
                    }
                    else -> Text("Unavailable", color = MaterialTheme.colorScheme.tertiary)
                }
            }

            if (packageInfo.description.isNotBlank()) {
                Text(packageInfo.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DevForge API " + packageInfo.apiVersion, style = MaterialTheme.typography.labelSmall)
                Text("Requires " + packageInfo.minimumDevForgeVersion + "+", style = MaterialTheme.typography.labelSmall)
                packageInfo.downloads?.let { Text(formatMarketplaceCount(it) + " downloads", style = MaterialTheme.typography.labelSmall) }
            }

            if (packageInfo.requestedPermissions.isNotEmpty()) {
                Text(
                    "Permissions: " + packageInfo.requestedPermissions.joinToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun InstalledPackageCard(
    installed: InstalledExtension,
    onEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(installed.manifest.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        installed.manifest.kind.name.lowercase().replace('_', ' ') +
                            " • " + installed.manifest.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = installed.enabled, onCheckedChange = onEnabled)
            }

            if (installed.manifest.description.isNotBlank()) {
                Text(installed.manifest.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUninstall) { Text("Uninstall") }
            }
        }
    }
}

@Composable
private fun MarketplaceSettingsDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(currentUrl) { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marketplace server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Use the HTTPS base URL of the first-party DevForge Marketplace API.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Marketplace API URL") },
                    placeholder = { Text("https://your-worker.workers.dev") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatMarketplaceCount(value: Long): String = when {
    value >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(java.util.Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}
