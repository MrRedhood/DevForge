package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.workspace.LocalGitHubRepositoryLinkStore
import com.mrredhood.devforge.core.build.GitHubBuildSettingsStore
import com.mrredhood.devforge.core.terminal.TerminalSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GitHubRepositoryViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GitHubRepositoryGateway(CredentialSecurityStore(application))
    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val githubWorkspaceStore = GitHubWorkspaceStore(application)
    private val localGitHubLinkStore = LocalGitHubRepositoryLinkStore(application)
    private val buildSettingsStore = GitHubBuildSettingsStore(application)
    private val terminalSessions = TerminalSessionRepository(application)

    var state by mutableStateOf(GitHubRepositoryState())
        private set

    var deletingRepositoryId by mutableStateOf<Long?>(null)
        private set

    var deletingWorkflowIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    private var operationJob: Job? = null
    private var operationGeneration = 0L

    fun refreshRepositories() {
        if (state.isLoading) return
        operationGeneration += 1
        val generation = operationGeneration
        operationJob?.cancel()
        state = state.copy(isLoading = true, error = null)
        operationJob = viewModelScope.launch(Dispatchers.IO) {
            val userResult = gateway.currentUser()
            val repositoriesResult = gateway.listRepositories()
            val current = withContext(Dispatchers.Main.immediate) { state }
            val nextState = when {
                userResult.isFailure -> current.copy(
                    isLoading = false,
                    error = userResult.exceptionOrNull()?.message ?: "Unable to identify the GitHub account.",
                )
                repositoriesResult is GitHubRepositoryListResult.Success -> current.copy(
                    isLoading = false,
                    accountName = userResult.getOrNull(),
                    repositories = repositoriesResult.repositories,
                    truncated = repositoriesResult.truncated,
                    error = null,
                )
                repositoriesResult is GitHubRepositoryListResult.Failure -> current.copy(
                    isLoading = false,
                    accountName = userResult.getOrNull(),
                    error = repositoriesResult.message,
                )
                else -> current.copy(isLoading = false, error = "Unable to load GitHub repositories.")
            }
            withContext(Dispatchers.Main.immediate) {
                if (generation == operationGeneration) state = nextState
            }
        }
    }

    fun updateQuery(query: String) {
        state = state.copy(query = query)
    }

    fun selectRepository(repository: GitHubRepository) {
        if (state.isLoading) return
        operationGeneration += 1
        val generation = operationGeneration
        operationJob?.cancel()
        state = state.copy(
            isLoading = true,
            selectedRepository = null,
            workflows = emptyList(),
            selectedWorkflow = null,
            error = null,
        )
        operationJob = viewModelScope.launch(Dispatchers.IO) {
            when (val validated = gateway.getRepository(repository.owner, repository.name)) {
                is GitHubRepositoryResult.Success -> {
                    when (val workflows = gateway.listWorkflows(repository.owner, repository.name)) {
                        is GitHubWorkflowListResult.Success -> {
                            withContext(Dispatchers.Main.immediate) {
                                if (generation == operationGeneration) {
                                    state = state.copy(
                                        isLoading = false,
                                        selectedRepository = validated.repository,
                                        workflows = workflows.workflows,
                                        selectedWorkflow = workflows.workflows.firstOrNull { it.state == "active" },
                                        error = null,
                                    )
                                }
                            }
                        }
                        is GitHubWorkflowListResult.Failure -> {
                            withContext(Dispatchers.Main.immediate) {
                                if (generation == operationGeneration) {
                                    state = state.copy(
                                        isLoading = false,
                                        selectedRepository = validated.repository,
                                        error = workflows.message,
                                    )
                                }
                            }
                        }
                    }
                }
                is GitHubRepositoryResult.Failure -> {
                    withContext(Dispatchers.Main.immediate) {
                        if (generation == operationGeneration) {
                            state = state.copy(
                                isLoading = false,
                                error = validated.message,
                            )
                        }
                    }
                }
            }
        }
    }

    fun applyRepositoryUpdate(updated: GitHubRepository) {
        state = state.copy(
            repositories = state.repositories.map { if (it.id == updated.id) updated else it },
            selectedRepository = if (state.selectedRepository?.id == updated.id) updated else state.selectedRepository,
            error = null,
        )
    }

    fun selectWorkflow(workflow: GitHubWorkflow) {
        if (!state.isLoading) state = state.copy(selectedWorkflow = workflow)
    }

    fun deleteWorkflows(
        owner: String,
        repository: String,
        branch: String,
        workflows: List<GitHubWorkflow>,
        onFinished: () -> Unit = {},
    ) {
        val targets = workflows.distinctBy { it.id }
            .filter { it.path.startsWith(".github/workflows/") }
        if (targets.isEmpty() || deletingWorkflowIds.isNotEmpty()) return
        operationGeneration += 1
        val generation = operationGeneration
        operationJob?.cancel()
        deletingWorkflowIds = targets.map { it.id }.toSet()
        state = state.copy(error = null)

        operationJob = viewModelScope.launch(Dispatchers.IO) {
            val deletedIds = mutableSetOf<Long>()
            val failures = mutableListOf<String>()
            targets.forEach { workflow ->
                val result = gateway.deleteWorkflow(owner, repository, branch, workflow)
                if (result.isSuccess) {
                    deletedIds += workflow.id
                } else {
                    failures += workflow.name + ": " + (
                        result.exceptionOrNull()?.message ?: "Unable to delete workflow."
                    )
                }
            }
            withContext(Dispatchers.Main.immediate) {
                if (generation != operationGeneration) return@withContext
                deletingWorkflowIds = emptySet()
                state = state.copy(
                    workflows = state.workflows.filterNot { it.id in deletedIds },
                    selectedWorkflow = state.selectedWorkflow?.takeUnless { it.id in deletedIds },
                    error = failures.takeIf { it.isNotEmpty() }?.joinToString("; "),
                )
                onFinished()
            }
        }
    }

    fun deleteRepository(repository: GitHubRepository, onDeleted: () -> Unit = {}) {
        if (deletingRepositoryId != null) return
        operationGeneration += 1
        val generation = operationGeneration
        operationJob?.cancel()
        deletingRepositoryId = repository.id
        state = state.copy(error = null)
        operationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = gateway.deleteRepository(repository.owner, repository.name)
            withContext(Dispatchers.Main.immediate) {
                deletingRepositoryId = null
                if (generation != operationGeneration) return@withContext
                if (result.isSuccess) {
                    val remoteWorkspaceIds = githubWorkspaceStore.removeByRepository(
                        repository.owner,
                        repository.name,
                    )
                    remoteWorkspaceIds.forEach { workspaceId ->
                        terminalSessions.clear(workspaceId)
                        workspaceRepository.delete(workspaceId)
                    }
                    GitHubPendingChanges.clearMany(remoteWorkspaceIds)
                    localGitHubLinkStore.removeByRepository(repository.owner, repository.name)
                    buildSettingsStore.remove(repository.owner, repository.name)
                    state = state.copy(
                        repositories = state.repositories.filterNot { it.id == repository.id },
                        selectedRepository = if (state.selectedRepository?.id == repository.id) null else state.selectedRepository,
                        workflows = if (state.selectedRepository?.id == repository.id) emptyList() else state.workflows,
                        selectedWorkflow = if (state.selectedRepository?.id == repository.id) null else state.selectedWorkflow,
                        error = null,
                    )
                    onDeleted()
                } else {
                    state = state.copy(
                        error = result.exceptionOrNull()?.message ?: "Unable to delete the GitHub repository.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        operationJob?.cancel()
        super.onCleared()
    }
}

data class GitHubRepositoryState(
    val accountName: String? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val query: String = "",
    val selectedRepository: GitHubRepository? = null,
    val workflows: List<GitHubWorkflow> = emptyList(),
    val selectedWorkflow: GitHubWorkflow? = null,
    val isLoading: Boolean = false,
    val truncated: Boolean = false,
    val error: String? = null,
)
