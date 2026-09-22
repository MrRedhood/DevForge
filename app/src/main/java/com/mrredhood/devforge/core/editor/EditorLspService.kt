package com.mrredhood.devforge.core.editor

import android.content.Context
import com.mrredhood.devforge.core.workspace.WorkspaceSemanticRetrievalService
import com.mrredhood.devforge.core.workspace.WorkspaceSymbolIndexStore

/**
 * Optional LSP-compatible facade. DevForge keeps the protocol boundary independent from
 * a bundled language server, so servers can be registered later without weakening SAF,
 * capability or approval boundaries.
 */
class EditorLspService(context: Context) {
    private val symbols = WorkspaceSymbolIndexStore(context.applicationContext)
    private val retrieval = WorkspaceSemanticRetrievalService(context)

    fun descriptor(): LanguageServerDescriptor = LanguageServerDescriptor(
        id = "devforge-builtin",
        displayName = "DevForge built-in language intelligence",
        languages = EditorLanguage.entries.filter { it != EditorLanguage.PLAIN }.toSet(),
        capabilities = setOf(
            LspCapability.DIAGNOSTICS,
            LspCapability.SYMBOLS,
            LspCapability.DEFINITION,
            LspCapability.REFERENCES,
        ),
    )

    fun diagnostics(path: String, content: String): LspQueryResult<List<LspDiagnostic>> =
        LspQueryResult.Ready(
            EditorDiagnostics.analyze(path, content).diagnostics.map { diagnostic ->
                LspDiagnostic(
                    severity = diagnostic.severity.name,
                    message = diagnostic.message,
                    code = diagnostic.code,
                    range = diagnostic.location?.let {
                        val line = it.line ?: 1
                        val column = it.column ?: 1
                        LspRange(
                            LspPosition(line, column),
                            LspPosition(line, column + 1),
                        )
                    },
                )
            },
        )

    fun symbols(workspaceId: String, query: String): LspQueryResult<List<LspSymbol>> =
        LspQueryResult.Ready(
            symbols.search(workspaceId, query).take(80).map { symbol ->
                LspSymbol(
                    name = symbol.name,
                    kind = symbol.kind,
                    path = symbol.path,
                    range = LspRange(
                        LspPosition(symbol.line, 1),
                        LspPosition(symbol.line, symbol.name.length.coerceAtLeast(1) + 1),
                    ),
                )
            },
        )

    suspend fun definition(workspaceId: String, symbol: String): LspQueryResult<LspSymbol?> {
        val match = symbols.search(workspaceId, symbol)
            .firstOrNull { it.name.equals(symbol.trim(), ignoreCase = true) }
        return LspQueryResult.Ready(
            match?.let {
                LspSymbol(
                    it.name,
                    it.kind,
                    it.path,
                    LspRange(LspPosition(it.line, 1), LspPosition(it.line, it.name.length + 1)),
                )
            },
        )
    }

    suspend fun references(workspaceId: String, symbol: String): LspQueryResult<List<LspSymbol>> =
        LspQueryResult.Ready(
            retrieval.retrieve(workspaceId, symbol, limit = 40).mapNotNull { match ->
                match.line?.let { line ->
                    LspSymbol(
                        symbol,
                        match.kind,
                        match.path,
                        LspRange(LspPosition(line, 1), LspPosition(line, symbol.length + 1)),
                    )
                }
            },
        )

    fun rename(
        path: String,
        content: String,
        oldName: String,
        newName: String,
    ): LspQueryResult<String> {
        require(oldName.isNotBlank()) { "Old symbol name cannot be empty." }
        require(newName.isNotBlank()) { "New symbol name cannot be empty." }
        val replaced = Regex("\b" + Regex.escape(oldName) + "\b").replace(content, newName)
        return LspQueryResult.Ready(replaced)
    }
}
