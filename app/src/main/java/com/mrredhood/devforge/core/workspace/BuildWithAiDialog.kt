package com.mrredhood.devforge.core.workspace

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun BuildWithAiDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreateProject: (parentUri: android.net.Uri, projectName: String, goal: String) -> Unit,
) {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf("MyProject") }
    var goal by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { parentUri ->
        if (parentUri == null) return@rememberLauncherForActivityResult
        if (projectName.isBlank() || goal.isBlank()) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                parentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        onCreateProject(parentUri, projectName.trim(), goal.trim())
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Build with AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "DevForge creates a real local workspace on your device first. GitHub is optional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it.take(8_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What should AI build?") },
                    minLines = 4,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { launcher.launch(null) },
                enabled = !busy && projectName.isNotBlank() && goal.isNotBlank(),
            ) {
                Text(if (busy) "Creating…" else "Choose folder & continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
