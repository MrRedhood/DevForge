package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.security.AndroidSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GitHubRepositoryViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GitHubRepositoryGateway(AndroidSecretStore(application))

    var state by mutableStateOf(GitHubRepositoryState())
        private set

    fun refreshRepositories() {
        if (state.isLoading) return
        state = state.copy(isLoading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val userResult = gateway.currentUser()
            val repositoriesResult = gateway.listRepositories()
            val nextState = when {
                userResult.isFailure -> state.copy(
                    isLoading = false,
                    error = userResult.exceptionOrNull()?.message ?: "Unable to identify the GitHub account.",
                )
                repositoriesResult is GitHubRepositoryListResult.Success -> state.copy(
                    isLoading = false,
                    accountName = userResult.getOrNull(),
                    repositories = repositoriesResult.repositories,
                    truncated = repositoriesResult.truncated,
                    error = null,
                )
                repositoriesResult is GitHubRepositoryListResult.Failure -> state.copy(
                    isLoading = false,
                    accountName = userResult.getOrNull(),
                    error = repositoriesResult.message,
                )
                else -> state.copy(isLoading = false, error = "Unable to load GitHub repositories.")
            }
            state = nextState
        }
    }

    fun updateQuery(query: String) {
        state = state.copy(query = query)
    }

    fun selectRepository(repository: GitHubRepository) {
        if (state.isLoading) return
        state = state.copy(
            isLoading = true,
            selectedRepository = null,
            workflows = emptyList(),
            selectedWorkflow = null,
            error = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            when (val validated = gateway.getRepository(repository.owner, repository.name)) {
                is GitHubRepositoryResult.Success -> {
                    when (val workflows = gateway.listWorkflows(repository.owner, repository.name)) {
                        is GitHubWorkflowListResult.Success -> state = state.copy(
                            isLoading = false,
                            selectedRepository = validated.repository,
                            workflows = workflows.workflows,
                            selectedWorkflow = workflows.workflows.firstOrNull { it.state == "active" },
                            error = null,
                        )
                        is GitHubWorkflowListResult.Failure -> state = state.copy(
                            isLoading = false,
                            selectedRepository = validated.repository,
                            error = workflows.message,
                        )
                    }
                }
                is GitHubRepositoryResult.Failure -> state = state.copy(
                    isLoading = false,
                    error = validated.message,
                )
            }
        }
    }

    fun selectWorkflow(workflow: GitHubWorkflow) {
        state = state.copy(selectedWorkflow = workflow)
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
