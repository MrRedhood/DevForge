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
    val buildArtifact: Boolean = true,
    val publishArtifacts: Boolean = true,
    val lintReport: Boolean = true,
    val unitTestReport: Boolean = true,
    val dependencyReport: Boolean = false,
)

enum class BuildTarget(
    val label: String,
    val description: String,
    val workflowInput: String,
    val buildTask: String,
    val artifactName: String,
) {
    DebugApk("Debug APK", "Fast installable debug package", "debug_apk", ":app:assembleDebug", "devforge-debug-apk"),
    ReleaseApk("Release APK", "Signed release package; release signing secrets are required", "release_apk", ":app:assembleRelease", "devforge-release-apk"),
    ReleaseBundle("Release AAB", "Signed Play-ready bundle; release signing secrets are required", "release_aab", ":app:bundleRelease", "devforge-release-aab"),
}

fun BuildConfiguration.withTarget(target: BuildTarget): BuildConfiguration = copy(
    target = target,
    buildTask = target.buildTask,
    artifactName = target.artifactName,
)

sealed interface BuildState {
    data object Idle : BuildState
    data class Ready(val configuration: BuildConfiguration) : BuildState
    data class AwaitingApproval(val approvalId: String, val configuration: BuildConfiguration) : BuildState
    data class Dispatching(val configuration: BuildConfiguration) : BuildState
    data class Cancelling(val runId: Long, val configuration: BuildConfiguration) : BuildState
    data class Running(val runId: Long, val configuration: BuildConfiguration) : BuildState
    data class Succeeded(val runId: Long, val artifactName: String) : BuildState
    data class Failed(val message: String) : BuildState
    data class Cancelled(val runId: Long) : BuildState
}

data class BuildCapabilityState(
    val githubDispatch: CapabilityAvailability = CapabilityAvailability.Unavailable,
    val cancelBuild: CapabilityAvailability = CapabilityAvailability.Unavailable,
    val liveLogs: CapabilityAvailability = CapabilityAvailability.Unavailable,
    val artifacts: CapabilityAvailability = CapabilityAvailability.Unavailable,
)

enum class CapabilityAvailability {
    Available,
    Unavailable,
    NotConfigured,
}
