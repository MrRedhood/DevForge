package com.mrredhood.devforge.core.extension

enum class ExtensionCapability {
    READ_WORKSPACE,
    EDIT_WORKSPACE,
    NETWORK,
    GIT,
    BUILD,
}

data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val capabilities: Set<ExtensionCapability>,
    val entryPoint: String,
)

sealed interface ExtensionValidation {
    data class Valid(val manifest: ExtensionManifest) : ExtensionValidation
    data class Invalid(val message: String) : ExtensionValidation
}
