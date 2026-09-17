package com.mrredhood.devforge.core.build

import androidx.lifecycle.ViewModel

class BuildViewModel : ViewModel() {
    var configuration = BuildConfiguration()
        private set

    var state: BuildState = BuildState.Ready(configuration)
        private set

    val capabilities = BuildCapabilityState()

    fun selectTarget(target: BuildTarget) {
        configuration = when (target) {
            BuildTarget.DebugApk -> configuration.copy(
                target = target,
                buildTask = ":app:assembleDebug",
                artifactName = "devforge-debug-apk",
            )
            BuildTarget.ReleaseApk -> configuration.copy(
                target = target,
                buildTask = ":app:assembleRelease",
                artifactName = "devforge-release-apk",
            )
            BuildTarget.ReleaseBundle -> configuration.copy(
                target = target,
                buildTask = ":app:bundleRelease",
                artifactName = "devforge-release-aab",
            )
        }
        state = BuildState.Ready(configuration)
    }

    fun updateBranch(branch: String) {
        configuration = configuration.copy(branch = branch.ifBlank { "main" })
        state = BuildState.Ready(configuration)
    }

    fun resetToReady() {
        state = BuildState.Ready(configuration)
    }

    /** Explicit until GitHub authentication + workflow_dispatch is implemented. */
    fun requestDispatch() {
        state = if (capabilities.githubDispatch == CapabilityAvailability.Available) {
            BuildState.Dispatching(configuration)
        } else {
            BuildState.Failed("GitHub Actions dispatch is not connected yet. The build plan is ready, but no remote run was started.")
        }
    }
}
