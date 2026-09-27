package com.mrredhood.devforge.core.editor

/**
 * Lightweight registration boundary for optional external language-server adapters.
 *
 * DevForge always keeps its built-in language intelligence available. External providers
 * can be registered later without coupling editor UI, SAF access, or approval policy to a
 * particular server process.
 */
class LanguageServerRegistry(
    descriptors: Collection<LanguageServerDescriptor>,
) {
    private val registered = descriptors
        .filter { it.id.isNotBlank() && it.displayName.isNotBlank() && it.languages.isNotEmpty() }
        .distinctBy { it.id }

    fun all(): List<LanguageServerDescriptor> = registered

    fun forLanguage(language: EditorLanguage): List<LanguageServerDescriptor> =
        registered.filter { language in it.languages }

    fun find(id: String): LanguageServerDescriptor? =
        registered.firstOrNull { it.id == id }
}
