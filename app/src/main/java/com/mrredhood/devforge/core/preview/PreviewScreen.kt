package com.mrredhood.devforge.core.preview

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mrredhood.devforge.core.build.BuildConfiguration
import com.mrredhood.devforge.core.build.BuildState
import com.mrredhood.devforge.core.build.BuildTarget
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.github.GitHubActionsGateway
import com.mrredhood.devforge.core.github.GitHubArtifact
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

enum class PreviewMode {
    LIVE,
    APK,
    WEB,
}

@Composable
fun PreviewScreen(
    mode: PreviewMode,
    editor: EditorViewModel,
    workspace: WorkspaceViewModel,
    build: BuildViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activeTab = editor.activeTab
    val source = activeTab?.content.orEmpty()
    val fileName = activeTab?.name.orEmpty()
    val rendered = remember(source, fileName) {
        PreviewDocumentRenderer.render(fileName, source)
    }
    var debouncedHtml by remember(rendered) { mutableStateOf(rendered) }
    var previewConsole by remember { mutableStateOf(emptyList<String>()) }
    var apkError by remember { mutableStateOf<String?>(null) }
    var preparedApk by remember { mutableStateOf<PreviewApk?>(null) }
    var pendingPackage by remember { mutableStateOf<String?>(null) }
    var launchedPackage by remember { mutableStateOf<String?>(null) }
    var handledRunId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(rendered, mode) {
        delay(220)
        debouncedHtml = rendered
    }

    val installerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingPackage?.let { launchedPackage = it }
    }

    LaunchedEffect(mode, build.state, build.artifacts, build.runSnapshot?.id) {
        if (mode != PreviewMode.APK) return@LaunchedEffect
        val succeeded = build.state as? BuildState.Succeeded ?: return@LaunchedEffect
        if (handledRunId == succeeded.runId) return@LaunchedEffect
        handledRunId = succeeded.runId
        apkError = null
        val artifact = selectApkArtifact(build.artifacts, build.configuration.artifactName)
        if (artifact == null) {
            apkError = "The APK build completed, but no installable APK artifact was returned."
            return@LaunchedEffect
        }
        val result = runCatching {
            PreviewApkInstaller.downloadApk(context, build.configuration, artifact)
        }
        result.onSuccess { apk ->
            preparedApk = apk
            pendingPackage = apk.packageName
            installerLauncher.launch(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apk.uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            apkError = it.message ?: "Unable to prepare the preview APK."
        }
    }

    LaunchedEffect(launchedPackage) {
        val packageName = launchedPackage ?: return@LaunchedEffect
        repeat(20) {
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return@LaunchedEffect
            }
            delay(750)
        }
    }

    LaunchedEffect(launchedPackage) {
        val packageName = launchedPackage ?: return@LaunchedEffect
        while (isActive) {
            previewConsole = withContext(Dispatchers.IO) {
                PreviewRuntimeLogReader.read(context, packageName)
            }
            delay(2_000)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (mode) {
                                PreviewMode.LIVE -> "Live preview"
                                PreviewMode.APK -> "APK preview"
                                PreviewMode.WEB -> "Web view preview"
                            },
                        )
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close preview")
                    }
                },
                actions = {
                    if (mode != PreviewMode.APK) {
                        IconButton(onClick = {
                            debouncedHtml = PreviewDocumentRenderer.render(fileName, source)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh preview")
                        }
                    }
                },
            )
            if (mode == PreviewMode.APK) {
                ApkPreviewContent(
                    build = build,
                    preparedApk = preparedApk,
                    error = apkError,
                    runtimeLogs = previewConsole,
                    onBuild = {
                        apkError = null
                        preparedApk = null
                        handledRunId = null
                        build.startBuild(BuildTarget.DebugApk)
                    },
                    onInstall = {
                        preparedApk?.let { apk ->
                            pendingPackage = apk.packageName
                            installerLauncher.launch(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(apk.uri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    },
                    onLaunch = {
                        preparedApk?.packageName?.let { launchedPackage = it }
                    },
                )
            } else {
                PreviewWebSurface(
                    html = debouncedHtml,
                    mode = mode,
                    runtimeLogs = previewConsole,
                    onRuntimeMessage = { previewConsole = (previewConsole + it).takeLast(120) },
                )
            }
        }
    }
}

