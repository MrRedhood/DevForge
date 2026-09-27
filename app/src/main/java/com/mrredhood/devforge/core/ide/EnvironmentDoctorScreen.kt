package com.mrredhood.devforge.core.ide

import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel

private data class DoctorCheck(
    val title: String,
    val detail: String,
    val ok: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentDoctorScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
) {
    var checks by remember { mutableStateOf<List<DoctorCheck>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }



    LaunchedEffect(workspace.rootUri, workspace.entries.size, workspace.remoteWorkspace?.repository) {
        val active = workspace.workspace
        if (active == null || workspace.rootUri == null) {
            checks = listOf(DoctorCheck("Workspace", "Choose a workspace before running diagnostics.", false))
            scanning = false
        } else {
            scanning = true
            val rootEntries = workspace.entries
            val buildFiles = rootEntries.filter {
                it.name in setOf("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts")
            }
            val wrapper = rootEntries.any { it.name == "gradlew" || it.name == "gradlew.bat" }
            val androidProject = buildFiles.isNotEmpty() || rootEntries.any { it.name == "app" }
            val remote = workspace.remoteWorkspace
            val gitResult = if (remote != null) {
                true to (remote.owner + "/" + remote.repository + " · " + remote.branch)
            } else {
                val root = workspace.rootUri
                val state = if (root == null) null else GitRepositoryService(workspace.getApplication<android.app.Application>().contentResolver).detect(root)
                when (state) {
                    is GitDetectionState.Detected -> true to "Repository detected · " + (state.repository.branchName ?: "detached")
                    GitDetectionState.NotDetected -> false to "No readable Git repository detected."
                    GitDetectionState.Detecting -> false to "Git detection is still running."
                    is GitDetectionState.Unsupported -> false to state.reason
                    null -> false to "No workspace root."
                }
            }
            checks = listOf(
                DoctorCheck("Workspace", active.name, true),
                DoctorCheck(
                    "Android project",
                    if (androidProject) "Gradle/Android project markers detected." else "No common Android/Gradle project markers found at the workspace root.",
                    androidProject,
                ),
                DoctorCheck(
                    "Gradle wrapper",
                    if (wrapper) "gradlew/gradlew.bat detected." else "Gradle wrapper not found at the workspace root.",
                    wrapper,
                ),
                DoctorCheck("Git", gitResult.second, gitResult.first),
                DoctorCheck(
                    "GitHub workspace",
                    if (remote != null) "Linked to " + remote.owner + "/" + remote.repository + " on " + remote.branch else "Local workspace; no GitHub-backed workspace is linked.",
                    true,
                ),
                DoctorCheck("Android runtime", "Android " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT, true),
                DoctorCheck(
                    "Device",
                    (Build.MANUFACTURER + " " + Build.MODEL).trim().ifBlank { "Unknown Android device" },
                    true,
                ),
                DoctorCheck("Compose UI", "Material 3 and adaptive Compose dependencies are configured in the app.", true),
                DoctorCheck("AI", "AI execution is cloud/provider based; no local model runtime is required.", true),
                DoctorCheck("Web preview", "Web Live Preview is available from the Preview surface.", true),
            )
            scanning = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Environment doctor", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = workspace::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh environment")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Development readiness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (scanning) "Checking workspace and platform signals…"
                                else checks.count { it.ok }.toString() + " / " + checks.size + " checks healthy",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(checks, key = { it.title }) { check ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (check.ok) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (check.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(check.title, fontWeight = FontWeight.SemiBold)
                            Text(check.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
