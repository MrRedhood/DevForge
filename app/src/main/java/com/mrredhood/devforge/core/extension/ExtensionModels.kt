package com.mrredhood.devforge.core.extension

enum class ExtensionCapability {
    READ_WORKSPACE, EDIT_WORKSPACE, NETWORK, GIT, BUILD,
}

enum class ExtensionSource { ACODE, VSCODE, DEVFORGE }

enum class ExtensionPackageKind {
    RUNTIME, LANGUAGE, ICON_THEME, THEME, SNIPPET, GRAMMAR, DECLARATIVE,
}

enum class ExtensionCompatibility {
    NATIVE_LANGUAGE,
    DECLARATIVE_SUPPORTED,
    ACODE_RUNTIME_SUPPORTED,
    VSCODE_WEB_RUNTIME_SUPPORTED,
    UNSUPPORTED,
}

data class ExtensionLanguageContribution(
    val id: String,
    val label: String,
    val extensions: List<String>,
    val aliases: List<String> = emptyList(),
)

data class ExtensionIconThemeContribution(
    val id: String,
    val label: String,
    val path: String,
)

enum class ExtensionSettingType { BOOLEAN, STRING, NUMBER }

data class ExtensionSetting(
    val key: String,
    val label: String,
    val description: String = "",
    val type: ExtensionSettingType = ExtensionSettingType.STRING,
    val defaultValue: String = "",
)

data class ExtensionContributions(
    val languages: List<ExtensionLanguageContribution> = emptyList(),
    val iconThemes: List<ExtensionIconThemeContribution> = emptyList(),
    val commands: List<String> = emptyList(),
    val grammars: List<String> = emptyList(),
    val snippets: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
)

data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val capabilities: Set<ExtensionCapability>,
    val entryPoint: String,
    val source: ExtensionSource = ExtensionSource.DEVFORGE,
    val kind: ExtensionPackageKind = ExtensionPackageKind.RUNTIME,
    val compatibility: ExtensionCompatibility = ExtensionCompatibility.UNSUPPORTED,
    val description: String = "",
    val rootPath: String = "",
    val contributions: ExtensionContributions = ExtensionContributions(),
    val nativeLanguages: List<String> = emptyList(),
    val unsupportedReason: String? = null,
    val settings: List<ExtensionSetting> = emptyList(),
    /** Source package URL used for optional future update checks. */
    val downloadUrl: String? = null,
    /** Human-facing catalog page for the installed package. */
    val sourcePageUrl: String? = null,
)

sealed interface ExtensionValidation {
    data class Valid(val manifest: ExtensionManifest) : ExtensionValidation
    data class Invalid(val message: String) : ExtensionValidation
}

data class MarketplaceExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val publisher: String,
    val source: ExtensionSource,
    val downloadUrl: String,
    val sourcePageUrl: String,
    val downloads: Long? = null,
    val rating: Double? = null,
    val priceText: String? = null,
    val installable: Boolean = true,
    val packageType: String = "extension",
    val apiVersion: String = "1.0",
    val minimumDevForgeVersion: String = "1.0.0",
    val requestedPermissions: List<String> = emptyList(),
)
