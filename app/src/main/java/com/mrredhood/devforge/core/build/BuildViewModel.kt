package com.mrredhood.devforge.core.build

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.github.GitHubActionsGateway
import com.mrredhood.devforge.core.github.GitHubConnectionViewModel
import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.Approval
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.AndroidSecretStore
import com.mrredhood.devforge.core.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class BuildViewModel(application: Application) : AndroidViewModel(application) {
    private val secretStore: SecretStore = AndroidSecretStore(application)
    private val githubGateway = GitHubActionsGateway.forBuildStore(secretStore)

    var configuration by mutableStateOf(BuildConfiguration())
        private set

    var state by mutableStateOf<BuildState>(BuildState.Ready(configuration))
        private set

    var capabilities by mutableStateOf(BuildCapabilityState())
        private set

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
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun updateBranch(branch: String) {
        configuration = configuration.copy(branch = branch.ifBlank { "main" })
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun configureGitHubRepository(
        owner: String,
        repository: String,
        defaultBranch: String,
        workflowFile: String,
    ) {
        configuration = configuration.copy(
            githubOwner = owner,
            githubRepository = repository,
            branch = defaultBranch.ifBlank { "main" },
            workflowFile = workflowFile,
        )
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun resetToReady() {
        state = BuildState.Ready(configuration)
    }

    /**
     * Dispatch is a side-effecting capability. The explicit Build Center button is the
     * user's one-shot confirmation until a dedicated Approval Center exists.
     */
    fun requestDispatch() {
        refreshDispatchCapability()
        if (capabilities.githubDispatch != CapabilityAvailability.Available) {
            state = BuildState.Failed(dispatchUnavailableMessage())
            return
        }

        val action = ActionRequest(
            actionId = "build-dispatch",
            capability = Capability.DISPATCH_BUILD,
            risk = RiskLevel.R2,
            workspaceId = "github:${configuration.githubOwner}/${configuration.githubRepository}",
            summary = "Dispatch ${configuration.workflowFile} on ${configuration.branch}",
            parametersHash = configurationHash(configuration),
            preconditionHash = configurationHash(configuration),
        )

        if (DefaultPolicy.requiresApproval(action, PermissionMode.SOME)) {
            val approval = Approval(
                approvalId = "direct:${System.currentTimeMillis()}",
                actionId = action.actionId,
                parametersHash = action.parametersHash,
                workspaceRevision = action.preconditionHash.orEmpty(),
                policyVersion = "v1",
                expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_WINDOW_MS,
            )
            if (approval.parametersHash != action.parametersHash || approval.expiresAtEpochMs <= System.currentTimeMillis()) {
                state = BuildState.Failed("Build dispatch approval is no longer valid. Review the build configuration and try again.")
                return
            }
        }

        val request = configuration
        state = BuildState.Dispatching(request)
        viewModelScope.launch(Dispatchers.IO) {
            val nextState = when (val result = githubGateway.dispatch(request.githubOwner, request.githubRepository, request)) {
                is com.mrredhood.devforge.core.github.GitHubDispatchResult.Started -> {
                    BuildState.Running(result.runId, request)
                }
                is com.mrredhood.devforge.core.github.GitHubDispatchResult.Failure -> {
                    BuildState.Failed(result.message)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                state = nextState
            }
        }
    }

    private fun refreshDispatchCapability() {
        val repositorySelected = configuration.githubOwner.isNotBlank() &&
            configuration.githubRepository.isNotBlank() &&
            configuration.workflowFile.isNotBlank()
        val credentialAvailable = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) != null
        val supportedTarget = configuration.target == BuildTarget.DebugApk

        capabilities = capabilities.copy(
            githubDispatch = when {
                !repositorySelected -> CapabilityAvailability.NotConfigured
                !credentialAvailable -> CapabilityAvailability.Unavailable
                !supportedTarget -> CapabilityAvailability.NotConfigured
                else -> CapabilityAvailability.Available
            },
        )
    }

    private fun dispatchUnavailableMessage(): String = when {
        configuration.githubOwner.isBlank() || configuration.githubRepository.isBlank() ->
            "Select and validate a GitHub repository before starting a remote build."
        secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) == null ->
            "Connect GitHub before starting a remote build."
        configuration.target != BuildTarget.DebugApk ->
            "The selected workflow currently supports the debug APK target only. Release dispatch inputs will be added with release workflow support."
        else -> "Remote workflow dispatch is not available for this configuration."
    }

    private fun configurationHash(value: BuildConfiguration): String {
        val input = listOf(
            value.githubOwner,
            value.githubRepository,
            value.workflowFile,
            value.branch,
            value.buildTask,
            value.artifactName,
            value.target.name,
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val APPROVAL_WINDOW_MS = 120_000L
    }
}
