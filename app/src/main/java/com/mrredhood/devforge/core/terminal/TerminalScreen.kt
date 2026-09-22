package com.mrredhood.devforge.core.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TerminalBlack = Color.Black
private val TerminalPanel = Color(0xFF0B0B0B)
private val TerminalText = Color(0xFFE6E6E6)
private val TerminalGreen = Color(0xFF55FF77)
private val TerminalMuted = Color(0xFF7D7D7D)
private val TerminalError = Color(0xFFFF6B6B)

@Composable
fun TerminalScreen(
    onBack: () -> Unit = {},
    viewModel: TerminalViewModel = viewModel(),
) {
    val outputState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val inputScroll = rememberScrollState()
    val outputLines = remember(viewModel.output) {
        if (viewModel.output.isBlank()) emptyList() else viewModel.output.split('\n')
    }
    val session = viewModel.sessions.firstOrNull { it.id == viewModel.activeSessionId }
    val path = session?.workingDirectory?.takeIf(String::isNotBlank)?.let { "~/$it" } ?: "~"
    val prompt = "devforge:$path$ "

    LaunchedEffect(viewModel.output, viewModel.isRunning) {
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalBlack),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalPanel)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TerminalText,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "TERMINAL",
                        color = TerminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = session?.name ?: "No session",
                        color = TerminalMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (viewModel.isRunning) "RUNNING" else "READY",
                        color = if (viewModel.isRunning) TerminalGreen else TerminalMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = viewModel::clearOutput,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Text(
                            "CLR",
                            color = TerminalText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(
                        onClick = viewModel::stop,
                        enabled = viewModel.isRunning,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Text(
                            "■",
                            color = if (viewModel.isRunning) TerminalError else TerminalMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                    IconButton(
                        onClick = viewModel::newSession,
                        enabled = viewModel.workspaceId != null && viewModel.sessions.size < 8 && !viewModel.isRunning,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.Add, "New terminal", tint = TerminalText, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = viewModel::closeSession,
                        enabled = viewModel.sessions.size > 1 && !viewModel.isRunning,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.Close, "Close terminal", tint = TerminalText, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (viewModel.sessions.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalPanel)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    viewModel.sessions.forEach { item ->
                        Text(
                            text = item.name,
                            modifier = Modifier.clickable { viewModel.selectSession(item.id) },
                            color = if (item.id == viewModel.activeSessionId) TerminalGreen else TerminalMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = if (item.id == viewModel.activeSessionId) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            LazyColumn(
                state = outputState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TerminalBlack)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (outputLines.isEmpty()) {
                    item {
                        Text(
                            text = "DevForge terminal",
                            color = TerminalMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    items(outputLines) { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = line,
                                color = if (line.startsWith("devforge:")) TerminalGreen else TerminalText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalBlack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = prompt,
                    color = TerminalGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                BasicTextField(
                    value = viewModel.commandLine,
                    onValueChange = viewModel::updateCommandLine,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(inputScroll)
                        .focusRequester(inputFocus)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when {
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
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalText,
                    ),
                    decorationBox = { inner -> inner() },
                )
            }

            viewModel.statusMessage?.let { status ->
                Text(
                    text = status,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalBlack)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    color = if (viewModel.isRunning) TerminalGreen else TerminalError,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
