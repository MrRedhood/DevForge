package com.mrredhood.devforge.core.extension

import com.mrredhood.devforge.core.editor.EditorLanguage
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class AnalyzedExtension(
    val manifest: ExtensionManifest,
    val packageRoot: File,
)

object ExtensionPackageAnalyzer {
    private val acodeModules = setOf("commands")

    fun analyze(root: File): Result<AnalyzedExtension> = runCatching {
        val packageRoot = locatePackageRoot(root)
        val plugin = File(packageRoot, "plugin.json")
        val packageJson = File(packageRoot, "package.json")
        when {
            plugin.isFile -> analyzeAcode(packageRoot, JSONObject(plugin.readText()))
            packageJson.isFile -> analyzeVsCode(packageRoot, JSONObject(packageJson.readText()))
            else -> error("Package must contain plugin.json (Acode) or package.json (VS Code).")
        }
    }

    private fun locatePackageRoot(root: File): File {
        if (File(root, "plugin.json").isFile || File(root, "package.json").isFile) return root
        val children = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val nested = children.firstOrNull { child ->
            File(child, "plugin.json").isFile || File(child, "package.json").isFile
        }
        return nested ?: root
    }

    private fun analyzeAcode(root: File, json: JSONObject): AnalyzedExtension {
        val id = json.optString("id").trim()
        val name = json.optString("name").trim()
        val version = json.optString("version").trim()
        val main = json.optString("main").trim().ifBlank { "main.js" }
        require(id.isNotBlank() && name.isNotBlank() && version.isNotBlank()) { "Acode plugin manifest is incomplete." }
        val entry = File(root, main)
        require(entry.isFile) { "Acode entry script '$main' is missing." }
        val source = entry.readText(Charsets.UTF_8)
        require(source.length <= MAX_SCRIPT_BYTES) { "Acode entry script is too large." }

        val requires = Regex("""acode\.require\(\s*["']([^"']+)["']\s*\)""")
            .findAll(source).map { it.groupValues[1] }.toSet()
        val unsupported = requires - acodeModules
        val isIconPack = File(root, "file_icons.json").isFile || File(root, "folder_icons.json").isFile ||
            source.contains("file_icons.json") || source.contains("folder_icons.json")
        val kind = when {
            isIconPack -> ExtensionPackageKind.ICON_THEME
            source.contains("setPluginInit") -> ExtensionPackageKind.RUNTIME
            else -> ExtensionPackageKind.DECLARATIVE
        }
        val compatibility = when {
            unsupported.isNotEmpty() -> ExtensionCompatibility.UNSUPPORTED
            else -> if (kind == ExtensionPackageKind.RUNTIME || kind == ExtensionPackageKind.ICON_THEME) {
                if (kind == ExtensionPackageKind.RUNTIME) ExtensionCompatibility.ACODE_RUNTIME_SUPPORTED
                else ExtensionCompatibility.DECLARATIVE_SUPPORTED
            } else ExtensionCompatibility.DECLARATIVE_SUPPORTED
        }
        val reason = unsupported.takeIf { it.isNotEmpty() }?.joinToString(", ") { "unsupported Acode module '$it'" }
        return AnalyzedExtension(
            ExtensionManifest(
                id = id, name = name, version = version,
                capabilities = setOf(ExtensionCapability.READ_WORKSPACE, ExtensionCapability.EDIT_WORKSPACE),
                entryPoint = main, source = ExtensionSource.ACODE, kind = kind,
                compatibility = compatibility, description = json.optString("description"),
                unsupportedReason = reason,
            ),
            root,
        )
    }