@Composable
private fun PreviewWebSurface(
    html: String,
    mode: PreviewMode,
    runtimeLogs: List<String>,
    onRuntimeMessage: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = mode == PreviewMode.WEB
                    settings.allowContentAccess = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = false
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            onRuntimeMessage(
                                consoleMessage.messageLevel.name + ": " +
                                    consoleMessage.message() +
                                    " @" + consoleMessage.lineNumber(),
                            )
                            return true
                        }
                    }
                    loadDataWithBaseURL(
                        "https://devforge.local/",
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            update = { view ->
                val hash = html.hashCode()
                if (view.tag != hash) {
                    view.tag = hash
                    view.loadDataWithBaseURL(
                        "https://devforge.local/",
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
        )
        if (runtimeLogs.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("Preview console", style = MaterialTheme.typography.labelLarge)
                    runtimeLogs.takeLast(6).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkPreviewContent(
    build: BuildViewModel,
    preparedApk: PreviewApk?,
    error: String?,
    runtimeLogs: List<String>,
    onBuild: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Build APK preview", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Build the project's debug APK, install it with Android's package installer, launch it, and keep runtime/build diagnostics here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onBuild,
                        enabled = build.state !is BuildState.Dispatching &&
                            build.state !is BuildState.Running &&
                            build.state !is BuildState.AwaitingApproval &&
                            build.state !is BuildState.Cancelling,
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Build & preview")
                    }
                    if (build.state is BuildState.Dispatching || build.state is BuildState.Running) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            build.monitoringMessage?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(it, Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }

        if (build.state is BuildState.Failed) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Text((build.state as BuildState.Failed).message, Modifier.weight(1f))
                    }
                }
            }
        }

        error?.let {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }

        item { Text("Build status: " + buildStatusLabel(build.state), style = MaterialTheme.typography.titleSmall) }

        if (build.logs.isNotEmpty()) {
            item { Text("Build logs", style = MaterialTheme.typography.titleMedium) }
            items(build.logs, key = { it.jobId }) { job ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(job.jobName + " · " + job.status + "/" + (job.conclusion ?: "running"))
                        Text(job.text.takeLast(8_000), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        preparedApk?.let { apk ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("APK ready", style = MaterialTheme.typography.titleMedium)
                        Text(apk.packageName)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onInstall) { Text("Install again") }
                            Button(onClick = onLaunch) { Text("Launch app") }
                        }
                    }
                }
            }
        }

        if (runtimeLogs.isNotEmpty()) {
            item { Text("Runtime diagnostics", style = MaterialTheme.typography.titleMedium) }
            items(runtimeLogs) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun buildStatusLabel(state: BuildState): String = when (state) {
    BuildState.Idle -> "idle"
    is BuildState.Ready -> "ready"
    is BuildState.AwaitingApproval -> "waiting for approval"
    is BuildState.Dispatching -> "dispatching"
    is BuildState.Cancelling -> "cancelling"
    is BuildState.Running -> "running"
    is BuildState.Succeeded -> "succeeded"
    is BuildState.Failed -> "failed: " + state.message
    is BuildState.Cancelled -> "cancelled"
}

private fun selectApkArtifact(
    artifacts: List<GitHubArtifact>,
    preferred: String,
): GitHubArtifact? =
    artifacts.firstOrNull { !it.expired && it.name == preferred }
        ?: artifacts.firstOrNull { !it.expired && it.name.contains("apk", ignoreCase = true) }

private data class PreviewApk(
    val file: File,
    val packageName: String,
    val uri: Uri,
)

private object PreviewApkInstaller {
    suspend fun downloadApk(
        context: Context,
        configuration: BuildConfiguration,
        artifact: GitHubArtifact,
    ): PreviewApk = withContext(Dispatchers.IO) {
        val gateway = GitHubActionsGateway.forBuildStore(CredentialSecurityStore(context))
        val root = File(context.cacheDir, "preview-apk").apply { mkdirs() }
        root.listFiles()?.forEach { it.delete() }
        val zipFile = File.createTempFile("artifact-", ".zip", root)
        try {
            FileOutputStream(zipFile).use { output ->
                gateway.downloadArtifact(
                    owner = configuration.githubOwner,
                    repository = configuration.githubRepository,
                    artifactId = artifact.id,
                    output = output,
                ).getOrElse { throw it }
            }
            val apk = File(root, "preview.apk")
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var found = false
                while (!found) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name.contains("..")) throw IllegalStateException("Unsafe artifact path.")
                    if (!entry.name.endsWith(".apk", ignoreCase = true)) continue
                    FileOutputStream(apk).use { output -> zip.copyTo(output) }
                    found = true
                }
            }
            require(apk.exists() && apk.length() > 0L) { "The artifact did not contain an APK." }
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_META_DATA,
            ) ?: throw IllegalStateException("Unable to inspect the generated APK.")
            val packageName = packageInfo.packageName
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".preview-files",
                apk,
            )
            PreviewApk(apk, packageName, uri)
        } finally {
            zipFile.delete()
        }
    }
}

