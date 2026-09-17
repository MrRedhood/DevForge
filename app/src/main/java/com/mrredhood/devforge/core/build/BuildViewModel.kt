package com.mrredhood.devforge.core.build

import android.app.Application
import org.json.JSONObject
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
import com.mrredhood.devforge.core.github.GitHubJobLog
import com.mrredhood.devforge.core.github.GitHubLogsResult
import com.mrredhood.devforge.core.github.GitHubRunResult
import com.mrredhood.devforge.core.github.GitHubRunSnapshot
import com.mrredhood.devforge.core.github.GitHubArtifact
import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.AndroidSecretStore
import com.mrredhood.devforge.core.security.SecretStore
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.BuildReceiptEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.toDomain
import com.mrredhood.devforge.core.storage.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class BuildViewModel(application: Application) : AndroidViewModel(application) {
    private val secretStore: SecretStore = AndroidSecretStore(application)
    private val githubGateway = GitHubActionsGateway.forBuildStore(secretStore)
    private val database = DevForgeDatabase.get(application)
    private val buildReceiptDao = database.buildReceiptDao()
    private val approvalRepository = ApprovalRepository(database.approvalDao())
    private var monitorJob: Job? = null
    private var approvalJob: Job? = null

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

    init {
        viewModelScope.launch {
            buildReceiptDao.observeRecent(MAX_HISTORY).collect { receipts ->
                history = receipts.map(BuildReceiptEntity::toDomain)
            }
        }
        approvalJob = viewModelScope.launch(Dispatchers.IO) {
            approvalRepository.observeApproved("build-dispatch:").collect { approvals ->
                approvals.forEach { executeApprovedBuild(it) }
            }
        }
    }

    fun selectTarget(target: BuildTarget) {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.AwaitingApproval) return
        configuration = when (target) {
            BuildTarget.DebugApk -> configuration.copy(target = target, buildTask = ":app:assembleDebug", artifactName = "devforge-debug-apk")
            BuildTarget.ReleaseApk -> configuration.copy(target = target, buildTask = ":app:assembleRelease", artifactName = "devforge-release-apk")
            BuildTarget.ReleaseBundle -> configuration.copy(target = target, buildTask = ":app:bundleRelease", artifactName = "devforge-release-aab")
        }
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun updateBranch(branch: String) {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.AwaitingApproval) return
        configuration = configuration.copy(branch = branch.ifBlank { "main" })
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun configureGitHubRepository(owner: String, repository: String, defaultBranch: String, workflowFile: String) {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.AwaitingApproval) return
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

    fun refreshRun() {
        val run = runSnapshot ?: return
        startMonitoring(run.id, configuration, immediateOnly = true)
    }

    fun requestDispatch() {
        refreshDispatchCapability()
        if (capabilities.githubDispatch != CapabilityAvailability.Available) {
            state = BuildState.Failed(dispatchUnavailableMessage())
            return
        }

        val request = configuration
        val action = ActionRequest(
            actionId = "build-dispatch:${System.currentTimeMillis()}",
            capability = Capability.DISPATCH_BUILD,
            risk = RiskLevel.R2,
            workspaceId = "github:${request.githubOwner}/${request.githubRepository}",
            summary = "Dispatch ${request.workflowFile} on ${request.branch} (${request.target.workflowInput})",
            parametersHash = configurationHash(request),
            preconditionHash = configurationHash(request),
        )

        if (DefaultPolicy.requiresApproval(action, PermissionMode.SOME)) {
            val approvalId = action.actionId
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    approvalRepository.createPending(
                        approvalId = approvalId,
                        actionId = action.actionId,
                        capability = action.capability,
                        risk = action.risk,
                        workspaceId = action.workspaceId,
                        summary = action.summary,
                        parametersHash = action.parametersHash,
                        preconditionHash = action.preconditionHash,
                        payload = encodeConfiguration(request),
                        expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_WINDOW_MS,
                    )
                }.onSuccess {
                    withContext(Dispatchers.Main.immediate) {
                        state = BuildState.AwaitingApproval(approvalId, request)
                        monitoringMessage = "Build request sent to Approval Center."
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main.immediate) {
                        state = BuildState.Failed(error.message ?: "Unable to create the build approval request.")
                    }
                }
            }
            return
        }

        executeDispatch(request, null)
    }

    private suspend fun executeApprovedBuild(approval: ApprovalEntity) {
        if (approval.expiresAtEpochMs <= System.currentTimeMillis()) {
            approvalRepository.expireDue()
            return
        }
        if (!approvalRepository.claimApproved(approval.approvalId)) return
        val request = decodeConfiguration(approval.payload)
        if (request == null || configurationHash(request) != approval.parametersHash) {
            approvalRepository.finishFailure(approval.approvalId)
            return
        }
        withContext(Dispatchers.Main.immediate) {
            configuration = request
            state = BuildState.Dispatching(request)
            monitoringMessage = "Approved build is being dispatched."
        }
        executeDispatch(request, approval.approvalId)
    }

    private fun executeDispatch(request: BuildConfiguration, approvalId: String?) {
        monitorJob?.cancel()
        runSnapshot = null
        logs = emptyList()
        artifacts = emptyList()
        logsTruncated = false
        monitoringMessage = null
        state = BuildState.Dispatching(request)
        viewModelScope.launch(Dispatchers.IO) {
            val result = githubGateway.dispatch(request.githubOwner, request.githubRepository, request)
            when (result) {
                is GitHubDispatchResult.Started -> {
                    if (approvalId != null) approvalRepository.finishSuccess(approvalId)
                    withContext(Dispatchers.Main.immediate) {
                        state = BuildState.Running(result.runId, request)
                        startMonitoring(result.runId, request, immediateOnly = false)
                    }
                }
                is GitHubDispatchResult.Failure -> {
                    if (approvalId != null) approvalRepository.finishFailure(approvalId)
                    withContext(Dispatchers.Main.immediate) {
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
                val runResult = githubGateway.getRun(buildConfiguration.githubOwner, buildConfiguration.githubRepository, runId)
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
                val artifactsResult = githubGateway.listArtifacts(buildConfiguration.githubOwner, buildConfiguration.githubRepository, runId)
                val logsResult = githubGateway.fetchLogs(buildConfiguration.githubOwner, buildConfiguration.githubRepository, runId)
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
            in ACTIVE_RUN_STATUSES -> state = BuildState.Running(snapshot.id, buildConfiguration)
            "completed" -> {
                state = when (snapshot.conclusion) {
                    "success" -> BuildState.Succeeded(snapshot.id, buildConfiguration.artifactName)
                    "cancelled", "timed_out" -> BuildState.Cancelled(snapshot.id)
                    else -> BuildState.Failed("GitHub Actions run #${snapshot.runNumber} finished with ${snapshot.conclusion ?: "an unknown conclusion"}.")
                }
                val entry = BuildHistoryEntry(
                    runId = snapshot.id,
                    runNumber = snapshot.runNumber,
                    configuration = buildConfiguration,
                    state = snapshot.status,
                    conclusion = snapshot.conclusion,
                    htmlUrl = snapshot.htmlUrl,
                    updatedAt = snapshot.updatedAt,
                    recordedAtEpochMs = System.currentTimeMillis(),
                )
                viewModelScope.launch(Dispatchers.IO) { buildReceiptDao.record(entry.toEntity(), MAX_HISTORY) }
            }
        }
    }

    private fun refreshDispatchCapability() {
        val repositorySelected = configuration.githubOwner.isNotBlank() && configuration.githubRepository.isNotBlank() && configuration.workflowFile.isNotBlank()
        val credentialAvailable = secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) != null
        val workflowContractSelected = configuration.workflowFile.trim().substringAfterLast('/') == GitHubActionsGateway.TARGET_CONTRACT_WORKFLOW
        capabilities = capabilities.copy(
            githubDispatch = when {
                !repositorySelected -> CapabilityAvailability.NotConfigured
                !credentialAvailable -> CapabilityAvailability.Unavailable
                !workflowContractSelected -> CapabilityAvailability.NotConfigured
                else -> CapabilityAvailability.Available
            },
            liveLogs = if (credentialAvailable && repositorySelected && runSnapshot != null) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            artifacts = if (credentialAvailable && repositorySelected && runSnapshot != null) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
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
        configuration.githubOwner.isBlank() || configuration.githubRepository.isBlank() -> "Select and validate a GitHub repository before starting a remote build."
        secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) == null -> "Connect GitHub before starting a remote build."
        configuration.workflowFile.trim().substringAfterLast('/') != GitHubActionsGateway.TARGET_CONTRACT_WORKFLOW -> "Select the DevForge Android workflow that declares the fixed debug/release target contract."
        else -> "Remote workflow dispatch is not available for this configuration."
    }

    private fun configurationHash(value: BuildConfiguration): String {
        val input = listOf(value.githubOwner, value.githubRepository, value.workflowFile, value.branch, value.buildTask, value.artifactName, value.target.name).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun encodeConfiguration(value: BuildConfiguration): String = JSONObject()
        .put("owner", value.githubOwner)
        .put("repository", value.githubRepository)
        .put("workflow", value.workflowFile)
        .put("branch", value.branch)
        .put("task", value.buildTask)
        .put("artifact", value.artifactName)
        .put("target", value.target.name)
        .toString()

    private fun decodeConfiguration(payload: String): BuildConfiguration? = runCatching {
        val json = JSONObject(payload)
        val target = BuildTarget.valueOf(json.getString("target"))
        BuildConfiguration(
            githubOwner = json.getString("owner"),
            githubRepository = json.getString("repository"),
            workflowFile = json.getString("workflow"),
            branch = json.getString("branch"),
            buildTask = json.getString("task"),
            artifactName = json.getString("artifact"),
            target = target,
        )
    }.getOrNull()

    override fun onCleared() {
        monitorJob?.cancel()
        approvalJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val APPROVAL_WINDOW_MS = 120_000L
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_HISTORY = 20
        private val ACTIVE_RUN_STATUSES = setOf("queued", "in_progress", "waiting", "requested", "pending")
    }
}
