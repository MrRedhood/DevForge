package com.mrredhood.devforge.core.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Terminal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Native, sandboxed and approval-aware execution.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = viewModel::newSession) { Icon(Icons.Default.Add, "New terminal") }
            IconButton(onClick = viewModel::closeSession, enabled = viewModel.sessions.size > 1 && !viewModel.isRunning) {
                Icon(Icons.Default.Close, "Close terminal")
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            viewModel.sessions.forEach { session ->
                FilterChip(
                    selected = session.id == viewModel.activeSessionId,
                    onClick = { viewModel.selectSession(session.id) },
                    label = { Text(session.name) },
                )
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TerminalExecutable.entries.forEach { item ->
                        FilterChip(
                            selected = viewModel.executable == item,
                            onClick = { viewModel.selectExecutable(item) },
                            label = { Text(item.name) },
                        )
                    }
                }
                BasicTextField(
                    value = viewModel.argsText,
                    onValueChange = { viewModel.argsText = it.take(TerminalCommandPolicy.MAX_COMMAND_BYTES) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { inner ->
                        Column {
                            if (viewModel.argsText.isBlank()) {
                                Text("Arguments separated by spaces", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::run,
                        enabled = viewModel.workspaceId != null && !viewModel.isRunning,
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(if (viewModel.isRunning) "Running…" else "Run")
                    }
                    TextButton(onClick = viewModel::clearOutput) {
                        Icon(Icons.Default.DeleteSweep, null)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text("Clear")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Live output", fontWeight = FontWeight.Bold)
                Text(
                    viewModel.output.ifBlank {
                        if (viewModel.workspaceId == null) "Choose a workspace to use Terminal."
                        else "No command output yet."
                    },
                    Modifier.padding(top = 8.dp),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        viewModel.statusMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissStatus) { Text("Dismiss") }
                }
            }
        }
    }
}
