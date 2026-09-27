package com.mrredhood.devforge.core.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.editor.EditorLspService
import com.mrredhood.devforge.core.editor.LspQueryResult
import com.mrredhood.devforge.core.editor.LspSymbol
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeIntelligenceScreen(
    onBack: () -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
) {
    val context = workspace.getApplication<android.app.Application>()
    val service = remember(context) { EditorLspService(context) }
    val descriptor = remember(service) { service.descriptor() }
    val workspaceId = workspace.workspace?.id
    var query by remember { mutableStateOf("") }
    var symbols by remember(workspaceId) { mutableStateOf<List<LspSymbol>>(emptyList()) }
    var definition by remember(workspaceId) { mutableStateOf<LspSymbol?>(null) }
    var references by remember(workspaceId) { mutableStateOf<List<LspSymbol>>(emptyList()) }
    var searching by remember(workspaceId) { mutableStateOf(false) }

    fun runLookup() {
        val clean = query.trim()
        if (workspaceId == null || clean.isBlank()) {
            symbols = emptyList()
            definition = null
            references = emptyList()
            return
        }
        searching = true
        symbols = (service.symbols(workspaceId, clean) as LspQueryResult.Ready).value
        kotlinx.coroutines.GlobalScope // bounded facade calls are in-memory/index based
        definition = null
        references = emptyList()
    }

    LaunchedEffect(workspaceId, query.trim()) {
        val clean = query.trim()
        if (workspaceId == null || clean.isBlank()) {
            symbols = emptyList()
            definition = null
            references = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        symbols = (service.symbols(workspaceId, clean) as LspQueryResult.Ready).value
        definition = service.definition(workspaceId, clean).let { result ->
            (result as? LspQueryResult.Ready)?.value
        }
        references = (service.references(workspaceId, clean) as? LspQueryResult.Ready)?.value.orEmpty()
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Code Intelligence", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Built-in code intelligence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(descriptor.displayName)
                        Text(
                            "Capabilities: " + descriptor.capabilities.joinToString(", ").lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (workspaceId == null) "Select a workspace to search indexed symbols." else "Workspace index: ready for symbol, definition and reference queries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Symbol") },
                    placeholder = { Text("Class, function, interface…") },
                    singleLine = true,
                )
            }
            item {
                TextButton(onClick = { runLookup() }, enabled = workspaceId != null && query.isNotBlank()) {
                    Text(if (searching) "Searching…" else "Search symbol")
                }
            }
            item {
                Text("Definitions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                if (definition == null) {
                    Text(
                        "No exact definition match yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SymbolCard(definition!!)
                }
            }
            item {
                Text("Matching symbols", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (symbols.isEmpty()) {
                item {
                    Text("No indexed symbols matched the query.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(symbols.size) { index -> SymbolCard(symbols[index]) }
            }
            item {
                Text(
                    "References",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (references.isEmpty()) {
                item {
                    Text("No indexed references were found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(references.size) { index -> SymbolCard(references[index]) }
            }
        }
    }
}

@Composable
private fun SymbolCard(symbol: LspSymbol) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(symbol.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(symbol.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(symbol.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Line " + symbol.range.start.line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
