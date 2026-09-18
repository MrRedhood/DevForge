package com.mrredhood.devforge.core.github

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GitHubConnectionScreen(
    viewModel: GitHubConnectionViewModel = viewModel(),
    onConnectionChanged: () -> Unit = {},
) {
    var token by rememberSaveable { mutableStateOf("") }
    val connected = viewModel.snapshot.state is GitHubConnectionState.Connected

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("GitHub connection", style = MaterialTheme.typography.headlineSmall)
        Text("Connect DevForge to GitHub so repository discovery and remote builds can use an authenticated session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (connected) "Connected" else "Not connected", style = MaterialTheme.typography.titleMedium)
                Text(
                    viewModel.snapshot.message
                        ?: if (connected) "A credential is stored in Android Keystore-backed encrypted storage." else "No GitHub credential is stored on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GitHub access token") },
            placeholder = { Text("Paste a token with the minimum required repository permissions") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !connected,
        )
        Text(
            "Repository discovery reads repositories and Actions workflows. Workflow dispatch will remain separately gated until its execution path is enabled.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "For private repositories, the token must have the repository permissions required by GitHub's repository and Actions APIs. Tokens are never rendered back into the UI.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!connected) {
            Button(
                onClick = { viewModel.connectWithToken(token); token = ""; onConnectionChanged() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Store securely") }
        } else {
            TextButton(
                onClick = viewModel::validateCredential,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verify credential now") }
            TextButton(
                onClick = { viewModel.disconnect(); onConnectionChanged() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Disconnect and erase credential") }
        }
    }
}
