package com.mrredhood.devforge.core.preview

import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLivePreviewScreen(
    fileName: String,
    source: String,
    targetUri: Uri?,
    workspace: WorkspaceViewModel,
    loading: Boolean = false,
    loadError: String? = null,
    onChooseFile: (WorkspaceEntry) -> Unit,
    onClose: () -> Unit,
) {
    var chooseOpen by remember { mutableStateOf(fileName.isBlank()) }
    val rootUri = workspace.rootUri
    var chooserStack by remember(rootUri) {
        mutableStateOf(
            rootUri?.let { listOf(PreviewChooserLocation(it, workspace.workspace?.name ?: "Workspace")) }.orEmpty(),
        )
    }
    var chooserEntries by remember(rootUri) { mutableStateOf(emptyList<WorkspaceEntry>()) }
    var chooserQuery by remember(rootUri) { mutableStateOf("") }
    var chooserLoading by remember(rootUri) { mutableStateOf(false) }
    var chooserError by remember(rootUri) { mutableStateOf<String?>(null) }
    var workspacePath by remember(targetUri, workspace.workspace?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(targetUri, workspace.workspace?.id) {
        workspacePath = targetUri?.let { uri -> workspace.previewPathFor(uri).getOrNull() }
    }

    LaunchedEffect(rootUri, chooseOpen, chooserStack) {
        if (!chooseOpen) return@LaunchedEffect
        val location = chooserStack.lastOrNull()
        if (location == null) {
            chooserEntries = emptyList()
            chooserError = "Open a workspace first."
            chooserLoading = false
            return@LaunchedEffect
        }
        chooserLoading = true
        chooserError = null
        runCatching { workspace.listDirectory(location.uri) }
            .onSuccess { chooserEntries = it }
            .onFailure {
                chooserEntries = emptyList()
                chooserError = it.message ?: "Unable to load this folder."
            }
        chooserLoading = false
    }

    LaunchedEffect(chooseOpen, chooserStack) {
        if (chooseOpen) chooserQuery = ""
    }

    val hasTarget = fileName.isNotBlank()
    val rendered = remember(fileName, source) {
        if (!hasTarget) "" else WebPreviewRenderer.render(fileName, source)
    }
    var previewHtml by remember(rendered) { mutableStateOf(rendered) }
    var consoleLines by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(rendered) {
        delay(80)
        previewHtml = rendered
        consoleLines = emptyList()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Web Live Preview", maxLines = 1)
                        Text(
                            fileName.ifBlank { workspace.workspace?.name ?: "Workspace" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close web live preview")
                    }
                },
                actions = {
                    TextButton(onClick = { chooseOpen = true }) { Text("Choose") }
                    IconButton(onClick = {
                        previewHtml = WebPreviewRenderer.render(fileName, source)
                        consoleLines = emptyList()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh web live preview")
                    }
                },
            )

            if (!hasTarget) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        if (loading) {
                            Text("Loading web preview…", style = MaterialTheme.typography.titleMedium)
                            Text("Reading the selected workspace file.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (loadError != null) {
                            Text("Web preview could not load", style = MaterialTheme.typography.titleMedium)
                            Text(loadError, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { chooseOpen = true }) { Text("Choose another file") }
                        } else {
                            Text("Choose a web file to preview", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "HTML, CSS, JavaScript, Markdown and workspace-relative assets are served through Web Live Preview.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { chooseOpen = true }) { Text("Choose file") }
                        }
                    }
                }
            } else {
                AndroidView(
                    Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.loadsImagesAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    val uri = request.url
                                    if (!uri.host.equals(WEB_PREVIEW_HOST, true)) return null
                                    val path = Uri.decode(uri.path.orEmpty().trim('/'))
                                    if (path.isBlank()) return null
                                    val bytes = workspace.readPreviewResource(path).getOrNull() ?: return null
                                    return WebResourceResponse(
                                        mimeType(path),
                                        charset(path),
                                        ByteArrayInputStream(bytes),
                                    )
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    return !request.url.host.equals(WEB_PREVIEW_HOST, true)
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    consoleLines = (
                                        consoleLines +
                                            (message.messageLevel().name + ": " + message.message() + " @" + message.lineNumber())
                                        ).takeLast(120)
                                    return true
                                }
                            }
                        }
                    },
                    update = { webView ->
                        val tag = fileName + "|" + workspacePath.orEmpty() + "|" + previewHtml.hashCode()
                        if (webView.tag != tag && previewHtml.isNotBlank()) {
                            webView.tag = tag
                            webView.loadDataWithBaseURL(
                                baseUrlFor(workspacePath.orEmpty()),
                                previewHtml,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )
            }

            if (chooseOpen) {
                AlertDialog(
                    onDismissRequest = { chooseOpen = false },
                    title = {
                        Text(
                            "Choose web preview file · " +
                                (chooserStack.lastOrNull()?.name ?: workspace.workspace?.name ?: "Workspace"),
                        )
                    },
                    text = {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Browse the active workspace. Selecting a file loads it into Web Live Preview without opening an editor tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = chooserQuery,
                                onValueChange = { chooserQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Filter files") },
                                singleLine = true,
                            )
                            when {
                                chooserLoading -> Text("Loading workspace files…")
                                chooserError != null -> Text(chooserError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                else -> {
                                    val visible = chooserEntries.filter {
                                        chooserQuery.isBlank() || it.name.contains(chooserQuery, true)
                                    }
                                    if (visible.isEmpty()) {
                                        Text(
                                            if (chooserQuery.isBlank()) "No files are available in this folder."
                                            else "No matching files in this folder.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        LazyColumn(
                                            Modifier.fillMaxWidth().heightIn(max = 420.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            items(visible, key = { it.uri.toString() }) { entry ->
                                                TextButton(
                                                    onClick = {
                                                        if (entry.isDirectory) {
                                                            chooserStack = chooserStack + PreviewChooserLocation(entry.uri, entry.name)
                                                        } else {
                                                            chooseOpen = false
                                                            onChooseFile(entry)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Row(
                                                        Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Refresh,
                                                            contentDescription = null,
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(entry.name, Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (chooserStack.size > 1) chooserStack = chooserStack.dropLast(1) else chooseOpen = false
                        }) {
                            Text(if (chooserStack.size > 1) "Up" else "Close")
                        }
                    },
                    dismissButton = { TextButton(onClick = { chooseOpen = false }) { Text("Cancel") } },
                )
            }

            if (consoleLines.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Web preview console", style = MaterialTheme.typography.labelLarge)
                        consoleLines.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

data class PreviewChooserLocation(
    val uri: Uri,
    val name: String,
)

private object WebPreviewRenderer {
    fun render(name: String, source: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> ensureHtml(source)
            "js", "mjs", "cjs" -> javaScriptPreview(source)
            "css", "scss", "sass", "less" -> cssPreview(source)
            "md", "markdown" -> markdownPreview(source)
            else -> sourcePreview(name, source)
        }

    private fun ensureHtml(source: String): String =
        if (source.contains("<html", true)) source
        else "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body>$source</body></html>"

    private fun javaScriptPreview(source: String): String =
        "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>body{margin:0;padding:16px;background:#0b0d12;color:#e5e7eb;font-family:monospace}pre{white-space:pre-wrap;word-break:break-word}</style>" +
            "</head><body><h3 style='font-family:sans-serif'>JavaScript web preview</h3><pre id='out'></pre><script>" +
            "const out=document.getElementById('out');const oldLog=console.log;console.log=(...a)=>{oldLog(...a);out.textContent+=a.map(String).join(' ')+'\\n';};" +
            "window.onerror=(m,s,l,c)=>{out.textContent+='ERROR: '+m+' @'+l+':'+c+'\\n';};" +
            escapeScript(source) +
            "</script></body></html>"

    private fun cssPreview(source: String): String =
        "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>" +
            source +
            "</style></head><body><main style='padding:20px;font-family:sans-serif'><h2>CSS web preview</h2><p>Styles from the current file are applied to this surface.</p><button>Sample button</button></main></body></html>"

    private fun markdownPreview(source: String): String =
        "<!doctype html><html><body style='font-family:sans-serif;padding:16px'>" +
            source.lineSequence().joinToString("<br>") { line ->
                when {
                    line.startsWith("# ") -> "<h2>" + escapeHtml(line.removePrefix("# ")) + "</h2>"
                    line.startsWith("## ") -> "<h3>" + escapeHtml(line.removePrefix("## ")) + "</h3>"
                    else -> escapeHtml(line)
                }
            } +
            "</body></html>"

    private fun sourcePreview(name: String, source: String): String =
        "<!doctype html><html><body style='font-family:monospace;padding:16px;white-space:pre-wrap;word-break:break-word'>" +
            "<h3 style='font-family:sans-serif'>" + escapeHtml(name) + "</h3><pre>" + escapeHtml(source) + "</pre></body></html>"

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun escapeScript(value: String): String =
        value.replace("</script>", "<\\/script>", ignoreCase = true)
}

private fun baseUrlFor(path: String): String {
    val directory = path.trim('/').substringBeforeLast('/', "")
    if (directory.isBlank()) return "https://" + WEB_PREVIEW_HOST + "/"
    return "https://" + WEB_PREVIEW_HOST + "/" +
        directory.split('/').filter { it.isNotBlank() }.joinToString("/") { Uri.encode(it) } + "/"
}

private fun charset(path: String): String? =
    if (mimeType(path).startsWith("text/") || path.substringAfterLast('.', "").lowercase() in setOf("json","xml","svg")) "UTF-8" else null

private fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html"
    "css", "scss", "sass", "less" -> "text/css"
    "js", "mjs", "cjs" -> "text/javascript"
    "json", "map" -> "application/json"
    "xml" -> "application/xml"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "ico" -> "image/x-icon"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    else -> "text/plain"
}

private const val WEB_PREVIEW_HOST = "devforge.local"
