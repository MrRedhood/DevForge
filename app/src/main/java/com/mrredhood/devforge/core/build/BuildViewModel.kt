package com.mrredhood.devforge.core.build

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.github.BuildHistoryEntry
import com.mrredhood.devforge.core.github.GitHubActionsGateway
import com.mrredhood.devforge.core.github.GitHubArtifactsResult
import com.mrredhood.devforge.core.github.GitHubConnectionViewModel
import com.mrredhood.devforge.core.github.GitHubDispatchResult
import com.mrredhood.devforge.core.github.GitHubLogsResult
import com.mrredhood.devforge.core.github.GitHubRunResult
import com.mrredhood.devforge.core.github.GitHubRunSnapshot
import com.mrredhood.devforge.core.github.GitHubArtifact
import com.mrredhood.devforge.core.github.GitHubJobLog
import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.Approval
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.AndroidSecretStore
import com.mrredhood.devforge.core.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class BuildViewModel(application: Application) : AndroidViewModel(application) {
    private val secretStore: SecretStore = AndroidSecretStore(application)
    private val githubGateway = GitHubActionsGateway.forBuildStore(secretStore)
    private var monitorJob: Job? = null

    var configuration by mutableStateOf(BuildConfiguration())
        private set

    var state by mutableStateOf<BuildState>(BuildState.Ready(configuration))
        private set

    var capabilities by mutableStateOf(BuildCapabilityState())
        private set

    var runSnapshot by mutableStateOf<GitHubRunSnapshot?>(null)
        private set

    var logs by mutableStateOf<List<GitHubJobLog>>(emptyList())
        private set

    var logsTruncated by mutableStateOf(false)
        private set

    var artifacts by mutableStateOf<List<GitHubArtifact>>(emptyList())
        private set

    var monitoringMessage by mutableStateOf<String?>(null)
        private set

    var history by mutableStateOf<List<BuildHistoryEntry>>(emptyList())
        private set

    fun selectTarget(target: BuildTarget) {
        if (state is BuildState.Dispatching || state is BuildState.Running) return
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
        if (state is BuildState.Dispatching || state is BuildState.Running) return
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
        if (state is BuildState.Dispatching || state is BuildState.Running) return
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
        if (state is BuildState.Dispatching || state is BuildState.Running) return
        monitoringMessage = null
        state = BuildState.Ready(configuration)
    }

    /** Refresh the current remote run and its logs/artifacts without starting another run. */
    fun refreshRun() {
        val run = runSnapshot ?: return
        startMonitoring(run.id, configuration, immediateOnly = true)
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

        monitorJob?.cancel()
        runSnapshot = null
        logs = emptyList()
        artifacts = emptyList()
        logsTruncated = false
        monitoringMessage = null

        val request = configuration
        state = BuildState.Dispatching(request)
        viewModelScope.launch(Dispatchers.IO) {
            val result = githubGateway.dispatch(request.githubOwner, request.githubRepository, request)
            withContext(Dispatchers.Main.immediate) {
                when (result) {
                    is GitHubDispatchResult.Started -> {
                        state = BuildState.Running(result.runId, request)
                        startMonitoring(result.runId, request, immediateOnly = false)
                    }
                    is GitHubDispatchResult.Failure -> {
                        state = BuildState.Failed(result.message)
                    }
                }
            }
        }
    }

    private fun startMonitoring(runId: Long, buildConfiguration: BuildConfiguration, immediateOnly: Boolean) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch(Dispatchers.IO) {
            do {
                val runResult = githubGateway.getRun(
                    buildConfiguration.githubOwner,
                    buildConfiguration.githubRepository,
                    runId,
                )

                val snapshot = when (runResult) {
                    is GitHubRunResult.Success -> runResult.run
                    is GitHubRunResult.Failure -> {
                        withContext(Dispatchers.Main.immediate) {
                            monitoringMessage = runResult.message
                            refreshMonitoringCapabilities(runAvailable = true)
                        }
                        break
                    }
                }

                val artifactsResult = githubGateway.listArtifacts(
                    buildConfiguration.githubOwner,
                    buildConfiguration.githubRepository,
                    runId,
                )
                val logsResult = githubGateway.fetchLogs(
                    buildConfiguration.githubOwner,
                    buildConfiguration.githubRepository,
                    runId,
                )

                withContext(Dispatchers.Main.immediate) {
                    runSnapshot = snapshot
                    when (artifactsResult) {
                        is GitHubArtifactsResult.Success -> artifacts = artifactsResult.artifacts
                        is GitHubArtifactsResult.Failure -> monitoringMessage = artifactsResult.message
                    }
                    when (logsResult) {
                        is GitHubLogsResult.Success -> {
                            logs = logsResult.jobs
                            logsTruncated = logsResult.truncated
                        }
                        is GitHubLogsResult.Failure -> monitoringMessage = logsResult.message
                    }
                    applyRunState(snapshot, buildConfiguration)
                    refreshMonitoringCapabilities(runAvailable = true)
                }

                if (immediateOnly || snapshot.status !in ACTIVE_RUN_STATUSES) break
                delay(POLL_INTERVAL_MS)
            } while (isActive)
        }
    }

    private fun applyRunState(snapshot: GitHubRunSnapshot, buildConfiguration: BuildConfiguration) {
        when (snapshot.status) {
            in ACTIVE_RUN_STATUSES -> {
                state = BuildState.Running(snapshot.id, buildConfiguration)
            }
            "completed" -> {
                state = when (snapshot.conclusion) {
                    "success" -> BuildState.Succeeded(snapshot.id, buildConfiguration.artifactName)
                    "cancelled", "timed_out" -> BuildState.Cancelled(snapshot.id)
                    else -> BuildState.Failed(
                        "GitHub Actions run #${snapshot.runNumber} finished with ${snapshot.conclusion ?: "an unknown conclusion"}.",
                    )
                }
                prependHistory(
                    BuildHistoryEntry(
                        runId = snapshot.id,
                        runNumber = snapshot.runNumber,
                        configuration = buildConfiguration,
                        state = snapshot.status,
                        conclusion = snapshot.conclusion,
                        htmlUrl = snapshot.htmlUrl,
                        updatedAt = snapshot.updatedAt,
                    ),
                )
            }
        }
    }

    private fun prependHistory(entry: BuildHistoryEntry) {
        history = listOf(entry) + history.filterNot { it.runId == entry.runId }.take(MAX_HISTORY - 1)
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
            liveLogs = if (credentialAvailable && repositorySelected && runSnapshot != null) {
                CapabilityAvailability.Available
            } else {
                CapabilityAvailability.Unavailable
            },
            artifacts = if (credentialAvailable && repositorySelected && runSnapshot != null) {
                CapabilityAvailability.Available
            } else {
                CapabilityAvailability.Unavailable
            },
        )
    }

    private fun refreshMonitoringCapabilities(runAvailable: Boolean) {
        val credentialAvailable = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) != null
        capabilities = capabilities.copy(
            liveLogs = if (credentialAvailable && runAvailable) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            artifacts = if (credentialAvailable && runAvailable) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
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

    override fun onCleared() {
        monitorJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val APPROVAL_WINDOW_MS = 120_000L
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_HISTORY = 20
        private val ACTIVE_RUN_STATUSES = setOf("queued", "in_progress", "waiting", "requested", "pending")
    }
}
