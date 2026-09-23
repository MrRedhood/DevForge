package com.mrredhood.devforge.core.extension

import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ExtensionCenterMode { DISCOVER, INSTALLED }
private enum class ExtensionCatalogSource { ALL, ACODE, VSCODE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionCenterScreen(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { ExtensionPackageStore(context) }
    val installer = remember { ExtensionPackageInstaller(context, store) }
    val catalogService = remember { ExtensionMarketplaceService() }
    val iconStore = remember { ExtensionIconThemeStore(context) }

    var installed by remember { mutableStateOf(store.list()) }
    var online by remember { mutableStateOf<List<MarketplaceExtension>>(emptyList()) }
    var mode by rememberSaveable { mutableStateOf(ExtensionCenterMode.DISCOVER.name) }
    var source by rememberSaveable { mutableStateOf(ExtensionCatalogSource.ALL.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var loadingOnline by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var activeRuntime by remember { mutableStateOf<InstalledExtension?>(null) }
    val scope = rememberCoroutineScope()
    val commands = remember { mutableStateListOf<RuntimeCommand>() }
    val installingIds = remember { mutableStateListOf<String>() }
    val runtime = remember {
        ExtensionRuntimeHost(
            onCommand = { command ->
                if (!commands.any { it.extensionId == command.extensionId && it.name == command.name }) {
                    commands += command
                }
            },
            onStatus = { status = it },
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Analyzing extension package…"
                installer.install(uri).onSuccess {
                    installed = store.list()
                    status = it.message
                    mode = ExtensionCenterMode.INSTALLED.name
                }.onFailure {
                    status = it.message ?: "Extension installation failed."
                }
            }
        }
    }

    fun refreshCatalog() {
        scope.launch {
            loadingOnline = true
            catalogError = null
            catalogService.searchAll(searchQuery.trim()).onSuccess {
                online = filterMarketplace(it, source)
            }.onFailure {
                catalogError = it.message ?: "Unable to load live extension catalogs."
                online = emptyList()
            }
            loadingOnline = false
        }
    }

    LaunchedEffect(searchQuery) {
        delay(350)
        refreshCatalog()
    }

    LaunchedEffect(source) {
        online = filterMarketplace(online, source)
    }

    DisposableEffect(Unit) {
        onDispose { runtime.dispose() }
    }

    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Extensions") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                            Text("Install")
                        }
                        IconButton(onClick = ::refreshCatalog) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh extension catalog")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ExtensionCenterMode.DISCOVER.name,
                        onClick = { mode = ExtensionCenterMode.DISCOVER.name },
                        label = { Text("Discover") },
                    )
                    FilterChip(
                        selected = mode == ExtensionCenterMode.INSTALLED.name,
                        onClick = {
                            installed = store.list()
                            mode = ExtensionCenterMode.INSTALLED.name
                        },
                        label = { Text("Installed (" + installed.size + ")") },
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search extensions") },
                    placeholder = { Text("Name, publisher, language, or feature") },
                )

