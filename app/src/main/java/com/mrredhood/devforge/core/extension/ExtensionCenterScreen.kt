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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionCenterScreen(onClose: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ExtensionPackageStore(context) }
    val installer = remember { ExtensionPackageInstaller(context, store) }
    val iconStore = remember { ExtensionIconThemeStore(context) }
    var extensions by remember { mutableStateOf(store.list()) }
    var status by remember { mutableStateOf<String?>(null) }
    var activeRuntime by remember { mutableStateOf<InstalledExtension?>(null) }
    val scope = rememberCoroutineScope()
    val commands = remember { mutableStateListOf<RuntimeCommand>() }
    val runtime = remember {
        ExtensionRuntimeHost(
            onCommand = { command ->
                if (!commands.any { it.extensionId == command.extensionId && it.name == command.name }) commands += command
            },
            onStatus = { status = it },
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val result = installer.install(uri)
                result.onSuccess {
                    extensions = store.list()
                    status = it.message
                }.onFailure {
                    status = it.message ?: "Extension installation failed."
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { runtime.dispose() }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.material3.TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
            )
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Text(
                "Install real Acode plugins or VS Code packages. DevForge analyzes the package first and refuses packages whose required runtime cannot be safely supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { launcher.launch(arrayOf("*/*")) }) { Text("Install ZIP / VSIX") }

            status?.let {
                Card(Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).also(runtime::attach)
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (extensions.isEmpty()) {
                    item { Text("No compatible extensions installed yet.") }
                }
                items(extensions, key = { it.manifest.id }) { extension ->
                    ExtensionCard(
                        extension = extension,
                        isActiveIconTheme = store.activeIconThemeId() == extension.manifest.id,
                        commands = commands.filter { it.extensionId == extension.manifest.id },
                        onEnabled = {
                            store.setEnabled(extension.manifest.id, it)
                            extensions = store.list()
                            if (!it && activeRuntime?.manifest?.id == extension.manifest.id) runtime.dispose()
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
                            extensions = store.list()
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
            if (extension.manifest.compatibility == ExtensionCompatibility.ACODE_RUNTIME_SUPPORTED ||
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
        "Supported natively by DevForge. Installation is retained only for any compatible declarative extras."
    ExtensionCompatibility.DECLARATIVE_SUPPORTED ->
        "Supported declarative package. Its language/icon/theme/grammar contributions are applied by DevForge."
    ExtensionCompatibility.ACODE_RUNTIME_SUPPORTED ->
        "Acode JavaScript runtime is available for the package's verified API subset."
    ExtensionCompatibility.VSCODE_WEB_RUNTIME_SUPPORTED ->
        "VS Code Web runtime bridge is available for this bundled browser extension."
    ExtensionCompatibility.UNSUPPORTED ->
        "Not installed because the required runtime cannot be safely supported."
}
