package com.mrredhood.devforge.core.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AISettingsScreen(viewModel: AISettingsViewModel = viewModel()) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("AI Providers", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Provider", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AIProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = viewModel.provider == provider,
                                onClick = { viewModel.selectProvider(provider) },
                                label = { Text(provider.displayName) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = viewModel.apiKey,
                        onValueChange = viewModel::setApiKey,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API key") },
                        placeholder = { Text("Stored encrypted with Android Keystore") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::save) { Text("Save key") }
                        TextButton(onClick = viewModel::remove) { Text("Remove") }
                    }
                    Text(viewModel.status, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Model catalog", fontWeight = FontWeight.Bold)
                    Text("DevForge loads the provider's live model list from the Model dropdown. Context limits are taken from the provider API when available and fall back to an internet lookup when missing.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
