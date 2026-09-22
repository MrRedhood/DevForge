package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GitHubRepositoryActivityState(
    val selectedRepository: GitHubRepository? = null,
    val issues: List<GitHubActivityItem> = emptyList(),
    val pullRequests: List<GitHubActivityItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class GitHubRepositoryActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GitHubRepositoryGateway(CredentialSecurityStore(application))

    var state by mutableStateOf(GitHubRepositoryActivityState())
        private set

    fun select(repository: GitHubRepository) {
        if (state.selectedRepository?.fullName == repository.fullName && state.loading) return
        state = state.copy(
            selectedRepository = repository,
            issues = emptyList(),
            pullRequests = emptyList(),
            loading = true,
            error = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val issues = gateway.listOpenIssues(repository.owner, repository.name)
            val pullRequests = gateway.listOpenPullRequests(repository.owner, repository.name)
            withContext(Dispatchers.Main.immediate) {
                state = when {
                    issues is GitHubActivityResult.Failure -> state.copy(loading = false, error = issues.message)
                    pullRequests is GitHubActivityResult.Failure -> state.copy(loading = false, error = pullRequests.message)
                    issues is GitHubActivityResult.Success && pullRequests is GitHubActivityResult.Success ->
                        state.copy(
                            loading = false,
                            issues = issues.items,
                            pullRequests = pullRequests.items,
                            error = null,
                        )
                    else -> state.copy(loading = false)
                }
            }
        }
    }
}
