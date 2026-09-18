package com.mrredhood.devforge.core.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Terminal", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clearOutput) { Text("Clear screen") }
            IconButton(
                onClick = viewModel::newSession,
                enabled = viewModel.workspaceId != null && viewModel.sessions.size < 8 && !viewModel.isRunning,
            ) { Icon(Icons.Default.Add, "New terminal") }
            IconButton(
                onClick = viewModel::closeSession,
                enabled = viewModel.sessions.size > 1 && !viewModel.isRunning,
            ) { Icon(Icons.Default.Close, "Close terminal") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            viewModel.sessions.forEach { session ->
                Surface(
                    onClick = { viewModel.selectSession(session.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (session.id == viewModel.activeSessionId) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(session.name, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontFamily = FontFamily.Monospace)
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (viewModel.output.isBlank()) {
                item {
                    Text("devforge:~$", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                items(
                    viewModel.output.split('\n'),
                    key = { index, line -> index.toString() + ":" + line.hashCode() },
                ) {
                    Text(it, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val session = viewModel.sessions.firstOrNull { it.id == viewModel.activeSessionId }
            val path = session?.workingDirectory?.takeIf { it.isNotBlank() }?.let { "~/" + it } ?: "~"
            Text("devforge:" + path + "$", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            BasicTextField(
                value = viewModel.commandLine,
                onValueChange = viewModel::updateCommandLine,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (event.key) {
                            Key.Enter -> { viewModel.run(); true }
                            Key.DirectionUp -> { viewModel.historyPrevious(); true }
                            Key.DirectionDown -> { viewModel.historyNext(); true }
                            else -> false
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(onClick = viewModel::historyPrevious) {
                Icon(Icons.Default.ArrowUpward, null)
                Spacer(Modifier.width(3.dp))
                Text("Previous command")
            }
            TextButton(onClick = viewModel::historyNext) {
                Icon(Icons.Default.ArrowDownward, null)
                Spacer(Modifier.width(3.dp))
                Text("Next command")
            }
        }

        viewModel.statusMessage?.let {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(it, Modifier.weight(1f), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::dismissStatus) { Text("Dismiss") }
            }
        }
    }
}
