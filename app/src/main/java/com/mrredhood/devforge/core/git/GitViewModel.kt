package com.mrredhood.devforge.core.git

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.Approval
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class GitViewModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val repositoryService = GitRepositoryService(resolver)
    private val statusService = GitWorkspaceStatusService(resolver)
    private val executionService = GitExecutionService(resolver)
    private val workspaces = WorkspaceDatabaseRepository(application)
    private var detectionJob: Job? = null
    private var statusJob: Job? = null
    private var mutationJob: Job? = null

    var state by mutableStateOf<GitDetectionState>(GitDetectionState.NotDetected)
        private set
    var workspaceStatus by mutableStateOf<GitWorkspaceStatus?>(null)
        private set
    var isInspectingStatus by mutableStateOf(false)
        private set
    var isExecuting by mutableStateOf(false)
        private set
    var operationMessage by mutableStateOf<String?>(null)
        private set
    var capabilities by mutableStateOf(GitCapabilityState())
        private set

    init {
        viewModelScope.launch {
            workspaces.activeWorkspace.collectLatest { workspace -> detect(workspace?.treeUri) }
        }
    }

    fun detect(root: Uri?) {
        detectionJob?.cancel()
        statusJob?.cancel()
        workspaceStatus = null
        operationMessage = null
        if (root == null) {
            state = GitDetectionState.NotDetected
            refreshCapabilities()
            return
        }
        state = GitDetectionState.Detecting
        refreshCapabilities()
        detectionJob = viewModelScope.launch {
            val detected = repositoryService.detect(root)
            state = detected
            if (detected is GitDetectionState.Detected) {
                inspectWorkspace(root, detected.repository.gitDirectoryUri, detected.repository.headRevision)
            } else {
                refreshCapabilities()
            }
        }
    }

    fun inspectWorkspace(
        root: Uri? = activeRoot(),
        gitDirectory: Uri? = activeGitDirectory(),
        headRevision: String? = activeHeadRevision(),
    ) {
        statusJob?.cancel()
        if (root == null) {
            workspaceStatus = null
            refreshCapabilities()
            return
        }
        isInspectingStatus = true
        statusJob = viewModelScope.launch {
            val status = statusService.inspect(root, gitDirectory, headRevision)
            workspaceStatus = status
            val detected = state as? GitDetectionState.Detected
            if (detected != null) {
                state = detected.copy(repository = detected.repository.copy(statusAvailability = status.mode))
            }
            isInspectingStatus = false
            refreshCapabilities()
        }
    }

    fun stage(path: String) = executeMutation(
        actionId = "git-stage",
        capability = Capability.STAGE_FILES,
        risk = RiskLevel.R1,
        summary = "Stage $path",
        parameters = path,
    ) { repository ->
        executionService.stage(repository.rootUri, repository.gitDirectoryUri, listOf(path))
    }

    fun unstage(path: String) = executeMutation(
        actionId = "git-unstage",
        capability = Capability.STAGE_FILES,
        risk = RiskLevel.R1,
        summary = "Unstage $path",
        parameters = path,
    ) { repository ->
        executionService.unstage(repository.gitDirectoryUri, repository.headRevision, listOf(path))
    }

    fun commit(message: String) = executeMutation(
        actionId = "git-commit",
        capability = Capability.CREATE_COMMIT,
        risk = RiskLevel.R2,
        summary = "Create Git commit",
        parameters = message.trim(),
    ) { repository ->
        executionService.commit(repository.gitDirectoryUri, repository.headRevision, message)
    }

    fun createBranch(name: String) = executeMutation(
        actionId = "git-create-branch",
        capability = Capability.CREATE_BRANCH,
        risk = RiskLevel.R2,
        summary = "Create branch $name",
        parameters = name.trim(),
    ) { repository ->
        executionService.createBranch(repository.gitDirectoryUri, repository.headRevision, name)
    }

    fun deleteBranch(name: String) = executeMutation(
        actionId = "git-delete-branch",
        capability = Capability.DELETE_BRANCH,
        risk = RiskLevel.R2,
        summary = "Delete branch $name",
        parameters = name.trim(),
    ) { repository ->
        executionService.deleteBranch(repository.gitDirectoryUri, repository.branchName, name)
    }

    private fun executeMutation(
        actionId: String,
        capability: Capability,
        risk: RiskLevel,
        summary: String,
        parameters: String,
        block: suspend (GitRepositoryState) -> GitExecutionResult,
    ) {
        if (isExecuting) return
        val repository = (state as? GitDetectionState.Detected)?.repository ?: run {
            operationMessage = "No supported Git repository is active."
            return
        }
        if (repository.statusAvailability == GitStatusAvailability.MetadataOnly && capability != Capability.CREATE_BRANCH) {
            operationMessage = "Git mutation is unavailable because the index/HEAD objects cannot be read safely through the selected workspace."
            return
        }

        val action = ActionRequest(
            actionId = actionId,
            capability = capability,
            risk = risk,
            workspaceId = repository.rootUri.toString(),
            summary = summary,
            parametersHash = hash(parameters),
            preconditionHash = hash("${repository.headRevision.orEmpty()}|${workspaceStatus?.files?.joinToString { it.path + ":" + it.gitStatus.name }}"),
        )
        if (DefaultPolicy.requiresApproval(action, PermissionMode.SOME)) {
            val approval = Approval(
                approvalId = "direct:$actionId:${System.currentTimeMillis()}",
                actionId = action.actionId,
                parametersHash = action.parametersHash,
                workspaceRevision = action.preconditionHash.orEmpty(),
                policyVersion = "v1",
                expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_WINDOW_MS,
            )
            if (approval.parametersHash != action.parametersHash || approval.expiresAtEpochMs <= System.currentTimeMillis()) {
                operationMessage = "Git action approval is no longer valid. Refresh the repository state and try again."
                return
            }
        }

        mutationJob?.cancel()
        isExecuting = true
        operationMessage = null
        mutationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = block(repository)
            withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                isExecuting = false
                operationMessage = when (result) {
                    is GitExecutionResult.Success -> result.message
                    is GitExecutionResult.Failure -> result.message
                }
                if (result is GitExecutionResult.Success) {
                    refreshAfterMutation(repository.rootUri)
                } else {
                    refreshCapabilities()
                }
            }
        }
    }

    private fun refreshAfterMutation(root: Uri) {
        detectionJob?.cancel()
        state = GitDetectionState.Detecting
        detectionJob = viewModelScope.launch {
            val detected = repositoryService.detect(root)
            state = detected
            if (detected is GitDetectionState.Detected) {
                inspectWorkspace(root, detected.repository.gitDirectoryUri, detected.repository.headRevision)
            } else {
                refreshCapabilities()
            }
        }
    }

    private fun refreshCapabilities() {
        val repository = (state as? GitDetectionState.Detected)?.repository
        val status = workspaceStatus
        val mutationReadReady = repository != null && status != null && !status.truncated
        capabilities = GitCapabilityState(
            stage = if (mutationReadReady) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            unstage = if (mutationReadReady && repository?.headRevision != null && status.mode == GitStatusAvailability.IndexAndHeadAware) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            commit = if (mutationReadReady && status.mode == GitStatusAvailability.IndexAndHeadAware && status.files.none { it.gitStatus == GitFileStatus.Conflict || it.gitStatus == GitFileStatus.Unchecked }) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            createBranch = if (repository?.headRevision != null) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            deleteBranch = if (repository?.branchName != null && repository.branches.any { !it.isCurrent }) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            fetchRemote = CapabilityAvailability.NotConfigured,
            pullRemote = CapabilityAvailability.NotConfigured,
            pushRemote = CapabilityAvailability.NotConfigured,
        )
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun activeRoot(): Uri? = (state as? GitDetectionState.Detected)?.repository?.rootUri
    private fun activeGitDirectory(): Uri? = (state as? GitDetectionState.Detected)?.repository?.gitDirectoryUri
    private fun activeHeadRevision(): String? = (state as? GitDetectionState.Detected)?.repository?.headRevision

    override fun onCleared() {
        detectionJob?.cancel()
        statusJob?.cancel()
        mutationJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val APPROVAL_WINDOW_MS = 120_000L
    }
}
