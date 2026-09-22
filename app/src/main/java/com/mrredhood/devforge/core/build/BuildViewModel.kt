package com.mrredhood.devforge.core.build

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.github.BuildHistoryEntry
import com.mrredhood.devforge.core.github.GitHubActionsGateway
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.github.GitHubTreeChange
import com.mrredhood.devforge.core.github.GitHubWorkflowRun
import com.mrredhood.devforge.core.github.GitHubArtifactsResult
import com.mrredhood.devforge.core.github.GitHubConnectionViewModel
import com.mrredhood.devforge.core.github.GitHubCancelResult
import com.mrredhood.devforge.core.github.GitHubDispatchResult
import com.mrredhood.devforge.core.github.GitHubJobLog
import com.mrredhood.devforge.core.github.GitHubLogsResult
import com.mrredhood.devforge.core.github.GitHubRunResult
import com.mrredhood.devforge.core.github.GitHubRunSnapshot
import com.mrredhood.devforge.core.notification.DevForgeActivityNotificationManager
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
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.quality.DevForgeOperationCenter
import com.mrredhood.devforge.core.quality.DevForgeOperationType
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
    private val githubRepositoryGateway = GitHubRepositoryGateway(secretStore)
    private val database = DevForgeDatabase.get(application)
    private val appSettings = DevForgeSettingsRepository(application)
    private val buildSettingsStore = GitHubBuildSettingsStore(application)
    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val githubWorkspaceStore = GitHubWorkspaceStore(application)
    private val buildReceiptDao = database.buildReceiptDao()
    private val approvalRepository = ApprovalRepository(database.approvalDao())
    private var monitorJob: Job? = null
    private var approvalJob: Job? = null
    private var operationId: String? = null
    private var cancelApprovalJob: Job? = null
    private var workspaceJob: Job? = null

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
    var workflowSetupMessage by mutableStateOf<String?>(null)
        private set
    var workflowSetupBusy by mutableStateOf(false)
        private set
    var workflowAvailable by mutableStateOf(false)
        private set
    var runningWorkflows by mutableStateOf<List<GitHubWorkflowRun>>(emptyList())
        private set
    var ciHealth by mutableStateOf<CiHealthSnapshot?>(null)
        private set

    init {
        viewModelScope.launch {
            buildReceiptDao.observeRecent(MAX_HISTORY).collect { receipts ->
                history = receipts.map(BuildReceiptEntity::toDomain)
                receipts.firstOrNull()?.let { restoreLatestRun(it.toDomain()) }
            }
        }
        approvalJob = viewModelScope.launch(Dispatchers.IO) {
            approvalRepository.observeApproved("build-dispatch:").collect { approvals -> approvals.forEach { executeApprovedBuild(it) } }
        }
        cancelApprovalJob = viewModelScope.launch(Dispatchers.IO) {
            approvalRepository.observeApproved("build-cancel:").collect { approvals -> approvals.forEach { executeApprovedCancellation(it) } }
        }
        workspaceJob = viewModelScope.launch {
            workspaceRepository.activeWorkspace.collect { workspace ->
                val remote = workspace?.let { githubWorkspaceStore.get(it.id) }
                if (remote == null) return@collect
                if (configuration.githubOwner != remote.owner ||
                    configuration.githubRepository != remote.repository ||
                    configuration.branch != remote.branch
                ) {
                    val output = buildSettingsStore.get(remote.owner, remote.repository)
                    configuration = configuration.copy(
                        githubOwner = remote.owner,
                        githubRepository = remote.repository,
                        branch = remote.branch,
                        workflowFile = configuration.workflowFile.ifBlank { ".github/workflows/android.yml" },
                        buildArtifact = output.buildArtifact,
                        publishArtifacts = output.publishArtifacts,
                        lintReport = output.lintReport,
                        unitTestReport = output.unitTestReport,
                        dependencyReport = output.dependencyReport,
                    ).withTarget(output.target)
                    appSettings.setGithub(
                        remote.owner,
                        remote.repository,
                        remote.branch,
                        configuration.workflowFile,
                    )
                    if (state !is BuildState.Dispatching &&
                        state !is BuildState.Running &&
                        state !is BuildState.Cancelling &&
                        state !is BuildState.AwaitingApproval
                    ) {
                        state = BuildState.Ready(configuration)
                    }
                    validateConfiguredWorkflow()
                }
            }
        }
        validateConfiguredWorkflow()
        viewModelScope.launch {
            while (isActive) {
                refreshRunningWorkflows()
                delay(15_000L)
            }
        }
    }

    fun refreshRunningWorkflows() {
        val owner = configuration.githubOwner.trim()
        val repository = configuration.githubRepository.trim()
        if (owner.isBlank() || repository.isBlank()) {
            runningWorkflows = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = githubGateway.listRecentRuns(owner, repository, null, 30)) {
                is com.mrredhood.devforge.core.github.GitHubWorkflowRunsResult.Success ->
                    withContext(Dispatchers.Main.immediate) {
                        runningWorkflows = result.runs.filter {
                            it.status in setOf("queued", "in_progress", "requested", "waiting", "pending")
                        }.take(20)
                        ciHealth = CiHealthAggregator.summarize(result.runs)
                    }
                is com.mrredhood.devforge.core.github.GitHubWorkflowRunsResult.Failure -> Unit
            }
        }
    }

    fun createCiWorkflow() = createWorkflow("CI", ".github/workflows/android.yml", BuildWorkflowTemplates.ci)
    fun createUiWorkflow() = createWorkflow("UI", ".github/workflows/ui.yml", BuildWorkflowTemplates.ui)
    fun createReleaseWorkflow() = createWorkflow("Release", ".github/workflows/release-validation.yml", BuildWorkflowTemplates.release)

    private fun createWorkflow(label: String, path: String, content: String) {
        val owner = configuration.githubOwner.trim()
        val repository = configuration.githubRepository.trim()
        val branch = configuration.branch.trim().ifBlank { "main" }
        if (owner.isBlank() || repository.isBlank()) {
            workflowSetupMessage = "Choose a GitHub repository first."
            return
        }
        if (workflowSetupBusy) return
        workflowSetupBusy = true
        workflowSetupMessage = "Creating " + label + " workflow…"
        viewModelScope.launch(Dispatchers.IO) {
            val result = githubRepositoryGateway.commitChanges(
                owner = owner,
                repository = repository,
                branch = branch,
                message = "DevForge: add " + label + " workflow",
                changes = listOf(GitHubTreeChange(path = path, content = content)),
            )
            withContext(Dispatchers.Main.immediate) {
                workflowSetupBusy = false
                workflowSetupMessage = when (result) {
                    is com.mrredhood.devforge.core.github.GitHubCommitResult.Success -> {
                        if (path == configuration.workflowFile) {
                            workflowAvailable = true
                            refreshDispatchCapability()
                        }
                        label + " workflow committed to " + branch + " (" + result.commitSha.take(12) + ")."
                    }
                    is com.mrredhood.devforge.core.github.GitHubCommitResult.Failure -> result.message
                }
            }
        }
    }

    fun startSelectedBuild() {
        if (state is BuildState.Dispatching ||
            state is BuildState.Running ||
            state is BuildState.Cancelling ||
            state is BuildState.AwaitingApproval
        ) return
        if (!configuration.buildArtifact) {
            state = BuildState.Failed("Building the selected APK/AAB is disabled for this repository. Enable it in repository build output settings first.")
            return
        }
        requestDispatch()
    }

    fun startReleaseApkCi() {
        startBuild(BuildTarget.ReleaseApk)
    }

    fun startBuild(target: BuildTarget) {
        if (state is BuildState.Dispatching ||
            state is BuildState.Running ||
            state is BuildState.Cancelling ||
            state is BuildState.AwaitingApproval
        ) return
        configuration = configuration
            .copy(workflowFile = ".github/workflows/android.yml")
            .withTarget(target)
        persistBuildOutputSettings()
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
        startSelectedBuild()
    }

    fun selectTarget(target: BuildTarget) {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.Cancelling || state is BuildState.AwaitingApproval) return
        configuration = configuration.withTarget(target)
        persistBuildOutputSettings()
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun updateBranch(branch: String) {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.AwaitingApproval) return
        configuration = configuration.copy(branch = branch.ifBlank { "main" })
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun configureGitHubRepository(
        owner: String,
        repository: String,
        defaultBranch: String,
        workflowFile: String,
        outputSettings: BuildOutputSettings = buildSettingsStore.get(owner, repository),
    ) {
        if (state is BuildState.Dispatching || state is BuildState.Running || state is BuildState.AwaitingApproval) return
        buildSettingsStore.set(owner, repository, outputSettings)
        configuration = configuration.copy(
            githubOwner = owner,
            githubRepository = repository,
            branch = defaultBranch.ifBlank { "main" },
            workflowFile = workflowFile,
            buildArtifact = outputSettings.buildArtifact,
            publishArtifacts = outputSettings.publishArtifacts,
            lintReport = outputSettings.lintReport,
            unitTestReport = outputSettings.unitTestReport,
            dependencyReport = outputSettings.dependencyReport,
        ).withTarget(outputSettings.target)
        workflowAvailable = false
        appSettings.setGithub(owner, repository, defaultBranch, workflowFile)
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
        validateConfiguredWorkflow()
    }

    fun updateBuildOutputSettings(settings: BuildOutputSettings) {
        val owner = configuration.githubOwner.trim()
        val repository = configuration.githubRepository.trim()
        if (owner.isBlank() || repository.isBlank()) return
        buildSettingsStore.set(owner, repository, settings)
        configuration = configuration.copy(
            buildArtifact = settings.buildArtifact,
            publishArtifacts = settings.publishArtifacts,
            lintReport = settings.lintReport,
            unitTestReport = settings.unitTestReport,
            dependencyReport = settings.dependencyReport,
        ).withTarget(settings.target)
        refreshDispatchCapability()
        state = BuildState.Ready(configuration)
    }

    fun currentBuildOutputSettings(): BuildOutputSettings = BuildOutputSettings(
        buildArtifact = configuration.buildArtifact,
        publishArtifacts = configuration.publishArtifacts,
        lintReport = configuration.lintReport,
        unitTestReport = configuration.unitTestReport,
        dependencyReport = configuration.dependencyReport,
        target = configuration.target,
    )

    private fun persistBuildOutputSettings() {
        val owner = configuration.githubOwner.trim()
        val repository = configuration.githubRepository.trim()
        if (owner.isBlank() || repository.isBlank()) return
        buildSettingsStore.set(owner, repository, currentBuildOutputSettings())
    }

    fun applyBuildOutputSettingsToWorkflow() {
        val owner = configuration.githubOwner.trim()
        val repository = configuration.githubRepository.trim()
        if (owner.isBlank() || repository.isBlank()) {
            workflowSetupMessage = "Choose a GitHub repository first."
            return
        }
        createCiWorkflow()
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
        val run = runSnapshot
        if (run != null) {
            startMonitoring(run.id, configuration, immediateOnly = true)
        } else {
            history.firstOrNull()?.let { restoreLatestRun(it) }
        }
    }

    fun reloadLogs() {
        val run = runSnapshot ?: return
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = githubGateway.fetchLogs(
                configuration.githubOwner,
                configuration.githubRepository,
                run.id,
                maxJobs = 8,
                maxBytes = 512 * 1024,
            )) {
                is GitHubLogsResult.Success -> withContext(Dispatchers.Main.immediate) {
                    logs = result.jobs
                    logsTruncated = result.truncated
                    monitoringMessage = null
                }
                is GitHubLogsResult.Failure -> withContext(Dispatchers.Main.immediate) {
                    monitoringMessage = result.message
                }
            }
        }
    }

    private var restoringRunId: Long? = null

    private fun restoreLatestRun(entry: BuildHistoryEntry) {
        if (entry.runId <= 0L || restoringRunId == entry.runId) return
        restoringRunId = entry.runId
        configuration = entry.configuration
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = githubGateway.getRun(
                    entry.configuration.githubOwner,
                    entry.configuration.githubRepository,
                    entry.runId,
                )) {
                    is GitHubRunResult.Success -> {
                        val run = result.run
                        withContext(Dispatchers.Main.immediate) {
                            runSnapshot = run
                            applyRunState(run, entry.configuration)
                            refreshMonitoringCapabilities(runAvailable = true)
                        }
                        when (val result = githubGateway.listArtifacts(
                            entry.configuration.githubOwner,
                            entry.configuration.githubRepository,
                            entry.runId,
                        )) {
                            is GitHubArtifactsResult.Success -> withContext(Dispatchers.Main.immediate) { artifacts = result.artifacts }
                            is GitHubArtifactsResult.Failure -> Unit
                        }
                        when (val result = githubGateway.fetchLogs(
                            entry.configuration.githubOwner,
                            entry.configuration.githubRepository,
                            entry.runId,
                            maxJobs = 8,
                            maxBytes = 512 * 1024,
                        )) {
                            is GitHubLogsResult.Success -> withContext(Dispatchers.Main.immediate) {
                                logs = result.jobs
                                logsTruncated = result.truncated
                            }
                            is GitHubLogsResult.Failure -> withContext(Dispatchers.Main.immediate) {
                                monitoringMessage = result.message
                            }
                        }
                    }
                    is GitHubRunResult.Failure -> withContext(Dispatchers.Main.immediate) {
                        monitoringMessage = result.message
                    }
                }
            } finally {
                // Keep the restored id as the guard for the current latest history entry.
                // A Room emission must not cause a repeated network reload of the same run.
            }
        }
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
            var tempZip: File? = null
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw IllegalStateException("Artifact downloads require Android 10 or newer.")
                }
                val context = getApplication<Application>()
                tempZip = File.createTempFile("devforge-artifact-", ".zip", context.cacheDir)
                FileOutputStream(tempZip).use { output ->
                    val result = githubGateway.downloadArtifact(
                        owner = owner,
                        repository = repository,
                        artifactId = artifact.id,
                        output = output,
                    )
                    val bytes = result.getOrElse { throw it }
                    if (bytes <= 0L) throw IllegalStateException("GitHub returned an empty artifact.")
                }

                val resolver = context.contentResolver
                val artifactFolder = artifact.name.trim()
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                    .take(100)
                    .ifBlank { "artifact" }
                var extracted = 0
                var extractedBytes = 0L
                ZipInputStream(tempZip.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        if (extracted >= MAX_ARTIFACT_FILES) {
                            throw IllegalStateException("The GitHub artifact contains too many files.")
                        }
                        val declaredSize = entry.size
                        if (declaredSize > MAX_ARTIFACT_FILE_BYTES) {
                            throw IllegalStateException("The GitHub artifact contains an oversized file.")
                        }
                        val rawName = entry.name.replace('\\', '/')
                        if (rawName.startsWith("/") || rawName.split('/').any { it == ".." }) {
                            zip.closeEntry()
                            continue
                        }
                        val safeFileName = rawName.substringAfterLast('/')
                            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                            .take(140)
                            .ifBlank { "artifact-file-$extracted" }
                        val extension = safeFileName.substringAfterLast('.', "").lowercase()
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                            ?: when (extension) {
                                "aab" -> "application/octet-stream"
                                "apk" -> "application/vnd.android.package-archive"
                                "html", "htm" -> "text/html"
                                "xml" -> "application/xml"
                                "txt", "log" -> "text/plain"
                                else -> "application/octet-stream"
                            }
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
                            put(MediaStore.Downloads.MIME_TYPE, mime)
                            put(
                                MediaStore.Downloads.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/DevForge/" + artifactFolder,
                            )
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IllegalStateException("Android could not create the download file.")
                        try {
                            resolver.openOutputStream(uri)?.use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var fileBytes = 0L
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read <= 0) break
                                    fileBytes += read
                                    extractedBytes += read
                                    if (fileBytes > MAX_ARTIFACT_FILE_BYTES || extractedBytes > MAX_EXTRACTED_ARTIFACT_BYTES) {
                                        throw IllegalStateException("The extracted GitHub artifact exceeds the DevForge extraction safety limit.")
                                    }
                                    output.write(buffer, 0, read)
                                }
                            } ?: throw IllegalStateException("Android could not open the download file.")
                            resolver.update(
                                uri,
                                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                                null,
                                null,
                            )
                            extracted++
                        } catch (error: Throwable) {
                            runCatching { resolver.delete(uri, null, null) }
                            throw error
                        } finally {
                            zip.closeEntry()
                        }
                    }
                }

                if (extracted == 0) {
                    throw IllegalStateException("The GitHub artifact contained no downloadable files.")
                }

                withContext(Dispatchers.Main.immediate) {
                    artifactMessage =
                        "Downloaded $extracted file(s) from ${artifact.name} to Downloads/DevForge/$artifactFolder."
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    artifactMessage = error.message ?: "Artifact download failed."
                }
            } finally {
                runCatching { tempZip?.delete() }
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
        operationId?.let { DevForgeOperationCenter.cancel(it, "Superseded by a new build request.") }
        val currentOperationId = DevForgeOperationCenter.start(
            DevForgeOperationType.BUILD,
            "Remote " + request.target.workflowInput + " build",
            message = "Validating credentials and build configuration.",
        )
        operationId = currentOperationId
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
                    DevForgeOperationCenter.fail(currentOperationId, "Credential validation failed: " + credential.message)
                }
                return@launch
            }
        }
        val result = githubGateway.dispatch(request.githubOwner, request.githubRepository, request)
            when (result) {
                is GitHubDispatchResult.Started -> {
                    if (approvalId != null) approvalRepository.finishSuccess(approvalId)
                    DevForgeOperationCenter.running(
                        currentOperationId,
                        "GitHub Actions run #" + result.runId + " is running.",
                    )
                    withContext(Dispatchers.Main.immediate) {
                        state = BuildState.Running(result.runId, request)
                        startMonitoring(result.runId, request, immediateOnly = false)
                    }
                }
                is GitHubDispatchResult.Failure -> {
                    if (approvalId != null) approvalRepository.finishFailure(approvalId)
                    DevForgeOperationCenter.fail(currentOperationId, result.message)
                    withContext(Dispatchers.Main.immediate) { state = BuildState.Failed(result.message) }
                }
            }
        }
    }

    private fun startMonitoring(runId: Long, buildConfiguration: BuildConfiguration, immediateOnly: Boolean) {
        monitorJob?.cancel()
        monitoringMessage = null
        monitorJob = viewModelScope.launch(Dispatchers.IO) {
            do {
                withContext(Dispatchers.Main.immediate) {
                    monitoringMessage = null
                }
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
                    else -> BuildState.Failed("GitHub Actions run #" + snapshot.runNumber + " finished with " + (snapshot.conclusion ?: "an unknown conclusion") + ".")
                }
                when (snapshot.conclusion) {
                    "success" -> DevForgeOperationCenter.succeed(operationId ?: "", "Build completed successfully.")
                    "cancelled", "timed_out" -> DevForgeOperationCenter.cancel(operationId ?: "", "Build ended with " + (snapshot.conclusion ?: "cancellation") + ".")
                    else -> DevForgeOperationCenter.fail(operationId ?: "", "Build finished with " + (snapshot.conclusion ?: "an unknown conclusion") + ".")
                }
                operationId = null
                DevForgeActivityNotificationManager.notify(
                    "Build " + (snapshot.conclusion ?: snapshot.status),
                    "Run #" + snapshot.runNumber + " · " + buildConfiguration.target.workflowInput + " · " + (buildConfiguration.githubOwner + "/" + buildConfiguration.githubRepository),
                )
                val entry = BuildHistoryEntry(snapshot.id, snapshot.runNumber, buildConfiguration, snapshot.status, snapshot.conclusion, snapshot.htmlUrl, snapshot.updatedAt, System.currentTimeMillis())
                viewModelScope.launch(Dispatchers.IO) { buildReceiptDao.record(entry.toEntity(), MAX_HISTORY) }
            }
        }
    }

    private fun validateConfiguredWorkflow() {
        val owner = configuration.githubOwner.trim()
        val repository = configuration.githubRepository.trim()
        val workflowFile = configuration.workflowFile.trim()
        if (owner.isBlank() || repository.isBlank() || workflowFile.isBlank()) {
            workflowAvailable = false
            refreshDispatchCapability()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val available = when (val result = githubRepositoryGateway.listWorkflows(owner, repository)) {
                is com.mrredhood.devforge.core.github.GitHubWorkflowListResult.Success ->
                    result.workflows.any { it.path == workflowFile && it.state == "active" }
                is com.mrredhood.devforge.core.github.GitHubWorkflowListResult.Failure -> false
            }
            withContext(Dispatchers.Main.immediate) {
                if (
                    configuration.githubOwner.trim() == owner &&
                    configuration.githubRepository.trim() == repository &&
                    configuration.workflowFile.trim() == workflowFile
                ) {
                    workflowAvailable = available
                    refreshDispatchCapability()
                }
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
                !workflowAvailable -> CapabilityAvailability.NotConfigured
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
        val input = listOf(
            value.githubOwner,
            value.githubRepository,
            value.workflowFile,
            value.branch,
            value.buildTask,
            value.artifactName,
            value.target.name,
            value.buildArtifact,
            value.publishArtifacts,
            value.lintReport,
            value.unitTestReport,
            value.dependencyReport,
        ).joinToString("\u0000")
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
        .put("buildArtifact", value.buildArtifact)
        .put("publishArtifacts", value.publishArtifacts)
        .put("lintReport", value.lintReport)
        .put("unitTestReport", value.unitTestReport)
        .put("dependencyReport", value.dependencyReport)
        .toString()

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
            buildArtifact = json.optBoolean("buildArtifact", true),
            publishArtifacts = json.optBoolean("publishArtifacts", true),
            lintReport = json.optBoolean("lintReport", true),
            unitTestReport = json.optBoolean("unitTestReport", true),
            dependencyReport = json.optBoolean("dependencyReport", false),
        )
    }.getOrNull()

    override fun onCleared() {
        monitorJob?.cancel()
        approvalJob?.cancel()
        cancelApprovalJob?.cancel()
        workspaceJob?.cancel()
        operationId?.let { DevForgeOperationCenter.cancel(it, "Build screen was disposed.") }
        operationId = null
        super.onCleared()
    }

    private fun initialConfiguration(): BuildConfiguration {
        val saved = appSettings.snapshot()
        val output = buildSettingsStore.get(saved.githubOwner, saved.githubRepository)
        return BuildConfiguration(
            githubOwner = saved.githubOwner,
            githubRepository = saved.githubRepository,
            workflowFile = saved.githubWorkflow.ifBlank { ".github/workflows/android.yml" },
            branch = saved.githubBranch.ifBlank { "main" },
            buildArtifact = output.buildArtifact,
            publishArtifacts = output.publishArtifacts,
            lintReport = output.lintReport,
            unitTestReport = output.unitTestReport,
            dependencyReport = output.dependencyReport,
        ).withTarget(output.target)
    }

    companion object {
        private const val MAX_ARTIFACT_FILES = 200
        private const val MAX_ARTIFACT_FILE_BYTES = 128L * 1024L * 1024L
        private const val MAX_EXTRACTED_ARTIFACT_BYTES = 512L * 1024L * 1024L
        private const val APPROVAL_WINDOW_MS = 120_000L
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_HISTORY = 20
        private val ACTIVE_RUN_STATUSES = setOf("queued", "in_progress", "waiting", "requested", "pending")
    }
}
