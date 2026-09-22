package com.mrredhood.devforge.core.editor

enum class LspCapability {
    DIAGNOSTICS,
    SYMBOLS,
    DEFINITION,
    REFERENCES,
    RENAME,
    CODE_ACTIONS,
}

data class LspPosition(val line: Int, val character: Int)
data class LspRange(val start: LspPosition, val end: LspPosition)

data class LspSymbol(
    val name: String,
    val kind: String,
    val path: String,
    val range: LspRange,
)

data class LspDiagnostic(
    val severity: String,
    val message: String,
    val code: String?,
    val range: LspRange?,
)

data class LanguageServerDescriptor(
    val id: String,
    val displayName: String,
    val languages: Set<EditorLanguage>,
    val capabilities: Set<LspCapability>,
)

sealed interface LspQueryResult<out T> {
    data class Ready<T>(val value: T) : LspQueryResult<T>
    data class Unsupported(val message: String) : LspQueryResult<Nothing>
}