private object PreviewRuntimeLogReader {
    fun read(context: Context, packageName: String): List<String> {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pid = manager.runningAppProcesses?.firstOrNull { it.processName == packageName }?.pid
        val command = if (pid != null) {
            listOf("logcat", "--pid", pid.toString(), "-t", "220")
        } else {
            listOf("logcat", "-d", "-t", "220")
        }
        return runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.lineSequence()
                .filter {
                    pid != null ||
                        it.contains(packageName) ||
                        it.contains("AndroidRuntime") ||
                        it.contains("FATAL EXCEPTION")
                }
                .takeLast(160)
                .toList()
        }.getOrDefault(emptyList())
    }
}

private object PreviewDocumentRenderer {
    fun render(name: String, source: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "html", "htm" -> ensureHtml(source)
            "js", "mjs", "cjs", "ts", "tsx" -> javaScriptPreview(source)
            "css", "scss", "sass", "less" -> cssPreview(source)
            "md", "markdown" -> markdown(source)
            else -> sourceDocument(name, source)
        }
    }

    private fun javaScriptPreview(source: String): String =
        "<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">" +
            "<style>body{margin:0;padding:16px;background:#0b0d12;color:#e5e7eb;font-family:monospace}pre{white-space:pre-wrap}</style>" +
            "</head><body><h3 style="font-family:sans-serif">JavaScript / TypeScript preview</h3><pre id="out"></pre><script>" +
            "const out=document.getElementById('out');" +
            "const oldLog=console.log;console.log=(...a)=>{oldLog(...a);out.textContent+=a.map(String).join(' ')+'\\n';};" +
            "window.onerror=(m,s,l,c)=>{out.textContent+='ERROR: '+m+' @'+l+':'+c+'\\n';};" +
            escapeHtmlScript(source) +
            "</script></body></html>"

    private fun cssPreview(source: String): String =
        "<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>" +
            source +
            "</style></head><body><main style="padding:20px;font-family:sans-serif">" +
            "<h2>CSS live preview</h2><p>Edit the stylesheet in DevForge and the preview updates automatically.</p><button>Sample button</button></main></body></html>"

    private fun ensureHtml(source: String): String =
        if (source.contains("<html", ignoreCase = true)) source
        else "<!doctype html><html><head><meta name=viewport content=\"width=device-width,initial-scale=1\"></head><body>" +
            source + "</body></html>"

    private fun sourceDocument(name: String, source: String): String =
        "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>" +
            "body{margin:0;padding:16px;background:#0b0d12;color:#e5e7eb;font-family:monospace}" +
            "h3{font-family:sans-serif}pre{white-space:pre-wrap;word-break:break-word}" +
            "</style></head><body><h3>Source preview · " + escapeHtml(name) + "</h3><pre>" +
            escapeHtml(source) + "</pre></body></html>"

    private fun markdown(source: String): String =
        "<!doctype html><html><body style=\"font-family:sans-serif;padding:16px\">" +
            source.lineSequence().joinToString("<br>") { line ->
                when {
                    line.startsWith("# ") -> "<h2>" + escapeHtml(line.removePrefix("# ")) + "</h2>"
                    line.startsWith("## ") -> "<h3>" + escapeHtml(line.removePrefix("## ")) + "</h3>"
                    line.startsWith("- ") -> "• " + escapeHtml(line.removePrefix("- "))
                    else -> escapeHtml(line)
                }
            } +
            "</body></html>"

    private fun escapeHtml(source: String): String =
        source.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

    private fun escapeHtmlScript(source: String): String =
        source.replace("</script", "<\\/script")
}
