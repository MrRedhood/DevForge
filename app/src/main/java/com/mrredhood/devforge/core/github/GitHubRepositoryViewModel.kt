package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GitHubRepositoryViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GitHubRepositoryGateway(CredentialSecurityStore(application))

    var state by mutableStateOf(GitHubRepositoryState())
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

    fun selectWorkflow(workflow: GitHubWorkflow) {
        if (!state.isLoading) state = state.copy(selectedWorkflow = workflow)
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
