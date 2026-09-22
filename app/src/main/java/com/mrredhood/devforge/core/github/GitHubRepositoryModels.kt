package com.mrredhood.devforge.core.github

data class GitHubRepository(
    val id: Long,
    val owner: String,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val hasDownloads: Boolean = true,
    val allowSquashMerge: Boolean = true,
    val allowMergeCommit: Boolean = true,
    val allowRebaseMerge: Boolean = true,
    val allowAutoMerge: Boolean = false,
    val mergeCommitMessage: String = "PR_TITLE",
)

data class GitHubRepositoryCreationRequest(
    val name: String,
    val description: String,
    val homepage: String,
    val private: Boolean,
    val autoInit: Boolean,
    val gitignoreTemplate: String,
    val licenseTemplate: String,
    val hasIssues: Boolean,
    val hasProjects: Boolean,
    val hasWiki: Boolean,
    val hasDiscussions: Boolean,
    val isTemplate: Boolean,
    val defaultBranch: String,
    val hasDownloads: Boolean = true,
    val allowSquashMerge: Boolean = true,
    val allowMergeCommit: Boolean = true,
    val allowRebaseMerge: Boolean = true,
    val allowAutoMerge: Boolean = false,
    val mergeCommitMessage: String = "PR_TITLE",
    val visibility: String = "private",
    val deleteBranchOnMerge: Boolean = false,
    val squashMergeTitle: String = "PR_TITLE",
    val squashMergeMessage: String = "COMMIT_MESSAGES",
    val mergeCommitTitle: String = "PR_TITLE",
    val organization: String? = null,
    val teamId: Long? = null,
    val customPropertiesJson: String = "",
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

data class GitHubActivityItem(
    val number: Long,
    val title: String,
    val state: String,
    val author: String,
    val createdAt: String?,
    val url: String?,
)

sealed interface GitHubActivityResult {
    data class Success(val items: List<GitHubActivityItem>) : GitHubActivityResult
    data class Failure(val message: String) : GitHubActivityResult
}
