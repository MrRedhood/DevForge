package com.mrredhood.devforge.core.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AISettingsScreen(viewModel: AISettingsViewModel = viewModel()) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "AI & models",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Connect a provider and store its API key securely on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ProviderMenu(
            selected = viewModel.provider,
            onSelect = viewModel::selectProvider,
        )

        if (viewModel.provider == AIProvider.OPENAI_COMPATIBLE) {
            OutlinedTextField(
                value = viewModel.customBaseUrl,
                onValueChange = viewModel::updateCustomBaseUrl,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                singleLine = true,
                label = { Text("Base URL") },
                placeholder = { Text("https://your-server.example/v1") },
            )
            OutlinedTextField(
                value = viewModel.customModelId,
                onValueChange = viewModel::updateCustomModelId,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                singleLine = true,
                label = { Text("Model ID (fallback)") },
                placeholder = { Text("Used when the /models endpoint is unavailable") },
            )
            Text(
                "HTTPS is required. HTTP is allowed only for localhost custom servers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = viewModel.apiKey,
            onValueChange = viewModel::updateApiKey,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            singleLine = true,
            label = { Text("API key") },
            placeholder = { Text("Enter provider key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Text(
            "Keys are encrypted with Android Keystore before being stored.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !viewModel.isTestingConnection,
        ) {
            Text("Save API key")
        }

        OutlinedButton(
            onClick = viewModel::testConnection,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !viewModel.isTestingConnection,
        ) {
            if (viewModel.isTestingConnection) {
                CircularProgressIndicator(Modifier.heightIn(max = 18.dp), strokeWidth = 2.dp)
            } else {
                Text("Test connection")
            }
        }

        OutlinedButton(
            onClick = viewModel::remove,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !viewModel.isTestingConnection,
        ) {
            Text("Remove saved key")
        }

        Text(
            viewModel.status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (viewModel.status.contains("Unable", ignoreCase = true) ||
                viewModel.status.contains("failed", ignoreCase = true)
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Text(
            "Live models are loaded automatically from the provider when you open Chat or the agent launcher.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderMenu(
    selected: AIProvider,
    onSelect: (AIProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Provider",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(selected.displayName, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AIProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(provider)
                    },
                )
            }
        }
    }
}
