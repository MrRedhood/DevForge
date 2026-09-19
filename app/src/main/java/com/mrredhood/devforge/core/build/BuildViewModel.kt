package com.mrredhood.devforge.core.build

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import com.mrredhood.devforge.core.github.GitHubCancelResult
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
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.settings.DevForgeSettingsRepository
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
    private val secretStore: SecretStore = CredentialSecurityStore(application)
    private val githubGateway = GitHubActionsGateway.forBuildStore(secretStore)
    private val database = DevForgeDatabase.get(application)
    private val appSettings = DevForgeSettingsRepository(application)
    private val buildReceiptDao = database.buildReceiptDao()
    private val approvalRepository = ApprovalRepository(database.approvalDao())
    private var monitorJob: Job? = null
    private var approvalJob: Job? = null
    private var cancelApprovalJob: Job? = null

    var configuration by mutableStateOf(initialConfiguration())
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
    var downloadingArtifactId by mutableStateOf<Long?>(null)
        private set
    var artifactMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            buildReceiptDao.observeRecent(MAX_HISTORY).collect { receipts -> history = receipts.map(BuildReceiptEntity::toDomain) }
        }
        approvalJob = viewModelScope.launch(Dispatchers.IO) {
            approvalRepository.observeApproved("build-dispatch:").collect { approvals -> approvals.forEach { executeApprovedBuild(it) } }
        }
        cancelApprovalJob = viewModelScope.launch(Dispatchers.IO) {
            approvalRepository.observeApproved("build-cancel:").collect { approvals -> approvals.forEach { executeApprovedCancellation(it) } }
        }
    }

    fun selectTarget(target: BuildTarget) {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.Cancelling || state is BuildState.AwaitingApproval) return
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
        configuration = configuration.copy(githubOwner = owner, githubRepository = repository, branch = defaultBranch.ifBlank { "main" }, workflowFile = workflowFile)
        appSettings.setGithub(owner, repository, defaultBranch, workflowFile)
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun cancelRun() {
        val run = runSnapshot ?: return
        val request = configuration
        if (state !is BuildState.Running) return
        refreshMonitoringCapabilities(runAvailable = true)
        if (capabilities.cancelBuild != CapabilityAvailability.Available) {
            state = BuildState.Failed("Build cancellation is unavailable until a valid GitHub credential is available.")
            return
        }
        val action = ActionRequest(
            actionId = "build-cancel:" + run.id,
            capability = Capability.CANCEL_BUILD,
            risk = RiskLevel.R2,
            workspaceId = "github:" + request.githubOwner + "/" + request.githubRepository,
            summary = "Cancel GitHub Actions run #" + run.runNumber + ".",
            parametersHash = cancellationHash(request.githubOwner, request.githubRepository, run.id),
            preconditionHash = runPreconditionHash(run),
        )
        if (DefaultPolicy.requiresApproval(action, PermissionMode.SOME)) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    approvalRepository.createPending(
                        approvalId = action.actionId,
                        actionId = action.actionId,
                        capability = action.capability,
                        risk = action.risk,
                        workspaceId = action.workspaceId,
                        summary = action.summary,
                        parametersHash = action.parametersHash,
                        preconditionHash = action.preconditionHash,
                        payload = JSONObject()
                            .put("owner", request.githubOwner)
                            .put("repository", request.githubRepository)
                            .put("runId", run.id)
                            .toString(),
                        expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_WINDOW_MS,
                    )
                }.onSuccess {
                    withContext(Dispatchers.Main.immediate) {
                        monitoringMessage = "Cancellation request sent to Approval Center."
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main.immediate) {
                        state = BuildState.Failed(error.message ?: "Unable to create the build cancellation approval request.")
                    }
                }
            }
            return
        }
        executeCancellation(request.githubOwner, request.githubRepository, run.id, null)
    }

    fun resetToReady() {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.Cancelling || state is BuildState.AwaitingApproval) return
        monitoringMessage = null
        state = BuildState.Ready(configuration)
    }

    fun refreshRun() {
        val run = runSnapshot ?: return
        startMonitoring(run.id, configuration, immediateOnly = true)
    }

    fun downloadArtifact(artifact: GitHubArtifact) {
        if (artifact.expired || artifact.id <= 0L) {
            artifactMessage = "This artifact is expired and cannot be downloaded."
            return
        }
        if (downloadingArtifactId != null) return
        val owner = configuration.githubOwner
        val repository = configuration.githubRepository
        if (owner.isBlank() || repository.isBlank()) {
            artifactMessage = "Select a GitHub repository before downloading artifacts."
            return
        }
        downloadingArtifactId = artifact.id
        artifactMessage = null
        viewModelScope.launch(Dispatchers.IO) {
            var targetUri: android.net.Uri? = null
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw IllegalStateException("Artifact downloads require Android 10 or newer.")
                }
                val resolver = getApplication<Application>().contentResolver
                val safeName = artifact.name.trim()
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                    .take(120)
                    .ifBlank { "artifact" }
                val fileName = if (safeName.endsWith(".zip", true)) safeName else safeName + ".zip"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DevForge")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Android could not create the DevForge download file.")
                resolver.openOutputStream(targetUri!!)!!.use { output ->
                    val result = githubGateway.downloadArtifact(
                        owner = owner,
                        repository = repository,
                        artifactId = artifact.id,
                        output = output,
                    )
                    val bytes = result.getOrElse { throw it }
                    if (bytes <= 0L) throw IllegalStateException("GitHub returned an empty artifact.")
                }
                val complete = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(targetUri!!, complete, null, null)
                withContext(Dispatchers.Main.immediate) {
                    artifactMessage = "Downloaded " + artifact.name + " to Downloads/DevForge."
                }
            } catch (error: Throwable) {
                targetUri?.let { uri ->
                    runCatching { getApplication<Application>().contentResolver.delete(uri, null, null) }
                }
                withContext(Dispatchers.Main.immediate) {
                    artifactMessage = error.message ?: "Artifact download failed."
                }
            } finally {
                withContext(Dispatchers.Main.immediate) { downloadingArtifactId = null }
            }
        }
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
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    approvalRepository.createPending(
                        approvalId = action.actionId,
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
                        state = BuildState.AwaitingApproval(action.actionId, request)
                        monitoringMessage = "Build request sent to Approval Center."
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main.immediate) { state = BuildState.Failed(error.message ?: "Unable to create the build approval request.") }
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
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main.immediate) {
                runSnapshot = null
                logs = emptyList()
                artifacts = emptyList()
                logsTruncated = false
                monitoringMessage = null
                state = BuildState.Dispatching(request)
            }
            when (val credential = githubGateway.validateCredential()) {
            is com.mrredhood.devforge.core.github.GitHubCredentialValidation.Valid -> Unit
            is com.mrredhood.devforge.core.github.GitHubCredentialValidation.Invalid -> {
                if (approvalId != null) approvalRepository.finishFailure(approvalId)
                withContext(Dispatchers.Main.immediate) {
                    state = BuildState.Failed("GitHub credential validation failed: " + credential.message)
                    monitoringMessage = "Live credential validation failed before remote build dispatch."
                }
                return@launch
            }
        }
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
                    withContext(Dispatchers.Main.immediate) { state = BuildState.Failed(result.message) }
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
                delay(appSettings.snapshot().buildPollSeconds * 1_000L)
            } while (isActive)
        }
    }

    private fun applyRunState(snapshot: GitHubRunSnapshot, buildConfiguration: BuildConfiguration) {
        when (snapshot.status) {
            in ACTIVE_RUN_STATUSES -> if (state !is BuildState.Cancelling) state = BuildState.Running(snapshot.id, buildConfiguration)
            "completed" -> {
                state = when (snapshot.conclusion) {
                    "success" -> BuildState.Succeeded(snapshot.id, buildConfiguration.artifactName)
                    "cancelled", "timed_out" -> BuildState.Cancelled(snapshot.id)
                    else -> BuildState.Failed("GitHub Actions run #${snapshot.runNumber} finished with ${snapshot.conclusion ?: "an unknown conclusion"}.")
                }
                val entry = BuildHistoryEntry(snapshot.id, snapshot.runNumber, buildConfiguration, snapshot.status, snapshot.conclusion, snapshot.htmlUrl, snapshot.updatedAt, System.currentTimeMillis())
                viewModelScope.launch(Dispatchers.IO) { buildReceiptDao.record(entry.toEntity(), MAX_HISTORY) }
            }
        }
    }

    private fun refreshDispatchCapability() {
        val repositorySelected = configuration.githubOwner.isNotBlank() && configuration.githubRepository.isNotBlank() && configuration.workflowFile.isNotBlank()
        val credentialAvailable = runCatching { !secretStore.get(GitHubConnectionViewModel.TOKEN_KEY).isNullOrBlank() }.getOrDefault(false)
        val workflowContractSelected = configuration.workflowFile.trim().substringAfterLast('/') == GitHubActionsGateway.TARGET_CONTRACT_WORKFLOW
        capabilities = capabilities.copy(
            cancelBuild = if (credentialAvailable && runSnapshot != null && configuration.githubOwner.isNotBlank() && configuration.githubRepository.isNotBlank()) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
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
        val credentialAvailable = runCatching { secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) != null }.getOrDefault(false)
        capabilities = capabilities.copy(
            cancelBuild = if (credentialAvailable && runAvailable) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            liveLogs = if (credentialAvailable && runAvailable) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            artifacts = if (credentialAvailable && runAvailable) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
        )
    }

    private suspend fun executeApprovedCancellation(approval: ApprovalEntity) {
        if (approval.expiresAtEpochMs <= System.currentTimeMillis()) {
            approvalRepository.expireDue()
            return
        }
        if (!approvalRepository.claimApproved(approval.approvalId)) return
        val payload = runCatching { JSONObject(approval.payload) }.getOrNull()
        val owner = payload?.optString("owner").orEmpty()
        val repository = payload?.optString("repository").orEmpty()
        val runId = payload?.optLong("runId", -1L) ?: -1L
        if (owner.isBlank() || repository.isBlank() || runId <= 0L ||
            cancellationHash(owner, repository, runId) != approval.parametersHash
        ) {
            approvalRepository.finishFailure(approval.approvalId)
            return
        }
        withContext(Dispatchers.Main.immediate) {
            state = BuildState.Cancelling(runId, configuration)
            monitoringMessage = "Approved cancellation is being sent to GitHub."
        }
        executeCancellation(owner, repository, runId, approval.approvalId)
    }

    private fun executeCancellation(owner: String, repository: String, runId: Long, approvalId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main.immediate) {
                state = BuildState.Cancelling(runId, configuration)
            }
            val result = githubGateway.cancelRun(owner, repository, runId)
            when (result) {
                is GitHubCancelResult.Accepted -> {
                    if (approvalId != null) approvalRepository.finishSuccess(approvalId)
                    withContext(Dispatchers.Main.immediate) {
                        monitoringMessage = "GitHub accepted the cancellation request."
                        startMonitoring(runId, configuration, immediateOnly = false)
                    }
                }
                GitHubCancelResult.Conflict -> {
                    if (approvalId != null) approvalRepository.finishFailure(approvalId)
                    withContext(Dispatchers.Main.immediate) {
                        monitoringMessage = "GitHub reports that this workflow run cannot be cancelled in its current state."
                        startMonitoring(runId, configuration, immediateOnly = true)
                    }
                }
                is GitHubCancelResult.Failure -> {
                    if (approvalId != null) approvalRepository.finishFailure(approvalId)
                    withContext(Dispatchers.Main.immediate) {
                        state = BuildState.Failed(result.message)
                    }
                }
            }
        }
    }

    private fun runPreconditionHash(run: GitHubRunSnapshot): String =
        MessageDigest.getInstance("SHA-256")
            .digest((run.id.toString() + "\u0000" + run.status + "\u0000" + (run.updatedAt ?: "")).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun cancellationHash(owner: String, repository: String, runId: Long): String {
        val input = listOf(owner.trim(), repository.trim(), runId.toString()).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun dispatchUnavailableMessage(): String = when {
        configuration.githubOwner.isBlank() || configuration.githubRepository.isBlank() -> "Select and validate a GitHub repository before starting a remote build."
        runCatching { secretStore.get(GitHubConnectionViewModel.TOKEN_KEY) == null }.getOrDefault(true) -> "Connect GitHub or unlock protected credentials before starting a remote build."
        configuration.workflowFile.trim().substringAfterLast('/') != GitHubActionsGateway.TARGET_CONTRACT_WORKFLOW -> "Select the DevForge Android workflow that declares the fixed debug/release target contract."
        else -> "Remote workflow dispatch is not available for this configuration."
    }

    private fun configurationHash(value: BuildConfiguration): String {
        val input = listOf(value.githubOwner, value.githubRepository, value.workflowFile, value.branch, value.buildTask, value.artifactName, value.target.name).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun encodeConfiguration(value: BuildConfiguration): String = JSONObject().put("owner", value.githubOwner).put("repository", value.githubRepository).put("workflow", value.workflowFile).put("branch", value.branch).put("task", value.buildTask).put("artifact", value.artifactName).put("target", value.target.name).toString()

    private fun decodeConfiguration(payload: String): BuildConfiguration? = runCatching {
        val json = JSONObject(payload)
        BuildConfiguration(
            githubOwner = json.getString("owner"),
            githubRepository = json.getString("repository"),
            workflowFile = json.getString("workflow"),
            branch = json.getString("branch"),
            buildTask = json.getString("task"),
            artifactName = json.getString("artifact"),
            target = BuildTarget.valueOf(json.getString("target")),
        )
    }.getOrNull()

    override fun onCleared() {
        monitorJob?.cancel()
        approvalJob?.cancel()
        cancelApprovalJob?.cancel()
        super.onCleared()
    }

    private fun initialConfiguration(): BuildConfiguration {
        val saved = appSettings.snapshot()
        return BuildConfiguration(
            githubOwner = saved.githubOwner,
            githubRepository = saved.githubRepository,
            workflowFile = saved.githubWorkflow.ifBlank { ".github/workflows/android.yml" },
            branch = saved.githubBranch.ifBlank { "main" },
        )
    }

    companion object {
        private const val APPROVAL_WINDOW_MS = 120_000L
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_HISTORY = 20
        private val ACTIVE_RUN_STATUSES = setOf("queued", "in_progress", "waiting", "requested", "pending")
    }
}
