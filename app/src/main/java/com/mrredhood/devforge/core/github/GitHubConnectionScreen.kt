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
    var replacingToken by rememberSaveable { mutableStateOf(false) }
    val connected = viewModel.snapshot.state is GitHubConnectionState.Connected
    val credentialStored = connected || viewModel.snapshot.state is GitHubConnectionState.CredentialStored
    val canEditToken = !credentialStored || replacingToken

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("GitHub connection", style = MaterialTheme.typography.headlineSmall)
        Text("Connect DevForge to GitHub so repository discovery and remote builds can use an authenticated session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        connected -> "Connected and verified"
                        credentialStored -> "Credential stored"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    viewModel.snapshot.message
                        ?: when {
                        connected -> "The stored credential has been verified with GitHub."
                        credentialStored -> "A credential is stored securely but has not been verified in this session."
                        else -> "No GitHub credential is stored on this device."
                    },
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
            enabled = canEditToken && !viewModel.isValidating,
        )
        if (viewModel.isValidating) {
            Text(
                "Checking the token with GitHub…",
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
        if (canEditToken) {
            Button(
                onClick = {
                    viewModel.connectWithToken(token)
                    token = ""
                    replacingToken = false
                },
                enabled = token.isNotBlank() && !viewModel.isValidating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (credentialStored) "Verify & replace token" else "Connect with token")
            }
            if (replacingToken) {
                TextButton(
                    onClick = {
                        replacingToken = false
                        token = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel replacement") }
            }
        } else {
            TextButton(
                onClick = viewModel::validateCredential,
                enabled = !viewModel.isValidating,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verify credential now") }
            TextButton(
                onClick = {
                    replacingToken = true
                    token = ""
                },
                enabled = !viewModel.isValidating,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Replace token") }
            TextButton(
                onClick = { viewModel.disconnect(); onConnectionChanged() },
                enabled = !viewModel.isValidating,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Disconnect and erase credential") }
        }
    }
}
