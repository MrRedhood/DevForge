package com.mrredhood.devforge.core.github

data class GitHubRepository(
    val id: Long,
    val owner: String,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
)

data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
)

sealed interface GitHubRepositoryResult {
    data class Success(val repository: GitHubRepository) : GitHubRepositoryResult
    data class Failure(val message: String) : GitHubRepositoryResult
}

sealed interface GitHubRepositoryListResult {
    data class Success(val repositories: List<GitHubRepository>, val truncated: Boolean) : GitHubRepositoryListResult
    data class Failure(val message: String) : GitHubRepositoryListResult
}

sealed interface GitHubWorkflowListResult {
    data class Success(val workflows: List<GitHubWorkflow>) : GitHubWorkflowListResult
    data class Failure(val message: String) : GitHubWorkflowListResult
}