                if (mode == ExtensionCenterMode.DISCOVER.name) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = source == ExtensionCatalogSource.ALL.name,
                            onClick = { source = ExtensionCatalogSource.ALL.name },
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = source == ExtensionCatalogSource.ACODE.name,
                            onClick = { source = ExtensionCatalogSource.ACODE.name },
                            label = { Text("Acode") },
                        )
                        FilterChip(
                            selected = source == ExtensionCatalogSource.VSCODE.name,
                            onClick = { source = ExtensionCatalogSource.VSCODE.name },
                            label = { Text("VS Code") },
                        )
                    }

                    Text(
                        "Live catalogs are queried from the official Acode plugin registry and the VS Code Marketplace. DevForge still validates every package before installation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    status?.let {
                        Card(Modifier.fillMaxWidth()) {
                            Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    catalogError?.let {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Live catalog unavailable", style = MaterialTheme.typography.titleMedium)
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = ::refreshCatalog) { Text("Retry") }
                            }
                        }
                    }

                    if (loadingOnline) {
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
                        items(online, key = { it.source.name + ":" + it.id }) { extension ->
                            MarketplaceExtensionCard(
                                extension = extension,
                                installed = installed.any { it.manifest.id == extension.id },
                                installing = extension.id in installingIds,
                                onInstall = {
                                    if (extension.id !in installingIds) {
                                        installingIds += extension.id
                                        scope.launch {
                                            status = "Downloading and validating " + extension.name + "…"
                                            installer.installRemote(extension).onSuccess {
                                                installed = store.list()
                                                status = it.message
                                            }.onFailure {
                                                status = it.message ?: "Extension installation failed."
                                            }
                                            installingIds.remove(extension.id)
                                        }
                                    }
                                },
                            )
                        }
                        if (!loadingOnline && online.isEmpty() && catalogError == null) {
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text("No matching extensions", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Try another search or switch between Acode and VS Code.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    status?.let {
                        Card(Modifier.fillMaxWidth()) {
                            Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
                        Text("Install ZIP / VSIX")
                    }

                    val filteredInstalled = installed.filter { extension ->
                        val query = searchQuery.trim()
                        query.isBlank() || listOf(
                            extension.manifest.name,
                            extension.manifest.id,
                            extension.manifest.source.name,
                            extension.manifest.kind.name,
                            extension.manifest.version,
                            extension.manifest.nativeLanguages.joinToString(" "),
                            extension.manifest.contributions.languages.joinToString(" ") { it.label + " " + it.id },
                        ).any { it.contains(query, ignoreCase = true) }
                    }

                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (filteredInstalled.isEmpty()) {
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text("No extensions installed", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Choose an extension from Discover, or install an Acode ZIP / VSIX package directly.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        items(filteredInstalled, key = { it.manifest.id }) { extension ->
                            ExtensionCard(
                                extension = extension,
                                isActiveIconTheme = store.activeIconThemeId() == extension.manifest.id,
                                commands = commands.filter { it.extensionId == extension.manifest.id },
                                onEnabled = {
                                    store.setEnabled(extension.manifest.id, it)
                                    installed = store.list()
                                    if (!it && activeRuntime?.manifest?.id == extension.manifest.id) {
                                        runtime.dispose()
                                        activeRuntime = null
                                    }
                                },
                                onActivate = {
                                    activeRuntime = extension
                                    commands.removeAll { it.extensionId == extension.manifest.id }
                                    runtime.activate(extension)
                                },
                                onRunCommand = { runtime.runCommand(extension.manifest.id, it) },
                                onUseIcons = {
                                    store.setActiveIconTheme(extension.manifest.id)
                                    iconStore.index(extension)
                                    status = "Active icon theme: " + extension.manifest.name
                                },
                                onUninstall = {
                                    installer.uninstall(extension.manifest.id)
                                    installed = store.list()
                                    commands.removeAll { it.extensionId == extension.manifest.id }
                                    if (activeRuntime?.manifest?.id == extension.manifest.id) {
                                        runtime.dispose()
                                        activeRuntime = null
                                    }
                                    status = extension.manifest.name + " uninstalled."
                                },
                            )
                        }
                    }
                }

                AndroidView(
                    factory = { viewContext -> WebView(viewContext).also(runtime::attach) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}

@Composable
private fun MarketplaceExtensionCard(
    extension: MarketplaceExtension,
    installed: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(extension.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        extension.publisher + " • " + extension.source.name + " • " + extension.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    installed -> Text("Installed", color = MaterialTheme.colorScheme.primary)
                    installing -> CircularProgressIndicator(Modifier.height(22.dp))
                    !extension.installable -> Text("Paid", color = MaterialTheme.colorScheme.tertiary)
                    else -> Button(onClick = onInstall) { Text("Install") }
                }
            }
            if (extension.description.isNotBlank()) {
                Text(
                    extension.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                extension.downloads?.let {
                    Text(formatCompactCount(it) + " downloads", style = MaterialTheme.typography.labelSmall)
                }
                extension.rating?.let {
                    Text(
                        "★ " + String.format(java.util.Locale.US, "%.1f", it),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                extension.priceText?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    extension: InstalledExtension,
    isActiveIconTheme: Boolean,
    commands: List<RuntimeCommand>,
    onEnabled: (Boolean) -> Unit,
    onActivate: () -> Unit,
    onRunCommand: (String) -> Unit,
    onUseIcons: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(extension.manifest.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        extension.manifest.source.name + " • " + extension.manifest.version,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Switch(checked = extension.enabled, onCheckedChange = onEnabled)
            }
            Text(
                compatibilityText(extension.manifest),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (extension.manifest.sourcePageUrl != null) {
                Text(
                    "Live catalog source linked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (extension.manifest.nativeLanguages.isNotEmpty()) {
                Text(
                    "Native language support already present: " + extension.manifest.nativeLanguages.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (extension.manifest.contributions.languages.isNotEmpty()) {
                Text(
                    "Languages: " + extension.manifest.contributions.languages.joinToString { it.label },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (extension.manifest.contributions.iconThemes.isNotEmpty() || extension.manifest.kind == ExtensionPackageKind.ICON_THEME) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onUseIcons) {
                        Text(if (isActiveIconTheme) "Icon theme active" else "Use icon theme")
                    }
                }
            }
            if (
                extension.manifest.compatibility == ExtensionCompatibility.ACODE_RUNTIME_SUPPORTED ||
                extension.manifest.compatibility == ExtensionCompatibility.VSCODE_WEB_RUNTIME_SUPPORTED
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onActivate, enabled = extension.enabled) { Text("Activate / verify") }
                }
                commands.forEach { command ->
                    OutlinedButton(onClick = { onRunCommand(command.name) }) { Text("Run " + command.name) }
                }
            }
            HorizontalDivider()
            TextButton(onClick = onUninstall) { Text("Uninstall") }
        }
    }
}

private fun compatibilityText(manifest: ExtensionManifest): String = when (manifest.compatibility) {
    ExtensionCompatibility.NATIVE_LANGUAGE ->
        "Supported natively by DevForge. Installation is retained only for compatible declarative extras."
    ExtensionCompatibility.DECLARATIVE_SUPPORTED ->
        "Supported declarative package. Its language/icon/theme/grammar contributions are applied by DevForge."
    ExtensionCompatibility.ACODE_RUNTIME_SUPPORTED ->
        "Acode JavaScript runtime is available for the package's verified API subset."
    ExtensionCompatibility.VSCODE_WEB_RUNTIME_SUPPORTED ->
        "VS Code Web runtime bridge is available for this bundled browser extension."
    ExtensionCompatibility.UNSUPPORTED ->
        "Not installed because the required runtime cannot be safely supported."
}

private fun filterMarketplace(
    values: List<MarketplaceExtension>,
    sourceName: String,
): List<MarketplaceExtension> = when (
    runCatching { ExtensionCatalogSource.valueOf(sourceName) }.getOrDefault(ExtensionCatalogSource.ALL)
) {
    ExtensionCatalogSource.ALL -> values
    ExtensionCatalogSource.ACODE -> values.filter { it.source == ExtensionSource.ACODE }
    ExtensionCatalogSource.VSCODE -> values.filter { it.source == ExtensionSource.VSCODE }
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(java.util.Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
