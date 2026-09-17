package com.mrredhood.devforge.core.build

/**
 * Declarative build configuration. The execution layer can later map this to a
 * GitHub Actions workflow_dispatch request without coupling the UI to GitHub APIs.
 */
data class BuildConfiguration(
    val workflowFile: String = ".github/workflows/android.yml",
    val branch: String = "main",
    val buildTask: String = ":app:assembleDebug",
    val artifactName: String = "devforge-debug-apk",
    val target: BuildTarget = BuildTarget.DebugApk,
)

enum class BuildTarget(
    val label: String,
    val description: String,
) {
    DebugApk("Debug APK", "Fast installable debug package"),
    ReleaseApk("Release APK", "Signed release package when signing is configured"),
    ReleaseBundle("Release AAB", "Play-ready bundle when signing is configured"),
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
