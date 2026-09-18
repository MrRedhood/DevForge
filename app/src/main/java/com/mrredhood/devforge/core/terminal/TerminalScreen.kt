package com.mrredhood.devforge.core.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    val outputState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val outputLines = remember(viewModel.output) {
        viewModel.output.ifBlank { "" }.split('\n')
    }

    LaunchedEffect(viewModel.output) {
        if (outputLines.isNotEmpty()) {
            outputState.scrollToItem((outputLines.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(viewModel.workspaceId, viewModel.activeSessionId) {
        if (viewModel.workspaceId != null) {
            inputFocus.requestFocus()
            keyboard?.show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Terminal",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    viewModel.sessions.firstOrNull { it.id == viewModel.activeSessionId }?.name ?: "No session",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = viewModel::clearOutput) { Text("Clear") }
            IconButton(
                onClick = viewModel::stop,
                enabled = viewModel.isRunning,
            ) { Text("■", fontFamily = FontFamily.Monospace) }
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
                    color = if (session.id == viewModel.activeSessionId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        session.name,
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Card(
            Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            LazyColumn(
                state = outputState,
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (viewModel.output.isBlank()) {
                    item {
                        Text(
                            "DevForge shell — type help for builtins",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(outputLines) { line ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            Text(line, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        val session = viewModel.sessions.firstOrNull { it.id == viewModel.activeSessionId }
        val path = session?.workingDirectory?.takeIf { it.isNotBlank() }?.let { "~/" + it } ?: "~"
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "devforge:" + path + "$ ",
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                BasicTextField(
                    value = viewModel.commandLine,
                    onValueChange = viewModel::updateCommandLine,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .focusRequester(inputFocus)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when {
                                event.isCtrlPressed && event.key == Key.C -> {
                                    viewModel.stop()
                                    true
                                }
                                event.key == Key.Enter -> {
                                    viewModel.run()
                                    true
                                }
                                event.key == Key.DirectionUp -> {
                                    viewModel.historyPrevious()
                                    true
                                }
                                event.key == Key.DirectionDown -> {
                                    viewModel.historyNext()
                                    true
                                }
                                else -> false
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.run() }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (viewModel.commandLine.isBlank()) {
                                Text(
                                    "command…",
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(onClick = viewModel::historyPrevious) {
                Icon(Icons.Default.ArrowUpward, null)
                Spacer(Modifier.width(3.dp))
                Text("Previous")
            }
            TextButton(onClick = viewModel::historyNext) {
                Icon(Icons.Default.ArrowDownward, null)
                Spacer(Modifier.width(3.dp))
                Text("Next")
            }
            TextButton(onClick = viewModel::stop, enabled = viewModel.isRunning) { Text("Ctrl+C") }
        }

        viewModel.statusMessage?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
                fontFamily = FontFamily.Monospace,
                color = if (viewModel.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
