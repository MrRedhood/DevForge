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
    DebugApk("Debug APK", "Fast installable debug package", "debug_apk"),
    ReleaseApk("Release APK", "Signed release package when signing is configured", "release_apk"),
    ReleaseBundle("Release AAB", "Play-ready bundle when signing is configured", "release_aab"),
}

sealed interface BuildState {
    data object Idle : BuildState
    data class Ready(val configuration: BuildConfiguration) : BuildState
    data class AwaitingApproval(val approvalId: String, val configuration: BuildConfiguration) : BuildState
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
