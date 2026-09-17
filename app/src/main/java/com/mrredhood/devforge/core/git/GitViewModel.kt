package com.mrredhood.devforge.core.git

import android.app.Application
import android.net.Uri
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
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
    private val remoteService = GitRemoteTransportService(application)
    private val workspaces = WorkspaceDatabaseRepository(application)
    private val approvalRepository = ApprovalRepository(DevForgeDatabase.get(application).approvalDao())
    private var detectionJob: Job? = null
    private var statusJob: Job? = null
    private var mutationJob: Job? = null
    private var approvalJob: Job? = null

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
        approvalJob = viewModelScope.launch(Dispatchers.IO) {
            approvalRepository.observeApproved("git-").collect { approvals -> approvals.forEach { executeApprovedMutation(it) } }
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
            if (detected is GitDetectionState.Detected) inspectWorkspace(root, detected.repository.gitDirectoryUri, detected.repository.headRevision)
            else refreshCapabilities()
        }
    }

    fun inspectWorkspace(root: Uri? = activeRoot(), gitDirectory: Uri? = activeGitDirectory(), headRevision: String? = activeHeadRevision()) {
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
            if (detected != null) state = detected.copy(repository = detected.repository.copy(statusAvailability = status.mode))
            isInspectingStatus = false
            refreshCapabilities()
        }
    }

    fun stage(path: String) = executeMutation("git-stage", Capability.STAGE_FILES, RiskLevel.R1, "Stage $path", path) { repository ->
        executionService.stage(repository.rootUri, repository.gitDirectoryUri, listOf(path))
    }

    fun unstage(path: String) = executeMutation("git-unstage", Capability.STAGE_FILES, RiskLevel.R1, "Unstage $path", path) { repository ->
        executionService.unstage(repository.gitDirectoryUri, repository.headRevision, listOf(path))
    }

    fun commit(message: String) = executeMutation("git-commit", Capability.CREATE_COMMIT, RiskLevel.R2, "Create Git commit", message.trim()) { repository ->
        executionService.commit(repository.gitDirectoryUri, repository.headRevision, message)
    }

    fun createBranch(name: String) = executeMutation("git-create-branch", Capability.CREATE_BRANCH, RiskLevel.R1, "Create branch ${name.trim()}", name.trim()) { repository ->
        executionService.createBranch(repository.gitDirectoryUri, repository.headRevision, name)
    }

    fun deleteBranch(name: String) = executeMutation("git-delete-branch", Capability.DELETE_BRANCH, RiskLevel.R2, "Delete branch ${name.trim()}", name.trim()) { repository ->
        executionService.deleteBranch(repository.gitDirectoryUri, repository.branchName, name)
    }

    fun fetchRemote() = executeRemote("git-fetch", Capability.FETCH_REMOTE, RiskLevel.R1, "Fetch origin") { repository ->
        remoteService.fetch(repository)
    }

    fun pullRemote() = executeRemote("git-pull", Capability.PULL_REMOTE, RiskLevel.R2, "Pull from origin") { repository ->
        remoteService.pull(repository)
    }

    fun pushRemote() = executeRemote("git-push", Capability.PUSH_REMOTE, RiskLevel.R3, "Push current branch to origin") { repository ->
        remoteService.push(repository)
    }

    private fun executeMutation(
        actionType: String,
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
        submitApprovedOrRun(actionType, capability, risk, summary, parameters, repository) { target -> block(target) }
    }

    private fun executeRemote(
        actionType: String,
        capability: Capability,
        risk: RiskLevel,
        summary: String,
        block: suspend (GitRepositoryState) -> GitRemoteResult,
    ) {
        if (isExecuting) return
        val repository = (state as? GitDetectionState.Detected)?.repository ?: run {
            operationMessage = "No supported Git repository is active."
            return
        }
        if (remoteService.validateConfigured(repository.remoteUrl).available.not()) {
            operationMessage = remoteService.validateConfigured(repository.remoteUrl).reason ?: "Remote Git transport is unavailable."
            refreshCapabilities()
            return
        }

        val parameters = repository.remoteUrl.orEmpty()
        submitApprovedOrRemote(actionType, capability, risk, summary, parameters, repository, block)
    }

    private fun submitApprovedOrRun(
        actionType: String,
        capability: Capability,
        risk: RiskLevel,
        summary: String,
        parameters: String,
        repository: GitRepositoryState,
        block: suspend (GitRepositoryState) -> GitExecutionResult,
    ) {
        val precondition = currentPrecondition(repository)
        val action = ActionRequest(
            actionId = "$actionType:${System.currentTimeMillis()}",
            capability = capability,
            risk = risk,
            workspaceId = repository.rootUri.toString(),
            summary = summary,
            parametersHash = hash(parameters),
            preconditionHash = precondition,
        )
        if (DefaultPolicy.requiresApproval(action, PermissionMode.SOME)) {
            queueApproval(action, summary, precondition, encodeGitAction(actionType, repository, parameters))
            return
        }
        runMutation(repository, block, null)
    }

    private fun submitApprovedOrRemote(
        actionType: String,
        capability: Capability,
        risk: RiskLevel,
        summary: String,
        parameters: String,
        repository: GitRepositoryState,
        block: suspend (GitRepositoryState) -> GitRemoteResult,
    ) {
        val precondition = currentPrecondition(repository)
        val action = ActionRequest(
            actionId = "$actionType:${System.currentTimeMillis()}",
            capability = capability,
            risk = risk,
            workspaceId = repository.rootUri.toString(),
            summary = summary,
            parametersHash = hash(parameters),
            preconditionHash = precondition,
        )
        if (DefaultPolicy.requiresApproval(action, PermissionMode.SOME)) {
            queueApproval(action, summary, precondition, encodeGitAction(actionType, repository, parameters))
            return
        }
        remoteJob(repository, block, null)
    }

    private fun queueApproval(action: ActionRequest, summary: String, precondition: String, payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                approvalRepository.createPending(
                    approvalId = action.actionId,
                    actionId = action.actionId,
                    capability = action.capability,
                    risk = action.risk,
                    workspaceId = action.workspaceId,
                    summary = summary,
                    parametersHash = action.parametersHash,
                    preconditionHash = precondition,
                    payload = payload,
                    expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_WINDOW_MS,
                )
            }.onSuccess {
                withContext(Dispatchers.Main.immediate) {
                    operationMessage = "$summary is waiting for approval in Approval Center."
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main.immediate) {
                    operationMessage = error.message ?: "Unable to create the Git approval request."
                }
            }
        }
    }

    private suspend fun executeApprovedMutation(approval: ApprovalEntity) {
        if (approval.expiresAtEpochMs <= System.currentTimeMillis()) {
            approvalRepository.expireDue()
            return
        }
        if (!approvalRepository.claimApproved(approval.approvalId)) return
        val action = decodeGitAction(approval.payload) ?: run {
            approvalRepository.finishFailure(approval.approvalId)
            return
        }

        val detected = repositoryService.detect(action.repository.rootUri)
        val current = (detected as? GitDetectionState.Detected)?.repository
        if (current == null || current.rootUri.toString() != action.repository.rootUri.toString() || current.remoteUrl != action.repository.remoteUrl) {
            approvalRepository.finishFailure(approval.approvalId)
            return
        }

        val freshStatus = statusService.inspect(current.rootUri, current.gitDirectoryUri, current.headRevision)
        val freshPrecondition = hash("${current.headRevision.orEmpty()}|${freshStatus.files.joinToString { it.path + ":" + it.gitStatus.name }}")
        if (freshPrecondition != approval.preconditionHash || freshStatus.truncated) {
            approvalRepository.finishFailure(approval.approvalId)
            withContext(Dispatchers.Main.immediate) {
                operationMessage = "Approved Git action was blocked because the repository changed. Review the action again."
            }
            return
        }

        withContext(Dispatchers.Main.immediate) {
            state = detected
            workspaceStatus = freshStatus
            isExecuting = true
            operationMessage = "Executing approved Git action…"
        }

        val result: Any = when (action.type) {
            "git-commit" -> executionService.commit(action.repository.gitDirectoryUri, current.headRevision, action.parameters)
            "git-delete-branch" -> executionService.deleteBranch(action.repository.gitDirectoryUri, current.branchName, action.parameters)
            "git-fetch" -> remoteService.fetch(current)
            "git-pull" -> remoteService.pull(current)
            "git-push" -> remoteService.push(current)
            else -> GitExecutionResult.Failure("Unsupported approved Git action.")
        }

        val success = when (result) {
            is GitExecutionResult.Success -> result.message
            is GitExecutionResult.Failure -> result.message
            is GitRemoteResult.Success -> result.message
            is GitRemoteResult.Failure -> result.message
            else -> "Git action completed."
        }

        withContext(Dispatchers.Main.immediate) {
            isExecuting = false
            operationMessage = success
        }

        val succeeded = result is GitExecutionResult.Success || result is GitRemoteResult.Success
        if (succeeded) {
            approvalRepository.finishSuccess(approval.approvalId)
            withContext(Dispatchers.Main.immediate) { refreshAfterMutation(current.rootUri) }
        } else {
            approvalRepository.finishFailure(approval.approvalId)
            refreshCapabilities()
        }
    }

    private fun runMutation(repository: GitRepositoryState, block: suspend (GitRepositoryState) -> GitExecutionResult, approvalId: String?) {
        mutationJob?.cancel()
        isExecuting = true
        operationMessage = null
        mutationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = block(repository)
            if (approvalId != null) {
                if (result is GitExecutionResult.Success) approvalRepository.finishSuccess(approvalId) else approvalRepository.finishFailure(approvalId)
            }
            withContext(Dispatchers.Main.immediate) {
                isExecuting = false
                operationMessage = when (result) {
                    is GitExecutionResult.Success -> result.message
                    is GitExecutionResult.Failure -> result.message
                }
                if (result is GitExecutionResult.Success) refreshAfterMutation(repository.rootUri) else refreshCapabilities()
            }
        }
    }

    private fun remoteJob(repository: GitRepositoryState, block: suspend (GitRepositoryState) -> GitRemoteResult, approvalId: String?) {
        mutationJob?.cancel()
        isExecuting = true
        operationMessage = null
        mutationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = block(repository)
            if (approvalId != null) {
                if (result is GitRemoteResult.Success) approvalRepository.finishSuccess(approvalId) else approvalRepository.finishFailure(approvalId)
            }
            withContext(Dispatchers.Main.immediate) {
                isExecuting = false
                operationMessage = when (result) {
                    is GitRemoteResult.Success -> result.message
                    is GitRemoteResult.Failure -> result.message
                }
                if (result is GitRemoteResult.Success) refreshAfterMutation(repository.rootUri) else refreshCapabilities()
            }
        }
    }

    private fun currentPrecondition(repository: GitRepositoryState): String = hash(
        "${repository.headRevision.orEmpty()}|${workspaceStatus?.files?.joinToString { it.path + ":" + it.gitStatus.name }}",
    )

    private fun refreshAfterMutation(root: Uri) {
        detectionJob?.cancel()
        state = GitDetectionState.Detecting
        detectionJob = viewModelScope.launch {
            val detected = repositoryService.detect(root)
            state = detected
            if (detected is GitDetectionState.Detected) inspectWorkspace(root, detected.repository.gitDirectoryUri, detected.repository.headRevision) else refreshCapabilities()
        }
    }

    private fun refreshCapabilities() {
        val repository = (state as? GitDetectionState.Detected)?.repository
        val status = workspaceStatus
        val mutationReadReady = repository != null && status != null && !status.truncated
        val remote = remoteService.validateConfigured(repository?.remoteUrl)
        val remoteReady = repository?.branchName != null && remote.available
        capabilities = GitCapabilityState(
            stage = if (mutationReadReady) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            unstage = if (mutationReadReady && repository?.headRevision != null && status.mode == GitStatusAvailability.IndexAndHeadAware) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            commit = if (mutationReadReady && status.mode == GitStatusAvailability.IndexAndHeadAware && status.files.none { it.gitStatus == GitFileStatus.Conflict || it.gitStatus == GitFileStatus.Unchecked }) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            createBranch = if (repository?.headRevision != null) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            deleteBranch = if (repository?.branchName != null && repository.branches.any { !it.isCurrent }) CapabilityAvailability.Available else CapabilityAvailability.Unavailable,
            fetchRemote = if (remoteReady) CapabilityAvailability.Available else CapabilityAvailability.NotConfigured,
            pullRemote = if (remoteReady && status?.mode == GitStatusAvailability.IndexAndHeadAware) CapabilityAvailability.Available else CapabilityAvailability.NotConfigured,
            pushRemote = if (remoteReady) CapabilityAvailability.Available else CapabilityAvailability.NotConfigured,
        )
    }

    private fun encodeGitAction(type: String, repository: GitRepositoryState, parameters: String): String = JSONObject()
        .put("type", type)
        .put("root", repository.rootUri.toString())
        .put("git", repository.gitDirectoryUri.toString())
        .put("head", repository.headRevision.orEmpty())
        .put("branch", repository.branchName.orEmpty())
        .put("remote", repository.remoteUrl.orEmpty())
        .put("parameters", parameters)
        .toString()

    private fun decodeGitAction(payload: String): DecodedGitAction? = runCatching {
        val json = JSONObject(payload)
        DecodedGitAction(
            type = json.getString("type"),
            repository = GitRepositoryState(
                rootUri = Uri.parse(json.getString("root")),
                gitDirectoryUri = Uri.parse(json.getString("git")),
                branchName = json.optString("branch").ifBlank { null },
                headRevision = json.optString("head").ifBlank { null },
                remoteUrl = json.optString("remote").ifBlank { null },
                detachedHead = json.optString("branch").isBlank(),
            ),
            parameters = json.getString("parameters"),
        )
    }.getOrNull()

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun activeRoot(): Uri? = (state as? GitDetectionState.Detected)?.repository?.rootUri
    private fun activeGitDirectory(): Uri? = (state as? GitDetectionState.Detected)?.repository?.gitDirectoryUri
    private fun activeHeadRevision(): String? = (state as? GitDetectionState.Detected)?.repository?.headRevision

    override fun onCleared() {
        detectionJob?.cancel()
        statusJob?.cancel()
        mutationJob?.cancel()
        approvalJob?.cancel()
        super.onCleared()
    }

    private data class DecodedGitAction(val type: String, val repository: GitRepositoryState, val parameters: String)

    companion object {
        private const val APPROVAL_WINDOW_MS = 120_000L
    }
}
