package com.mrredhood.devforge.core.extension.api

data class DevForgeManifest(
    val manifestVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val publisherId: String,
    val publisherName: String,
    val description: String,
    val type: DevForgePackageType,
    val apiVersion: String,
    val minimumDevForgeVersion: String,
    val entryRuntime: DevForgeRuntimeType,
    val entryPoint: String,
    val permissions: Set<DevForgePermission>,
    val activationEvents: List<String>,
    val contributions: DevForgeContributions = DevForgeContributions(),
)

enum class DevForgePackageType {
    EXTENSION, THEME, ICON_PACK, LANGUAGE, AI_AGENT, AI_TOOL, WORKFLOW, AUTOMATION, TEMPLATE, TOOL_PACK, PROJECT,
}
enum class DevForgeRuntimeType { JAVASCRIPT }

data class DevForgeContributions(
    val commands: List<DevForgeCommandContribution> = emptyList(),
    val panels: List<DevForgePanelContribution> = emptyList(),
    val menus: List<DevForgeMenuContribution> = emptyList(),
    val languages: List<DevForgeLanguageContribution> = emptyList(),
)
data class DevForgeCommandContribution(val id: String, val title: String)
data class DevForgePanelContribution(val id: String, val title: String, val location: String)
data class DevForgeMenuContribution(val id: String, val title: String, val commandId: String, val group: String? = null)
data class DevForgeLanguageContribution(val id: String, val label: String, val extensions: List<String>)

sealed interface DevForgeManifestValidation {
    data class Valid(val manifest: DevForgeManifest) : DevForgeManifestValidation
    data class Invalid(val errors: List<String>) : DevForgeManifestValidation
}

data class DevForgeSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<DevForgeSemVer> {
    override fun compareTo(other: DevForgeSemVer): Int {
        val base = compareValuesBy(this, other, DevForgeSemVer::major, DevForgeSemVer::minor, DevForgeSemVer::patch)
        if (base != 0) return base
        if (preRelease == other.preRelease) return 0
        if (preRelease == null) return 1
        if (other.preRelease == null) return -1
        return preRelease.compareTo(other.preRelease)
    }

    fun stableString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        preRelease?.let { append('-').append(it) }
    }

    companion object {
        private val PATTERN = Regex("""^(0|[1-9]d*).(0|[1-9]d*).(0|[1-9]d*)(?:-([0-9A-Za-z.-]+))?$""")
        fun parse(value: String): DevForgeSemVer? {
            val match = PATTERN.matchEntire(value.trim()) ?: return null
            return DevForgeSemVer(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                preRelease = match.groupValues[4].ifBlank { null },
            )
        }
    }
}
