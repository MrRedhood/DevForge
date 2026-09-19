package com.mrredhood.devforge.core.git

import android.app.Application
import android.net.Uri
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
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

class GitHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repositoryService = GitRepositoryService(application.contentResolver)
    private val statusService = GitWorkspaceStatusService(application.contentResolver)
    private val historyService = GitHistoryOperationService(application)
    private val reviewService = GitCommitHistoryService(application.contentResolver)
    private val workspaces = WorkspaceDatabaseRepository(application)
    private val githubHistory = GitHubRepositoryGateway(CredentialSecurityStore(application))
    private val approvalRepository = ApprovalRepository(DevForgeDatabase.get(application).approvalDao())
    private var detectionJob: Job? = null
    private var approvalJob: Job? = null
    private var reviewJob: Job? = null
    private var conflictJob: Job? = null

    var repository by mutableStateOf<GitRepositoryState?>(null)
        private set
    var status by mutableStateOf<GitWorkspaceStatus?>(null)
        private set
    var isExecuting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var selectedBranch by mutableStateOf<String?>(null)
        private set

    var commits by mutableStateOf<List<GitCommitHistoryEntry>>(emptyList())
        private set
    var selectedCommit by mutableStateOf<GitCommitHistoryEntry?>(null)
        private set
    var selectedCommitFiles by mutableStateOf<List<GitFileHistoryEntry>>(emptyList())
        private set
    var selectedFilePath by mutableStateOf<String?>(null)
        private set
    var selectedFileHistory by mutableStateOf<List<GitFileHistoryEntry>>(emptyList())
        private set
    var isLoadingHistory by mutableStateOf(false)
        private set

    var conflictSession by mutableStateOf<GitConflictSessionSnapshot?>(null)
        private set
    var isResolvingConflict by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            workspaces.activeWorkspace.collectLatest { workspace -> detect(workspace?.treeUri) }
        }
        approvalJob = viewModelScope.launch(Dispatchers.IO) {
            approvalRepository.observeApproved("git-history-").collect { approvals -> approvals.forEach { executeApproved(it) } }
        }
    }

    fun selectBranch(name: String) { selectedBranch = name }

    fun switchToSelectedBranch() {
        selectedBranch?.let { execute("git-history-switch", Capability.SWITCH_BRANCH, "Switch to branch $it", it) { repository -> historyService.switchBranch(repository, it) } }
    }

    fun mergeSelectedBranch() {
        selectedBranch?.let { execute("git-history-merge", Capability.MERGE_BRANCH, "Merge branch $it", it) { repository -> historyService.merge(repository, it) } }
    }

    fun rebaseOntoSelectedBranch() {
        selectedBranch?.let { execute("git-history-rebase", Capability.REBASE_BRANCH, "Rebase onto $it", it) { repository -> historyService.rebase(repository, it) } }
    }

    fun cherryPick(revision: String) {
        val value = revision.trim()
        if (!value.matches(SHA_PATTERN)) {
            message = "Enter a full 40-character commit SHA for cherry-pick."
            return
        }
        execute("git-history-cherry-pick", Capability.CHERRY_PICK, "Cherry-pick ${value.take(12)}", value) { repository -> historyService.cherryPick(repository, value) }
    }

    fun selectCommit(commit: GitCommitHistoryEntry) {
        selectedCommit = commit
        selectedFilePath = null
        selectedFileHistory = emptyList()
        val current = repository ?: return
        isLoadingHistory = true
        reviewJob?.cancel()
        reviewJob = viewModelScope.launch(Dispatchers.IO) {
            val files = reviewService.changedFiles(current, commit.commitId)
            withContext(Dispatchers.Main.immediate) {
                selectedCommitFiles = files
                isLoadingHistory = false
            }
        }
    }

    fun selectFile(path: String) {
        val current = repository ?: return
        selectedFilePath = path
        isLoadingHistory = true
        reviewJob?.cancel()
        reviewJob = viewModelScope.launch(Dispatchers.IO) {
            val history = reviewService.fileHistory(current, path)
            withContext(Dispatchers.Main.immediate) {
                selectedFileHistory = history
                isLoadingHistory = false
            }
        }
    }

    fun selectConflictFile(path: String) {
        val current = conflictSession ?: return
        if (path !in current.paths) return
        conflictSession = current.copy(selectedPath = path)
    }

    fun resolveConflict(path: String, content: String?) {
        val session = conflictSession ?: return
        conflictJob?.cancel()
        conflictJob = viewModelScope.launch(Dispatchers.IO) {
            isResolvingConflict = true
            val updated = runCatching { historyService.resolveConflict(session.sessionId, path, content) }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                isResolvingConflict = false
                if (updated == null) {
                    message = "The conflict session is no longer available."
                } else {
                    conflictSession = updated.copy(selectedPath = path.takeIf { it in updated.paths } ?: updated.paths.firstOrNull())
                    message = if (updated.paths.isEmpty()) "All conflicts are resolved. Review the result, then continue." else "Resolution staged for $path."
                }
            }
        }
    }

    fun continueConflict() {
        val session = conflictSession ?: return
        if (isResolvingConflict) return
        conflictJob?.cancel()
        conflictJob = viewModelScope.launch(Dispatchers.IO) {
            isResolvingConflict = true
            val result = historyService.continueConflict(session.sessionId)
            withContext(Dispatchers.Main.immediate) {
                isResolvingConflict = false
                when (result) {
                    is GitHistoryResult.Success -> {
                        val approvalId = session.approvalId
                        conflictSession = null
                        message = result.message
                        if (approvalId != null) viewModelScope.launch(Dispatchers.IO) { approvalRepository.finishSuccess(approvalId) }
                        detect(repository?.rootUri)
                    }
                    is GitHistoryResult.Conflict -> {
                        conflictSession = historyService.loadConflictSession(result.sessionId ?: session.sessionId)
                        message = "More conflicts remain. Resolve them before continuing."
                    }
                    is GitHistoryResult.Failure -> message = result.message
                }
            }
        }
    }

    fun abortConflict() {
        val session = conflictSession ?: return
        if (isResolvingConflict) return
        conflictJob?.cancel()
        conflictJob = viewModelScope.launch(Dispatchers.IO) {
            isResolvingConflict = true
            val result = historyService.abortConflict(session.sessionId)
            val approvalId = session.approvalId
            withContext(Dispatchers.Main.immediate) {
                isResolvingConflict = false
                conflictSession = null
                message = resultMessage(result)
                if (approvalId != null) viewModelScope.launch(Dispatchers.IO) { approvalRepository.finishFailure(approvalId) }
            }
        }
    }

    private fun detect(root: Uri?) {
        detectionJob?.cancel()
        reviewJob?.cancel()
        conflictJob?.cancel()
        repository = null
        status = null
        selectedBranch = null
        commits = emptyList()
        selectedCommit = null
        selectedCommitFiles = emptyList()
        selectedFilePath = null
        selectedFileHistory = emptyList()
        conflictSession = null
        isLoadingHistory = false
        if (root == null) return
        detectionJob = viewModelScope.launch {
            val detected = repositoryService.detect(root)
            val current = (detected as? GitDetectionState.Detected)?.repository ?: return@launch
            val inspected = statusService.inspect(current.rootUri, current.gitDirectoryUri, current.headRevision)
            repository = current.copy(statusAvailability = inspected.mode)
            status = inspected
            selectedBranch = current.branches.firstOrNull { !it.isCurrent }?.name
            isLoadingHistory = true
            reviewJob = launch(Dispatchers.IO) {
                val history = reviewService.load(current)
                if (history.commits.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        commits = history.commits
                        isLoadingHistory = false
                    }
                } else {
                    val remote = parseGitHubRemote(current.remoteUrl)
                    if (remote != null) {
                        when (val remoteHistory = githubHistory.listCommits(
                            remote.first,
                            remote.second,
                            current.branchName ?: "main",
                        )) {
                            is com.mrredhood.devforge.core.github.GitHubCommitHistoryResult.Success -> withContext(Dispatchers.Main.immediate) {
                                commits = remoteHistory.commits.map { item ->
                                    GitCommitHistoryEntry(
                                        commitId = item.sha,
                                        shortId = item.sha.take(12),
                                        subject = item.subject.ifBlank { "(no commit message)" },
                                        author = item.author.ifBlank { "Unknown author" },
                                        authoredAtEpochMs = item.authoredAt?.let { java.time.Instant.parse(it).toEpochMilli() },
                                        parents = emptyList(),
                                        changedFileCount = 0,
                                        changedFilesTruncated = false,
                                    )
                                }
                                isLoadingHistory = false
                            }
                            is com.mrredhood.devforge.core.github.GitHubCommitHistoryResult.Failure -> withContext(Dispatchers.Main.immediate) {
                                message = remoteHistory.message
                                isLoadingHistory = false
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main.immediate) { isLoadingHistory = false }
                    }
                }
            }
        }
    }

    private fun parseGitHubRemote(url: String?): Pair<String, String>? {
        val value = url?.trim() ?: return null
        val https = Regex("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?$")
        val match = https.matchEntire(value) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun execute(
        type: String,
        capability: Capability,
        summary: String,
        parameters: String,
        block: suspend (GitRepositoryState) -> GitHistoryResult,
    ) {
        if (isExecuting || conflictSession != null) return
        val current = repository ?: run {
            message = "No supported Git repository is active."
            return
        }
        val currentStatus = status ?: run {
            message = "Git status is not ready yet."
            return
        }
        if (currentStatus.truncated || current.statusAvailability != GitStatusAvailability.IndexAndHeadAware || currentStatus.files.any { it.gitStatus != GitFileStatus.Clean }) {
            message = "This operation requires a fully readable clean worktree."
            return
        }
        val precondition = hash("${current.headRevision.orEmpty()}|${currentStatus.files.joinToString { it.path + ":" + it.gitStatus.name }}")
        val action = ActionRequest(
            actionId = "$type:${System.currentTimeMillis()}",
            capability = capability,
            risk = RiskLevel.R2,
            workspaceId = current.rootUri.toString(),
            summary = summary,
            parametersHash = hash(parameters),
            preconditionHash = precondition,
        )
        if (DefaultPolicy.requiresApproval(action, PermissionMode.SOME)) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    approvalRepository.createPending(
                        approvalId = action.actionId,
                        actionId = action.actionId,
                        capability = capability,
                        risk = RiskLevel.R2,
                        workspaceId = current.rootUri.toString(),
                        summary = summary,
                        parametersHash = action.parametersHash,
                        preconditionHash = precondition,
                        payload = encode(type, current, parameters),
                        expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_WINDOW_MS,
                    )
                }.onSuccess { withContext(Dispatchers.Main.immediate) { message = "$summary is waiting for approval in Approval Center." } }
                    .onFailure { error -> withContext(Dispatchers.Main.immediate) { message = error.message ?: "Unable to create the history approval." } }
            }
            return
        }
        runOperation(current, block)
    }

    private suspend fun executeApproved(approval: ApprovalEntity) {
        if (approval.expiresAtEpochMs <= System.currentTimeMillis() || !approvalRepository.claimApproved(approval.approvalId)) return
        val action = decode(approval.payload) ?: run { approvalRepository.finishFailure(approval.approvalId); return }
        val detected = repositoryService.detect(action.root) as? GitDetectionState.Detected
        val current = detected?.repository
        if (current == null || current.gitDirectoryUri.toString() != action.gitDirectory.toString()) {
            approvalRepository.finishFailure(approval.approvalId)
            return
        }
        val fresh = statusService.inspect(current.rootUri, current.gitDirectoryUri, current.headRevision)
        val freshPrecondition = hash("${current.headRevision.orEmpty()}|${fresh.files.joinToString { it.path + ":" + it.gitStatus.name }}")
        if (fresh.truncated || freshPrecondition != approval.preconditionHash || fresh.files.any { it.gitStatus != GitFileStatus.Clean }) {
            approvalRepository.finishFailure(approval.approvalId)
            withContext(Dispatchers.Main.immediate) { message = "Approved history action was blocked because repository state changed." }
            return
        }

        val result = withContext(Dispatchers.IO) {
            isExecuting = true
            when (action.type) {
                "git-history-switch" -> historyService.switchBranch(current, action.parameters)
                "git-history-merge" -> historyService.merge(current, action.parameters, approval.approvalId)
                "git-history-rebase" -> historyService.rebase(current, action.parameters, approval.approvalId)
                "git-history-cherry-pick" -> historyService.cherryPick(current, action.parameters, approval.approvalId)
                else -> GitHistoryResult.Failure("Unsupported approved history action.")
            }
        }
        withContext(Dispatchers.Main.immediate) {
            isExecuting = false
            message = resultMessage(result)
            when (result) {
                is GitHistoryResult.Conflict -> {
                    conflictSession = result.sessionId?.let { id -> historyService.loadConflictSession(id) }
                }
                else -> Unit
            }
        }
        when (result) {
            is GitHistoryResult.Success -> {
                approvalRepository.finishSuccess(approval.approvalId)
                detect(current.rootUri)
            }
            is GitHistoryResult.Conflict -> Unit
            is GitHistoryResult.Failure -> approvalRepository.finishFailure(approval.approvalId)
        }
    }

    private fun runOperation(repository: GitRepositoryState, block: suspend (GitRepositoryState) -> GitHistoryResult) {
        viewModelScope.launch(Dispatchers.IO) {
            isExecuting = true
            val result = block(repository)
            withContext(Dispatchers.Main.immediate) {
                isExecuting = false
                message = resultMessage(result)
            }
            when (result) {
                is GitHistoryResult.Success -> detect(repository.rootUri)
                is GitHistoryResult.Conflict -> {
                    val id = result.sessionId
                    withContext(Dispatchers.Main.immediate) {
                        conflictSession = id?.let { historyService.loadConflictSession(it) }
                    }
                }
                is GitHistoryResult.Failure -> Unit
            }
        }
    }

    private fun resultMessage(result: GitHistoryResult): String = when (result) {
        is GitHistoryResult.Success -> result.message
        is GitHistoryResult.Failure -> result.message
        is GitHistoryResult.Conflict -> "${result.operation} found ${result.paths.size} conflict(s). Resolve them in the conflict editor before continuing."
    }

    private fun encode(type: String, repository: GitRepositoryState, parameters: String): String = JSONObject()
        .put("type", type)
        .put("root", repository.rootUri.toString())
        .put("git", repository.gitDirectoryUri.toString())
        .put("branch", repository.branchName.orEmpty())
        .put("parameters", parameters)
        .toString()

    private data class Decoded(val type: String, val root: Uri, val gitDirectory: Uri, val parameters: String)

    private fun decode(payload: String): Decoded? = runCatching {
        val json = JSONObject(payload)
        Decoded(json.getString("type"), Uri.parse(json.getString("root")), Uri.parse(json.getString("git")), json.getString("parameters"))
    }.getOrNull()

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    override fun onCleared() {
        detectionJob?.cancel()
        approvalJob?.cancel()
        reviewJob?.cancel()
        conflictJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val APPROVAL_WINDOW_MS = 120_000L
        private val SHA_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}
