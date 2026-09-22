package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.build.GitHubBuildSettingsStore
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.workspace.LocalGitHubRepositoryLinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class GitHubRepositoryEditForm(
    val name: String = "",
    val description: String = "",
    val homepage: String = "",
    val visibility: String = "private",
    val defaultBranch: String = "main",
    val hasIssues: Boolean = true,
    val hasProjects: Boolean = true,
    val hasWiki: Boolean = false,
    val hasDiscussions: Boolean = false,
    val hasDownloads: Boolean = true,
    val allowSquashMerge: Boolean = true,
    val allowMergeCommit: Boolean = true,
    val allowRebaseMerge: Boolean = true,
    val allowAutoMerge: Boolean = false,
    val deleteBranchOnMerge: Boolean = false,
    val squashMergeTitle: String = "PR_TITLE",
    val squashMergeMessage: String = "COMMIT_MESSAGES",
    val mergeCommitTitle: String = "PR_TITLE",
    val mergeCommitMessage: String = "PR_TITLE",
) {
    fun toRequest() = GitHubRepositoryUpdateRequest(
        name = name.trim(),
        description = description,
        homepage = homepage,
        visibility = visibility,
        defaultBranch = defaultBranch.trim().ifBlank { "main" },
        hasIssues = hasIssues,
        hasProjects = hasProjects,
        hasWiki = hasWiki,
        hasDiscussions = hasDiscussions,
        hasDownloads = hasDownloads,
        allowSquashMerge = allowSquashMerge,
        allowMergeCommit = allowMergeCommit,
        allowRebaseMerge = allowRebaseMerge,
        allowAutoMerge = allowAutoMerge,
        deleteBranchOnMerge = deleteBranchOnMerge,
        squashMergeTitle = squashMergeTitle,
        squashMergeMessage = squashMergeMessage,
        mergeCommitTitle = mergeCommitTitle,
        mergeCommitMessage = mergeCommitMessage,
    )
}

data class GitHubRepositoryEditState(
    val form: GitHubRepositoryEditForm = GitHubRepositoryEditForm(),
    val saving: Boolean = false,
    val saved: GitHubRepository? = null,
    val error: String? = null,
)

class GitHubRepositoryEditViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GitHubRepositoryGateway(CredentialSecurityStore(application))
    private val workspaceStore = GitHubWorkspaceStore(application)
    private val localLinkStore = LocalGitHubRepositoryLinkStore(application)
    private val buildSettingsStore = GitHubBuildSettingsStore(application)

    var state by mutableStateOf(GitHubRepositoryEditState())
        private set

    private var source: GitHubRepository? = null

    fun load(repository: GitHubRepository) {
        if (source?.id == repository.id && state.form.name == repository.name) return
        source = repository
        state = GitHubRepositoryEditState(
            form = GitHubRepositoryEditForm(
                name = repository.name,
                description = repository.description,
                homepage = repository.homepage,
                visibility = if (repository.isPrivate) "private" else "public",
                defaultBranch = repository.defaultBranch,
                hasIssues = repository.hasIssues,
                hasProjects = repository.hasProjects,
                hasWiki = repository.hasWiki,
                hasDiscussions = repository.hasDiscussions,
                hasDownloads = repository.hasDownloads,
                allowSquashMerge = repository.allowSquashMerge,
                allowMergeCommit = repository.allowMergeCommit,
                allowRebaseMerge = repository.allowRebaseMerge,
                allowAutoMerge = repository.allowAutoMerge,
                deleteBranchOnMerge = repository.deleteBranchOnMerge,
                squashMergeTitle = repository.squashMergeTitle,
                squashMergeMessage = repository.squashMergeMessage,
                mergeCommitTitle = repository.mergeCommitTitle,
                mergeCommitMessage = repository.mergeCommitMessage,
            ),
        )
    }

    fun update(form: GitHubRepositoryEditForm) {
        state = state.copy(form = form, error = null, saved = null)
    }

    fun save() {
        val sourceRepository = source ?: return
        if (state.saving) return
        state = state.copy(saving = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = gateway.updateRepository(
                owner = sourceRepository.owner,
                repository = sourceRepository.name,
                request = state.form.toRequest(),
            )) {
                is GitHubRepositoryResult.Success -> {
                    if (!sourceRepository.name.equals(result.repository.name, ignoreCase = true)) {
                        workspaceStore.renameRepository(sourceRepository.owner, sourceRepository.name, result.repository.name)
                        localLinkStore.renameRepository(sourceRepository.owner, sourceRepository.name, result.repository.name)
                        buildSettingsStore.renameRepository(sourceRepository.owner, sourceRepository.name, result.repository.name)
                        GitHubPendingChanges.renameRepository(sourceRepository.owner, sourceRepository.name, result.repository.name)
                    }
                    state = state.copy(saving = false, saved = result.repository)
                }
                is GitHubRepositoryResult.Failure -> {
                    state = state.copy(saving = false, error = result.message)
                }
            }
        }
    }
}