    private fun analyzeVsCode(root: File, json: JSONObject): AnalyzedExtension {
        val publisher = json.optString("publisher").trim()
        val rawName = json.optString("name").trim()
        val id = listOf(publisher, rawName).filter { it.isNotBlank() }.joinToString(".").ifBlank { rawName }
        val name = json.optString("displayName").ifBlank { rawName }.trim()
        val version = json.optString("version").trim()
        require(id.isNotBlank() && name.isNotBlank() && version.isNotBlank()) { "VS Code package manifest is incomplete." }

        val c = json.optJSONObject("contributes") ?: JSONObject()
        val languages = parseLanguages(c.optJSONArray("languages"))
        val icons = parseIconThemes(c.optJSONArray("iconThemes"))
        val commands = names(c.optJSONArray("commands"), "command")
        val grammars = names(c.optJSONArray("grammars"), "path")
        val snippets = names(c.optJSONArray("snippets"), "path")
        val themes = names(c.optJSONArray("themes"), "path")
        val native = languages.mapNotNull { nativeLanguageFor(it)?.name?.replace('_', ' ') }.distinct()
        val browser = json.optString("browser").trim()
        val main = json.optString("main").trim()
        val declarative = icons.isNotEmpty() || languages.isNotEmpty() || grammars.isNotEmpty() ||
            snippets.isNotEmpty() || themes.isNotEmpty()
        val executable = browser.isNotBlank() || main.isNotBlank() || commands.isNotEmpty()
        val unsupportedLanguage = languages.isNotEmpty() && native.size < languages.size
        val compatibility = when {
            unsupportedLanguage -> ExtensionCompatibility.UNSUPPORTED
            icons.isNotEmpty() -> ExtensionCompatibility.DECLARATIVE_SUPPORTED
            native.isNotEmpty() -> ExtensionCompatibility.NATIVE_LANGUAGE
            declarative && !executable -> ExtensionCompatibility.DECLARATIVE_SUPPORTED
            browser.isNotBlank() && webEntrySupported(root, browser) -> ExtensionCompatibility.VSCODE_WEB_RUNTIME_SUPPORTED
            else -> ExtensionCompatibility.UNSUPPORTED
        }
        val reason = if (compatibility == ExtensionCompatibility.UNSUPPORTED) {
            when {
                unsupportedLanguage -> "This package declares a programming language that DevForge does not currently support natively; it was not installed as a show-only language entry."
                main.isNotBlank() -> "This package requires the VS Code Node extension host, which DevForge does not emulate on Android."
                else -> "This package requires executable behavior that the DevForge Web extension bridge cannot safely verify."
            }
        } else null
        return AnalyzedExtension(
            ExtensionManifest(
                id = id, name = name, version = version,
                capabilities = if (main.isNotBlank() || browser.isNotBlank()) {
                    setOf(ExtensionCapability.READ_WORKSPACE, ExtensionCapability.EDIT_WORKSPACE)
                } else setOf(ExtensionCapability.READ_WORKSPACE),
                entryPoint = browser.ifBlank { main },
                source = ExtensionSource.VSCODE,
                kind = when {
                    icons.isNotEmpty() -> ExtensionPackageKind.ICON_THEME
                    languages.isNotEmpty() -> ExtensionPackageKind.LANGUAGE
                    snippets.isNotEmpty() -> ExtensionPackageKind.SNIPPET
                    grammars.isNotEmpty() -> ExtensionPackageKind.GRAMMAR
                    themes.isNotEmpty() -> ExtensionPackageKind.THEME
                    else -> ExtensionPackageKind.RUNTIME
                },
                compatibility = compatibility,
                description = json.optString("description"),
                nativeLanguages = native,
                unsupportedReason = reason,
                contributions = ExtensionContributions(
                    languages = languages, iconThemes = icons, commands = commands,
                    grammars = grammars, snippets = snippets, themes = themes,
                ),
            ),
            root,
        )
    }

    private fun webEntrySupported(root: File, browser: String): Boolean {
        val file = File(root, browser)
        if (!file.isFile || file.length() > MAX_SCRIPT_BYTES) return false
        val source = file.readText(Charsets.UTF_8)
        if (Regex("""\b(import|export)\s""").containsMatchIn(source)) return false
        return listOf("child_process", "fs", "net", "http", "https").none {
            source.contains("\"$it\"") || source.contains("'$it'")
        }
    }

    private fun parseLanguages(array: JSONArray?): List<ExtensionLanguageContribution> =
        if (array == null) emptyList() else buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank()) continue
                add(
                    ExtensionLanguageContribution(
                        id = id,
                        label = item.optString("aliases").ifBlank { id },
                        extensions = item.optJSONArray("extensions").strings().map { it.removePrefix(".") },
                        aliases = item.optJSONArray("aliases").strings(),
                    ),
                )
            }
        }

    private fun parseIconThemes(array: JSONArray?): List<ExtensionIconThemeContribution> =
        if (array == null) emptyList() else buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val path = item.optString("path").trim()
                if (id.isNotBlank() && path.isNotBlank()) {
                    add(ExtensionIconThemeContribution(id, item.optString("label").ifBlank { id }, path))
                }
            }
        }

    private fun names(array: JSONArray?, key: String): List<String> =
        if (array == null) emptyList() else buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                item.optString(key).takeIf { it.isNotBlank() }?.let(::add)
            }
        }

    private fun nativeLanguageFor(language: ExtensionLanguageContribution): EditorLanguage? {
        val probes = language.extensions.map { "sample.$it" } + language.id + language.aliases
        return probes.asSequence().map {
            when (it.lowercase()) {
                "c++", "cpp", "c" -> EditorLanguage.C_LIKE
                "javascript" -> EditorLanguage.JAVASCRIPT
                "typescript" -> EditorLanguage.TYPESCRIPT
                "c#" -> EditorLanguage.CSHARP
                "shell", "bash" -> EditorLanguage.SHELL
                "objective-c" -> EditorLanguage.OBJECTIVE_C
                else -> EditorLanguage.detect(it)
            }
        }.firstOrNull { it != EditorLanguage.PLAIN }
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else buildList {
            for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }

    private const val MAX_SCRIPT_BYTES = 2L * 1024L * 1024L
}
