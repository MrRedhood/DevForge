package com.mrredhood.devforge.core.build

/**
 * Declarative build configuration. The execution layer maps this to the fixed
 * GitHub Actions workflow_dispatch target contract without coupling the UI to GitHub APIs.
 */
data class BuildConfiguration(
    val githubOwner: String = "",
    val githubRepository: String = "",
    val workflowFile: String = ".github/workflows/android.yml",
    val branch: String = "main",
    val buildTask: String = ":app:assembleDebug",
    val artifactName: String = "devforge-debug-apk",
    val target: BuildTarget = BuildTarget.DebugApk,
)

enum class BuildTarget(
    val label: String,
    val description: String,
    val workflowInput: String,
) {
    DebugApk(
        label = "Debug APK",
        description = "Fast installable debug package",
        workflowInput = "debug_apk",
    ),
    ReleaseApk(
        label = "Release APK",
        description = "Signed release package when signing is configured",
        workflowInput = "release_apk",
    ),
    ReleaseBundle(
        label = "Release AAB",
        description = "Play-ready bundle when signing is configured",
        workflowInput = "release_aab",
    ),
}

sealed interface BuildState {
    data object Idle : BuildState
    data class Ready(val configuration: BuildConfiguration) : BuildState
    data class Dispatching(val configuration: BuildConfiguration) : BuildState
    data class Running(val runId: Long, val configuration: BuildConfiguration) : BuildState
    data class Succeeded(val runId: Long, val artifactName: String) : BuildState
    data class Failed(val message: String) : BuildState
    data class Cancelled(val runId: Long) : BuildState
}

data class BuildCapabilityState(
    val githubDispatch: CapabilityAvailability = CapabilityAvailability.Unavailable,
    val liveLogs: CapabilityAvailability = CapabilityAvailability.Unavailable,
    val artifacts: CapabilityAvailability = CapabilityAvailability.Unavailable,
)

enum class CapabilityAvailability {
    Available,
    Unavailable,
    NotConfigured,
}
