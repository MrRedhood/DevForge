package com.mrredhood.devforge.core.preview

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    editor: EditorViewModel,
    workspace: WorkspaceViewModel,
    onClose: () -> Unit,
) {
    val activeTab = editor.activeTab ?: editor.tabs.lastOrNull()
    val fileName = activeTab?.name.orEmpty()
    val source = activeTab?.content.orEmpty()
    val hasPreviewTarget = activeTab != null
    val rendered = remember(fileName, source, hasPreviewTarget) {
        if (hasPreviewTarget) {
            PreviewDocumentRenderer.render(fileName, source)
        } else {
            ""
        }
    }
    var previewHtml by remember(rendered) { mutableStateOf(rendered) }
    var consoleLines by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(activeTab?.uri) {
        if (editor.activeTab == null && activeTab != null) {
            editor.select(activeTab.uri)
        }
    }

    LaunchedEffect(rendered) {
        delay(180)
        previewHtml = rendered
        consoleLines = emptyList()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Live preview", maxLines = 1)
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close live preview")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        previewHtml = PreviewDocumentRenderer.render(fileName, source)
                        consoleLines = emptyList()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh live preview")
                    }
                },
            )

            if (!hasPreviewTarget) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            "Open a file to preview",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Live Preview renders the currently selected editor file.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean = false
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                    consoleLines = (
                                        consoleLines +
                                            (consoleMessage.messageLevel().name + ": " +
                                                consoleMessage.message() +
                                                " @" + consoleMessage.lineNumber())
                                        ).takeLast(120)
                                    return true
                                }
                            }
                        }
                    },
                    update = { webView ->
                        val hash = previewHtml.hashCode()
                        if (webView.tag != hash) {
                            webView.tag = hash
                            webView.loadDataWithBaseURL(
                                "https://devforge.local/",
                                previewHtml,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )
            }

            if (consoleLines.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Preview console", style = MaterialTheme.typography.labelLarge)
                        consoleLines.takeLast(6).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private object PreviewDocumentRenderer {
    fun render(name: String, source: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> ensureHtml(source)
            "js", "mjs", "cjs" -> javaScriptPreview(source)
            "css", "scss", "sass", "less" -> cssPreview(source)
            "md", "markdown" -> markdownPreview(source)
            else -> sourcePreview(name, source)
        }

    private fun ensureHtml(source: String): String =
        if (source.contains("<html", ignoreCase = true)) {
            source
        } else {
            "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body>$source</body></html>"
        }

    private fun javaScriptPreview(source: String): String =
        "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>body{margin:0;padding:16px;background:#0b0d12;color:#e5e7eb;font-family:monospace}pre{white-space:pre-wrap;word-break:break-word}</style>" +
            "</head><body><h3 style='font-family:sans-serif'>JavaScript live preview</h3><pre id='out'></pre><script>" +
            "const out=document.getElementById('out');" +
            "const oldLog=console.log;console.log=(...a)=>{oldLog(...a);out.textContent+=a.map(String).join(' ')+'\\n';};" +
            "window.onerror=(m,s,l,c)=>{out.textContent+='ERROR: '+m+' @'+l+':'+c+'\\n';};" +
            escapeScript(source) +
            "</script></body></html>"

    private fun cssPreview(source: String): String =
        "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>" +
            source +
            "</style></head><body><main style='padding:20px;font-family:sans-serif'>" +
            "<h2>CSS live preview</h2><p>Styles from the current file are applied to this preview surface.</p><button>Sample button</button></main></body></html>"

    private fun markdownPreview(source: String): String =
        "<!doctype html><html><body style='font-family:sans-serif;padding:16px'>" +
            source.lineSequence().joinToString("<br>") { line ->
                when {
                    line.startsWith("# ") -> "<h2>${escapeHtml(line.removePrefix("# "))}</h2>"
                    line.startsWith("## ") -> "<h3>${escapeHtml(line.removePrefix("## "))}</h3>"
                    line.startsWith("- ") -> "• ${escapeHtml(line.removePrefix("- "))}"
                    else -> escapeHtml(line)
                }
            } +
            "</body></html>"

    private fun sourcePreview(name: String, source: String): String =
        "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>body{margin:0;padding:16px;background:#0b0d12;color:#e5e7eb;font-family:monospace}" +
            "h3{font-family:sans-serif}pre{white-space:pre-wrap;word-break:break-word}</style></head><body>" +
            "<h3>Live source preview · " + escapeHtml(name) + "</h3><pre>" + escapeHtml(source) + "</pre></body></html>"

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun escapeScript(value: String): String =
        value.replace("</script", "<\\/script", ignoreCase = true)
}
