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

data class GitHubRepositoryCreationForm(
    val name: String = "",
    val description: String = "",
    val homepage: String = "",
    val private: Boolean = true,
    val autoInit: Boolean = true,
    val gitignoreTemplate: String = "",
    val licenseTemplate: String = "",
    val hasIssues: Boolean = true,
    val hasProjects: Boolean = true,
    val hasWiki: Boolean = true,
    val hasDiscussions: Boolean = false,
    val isTemplate: Boolean = false,
    val defaultBranch: String = "main",
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
    val teamId: String = "",
    val customPropertiesJson: String = "",
)

data class GitHubRepositoryCreationState(
    val form: GitHubRepositoryCreationForm = GitHubRepositoryCreationForm(),
    val organizations: List<String> = emptyList(),
    val isLoadingOrganizations: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val created: GitHubRepository? = null,
)

class GitHubRepositoryCreationViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GitHubRepositoryGateway(CredentialSecurityStore(application))

    var state by mutableStateOf(GitHubRepositoryCreationState())
        private set

    init {
        state = state.copy(isLoadingOrganizations = true)
        viewModelScope.launch(Dispatchers.IO) {
            val organizations = gateway.listOrganizations().getOrDefault(emptyList())
            launch(Dispatchers.Main.immediate) {
                state = state.copy(
                    organizations = organizations,
                    isLoadingOrganizations = false,
                )
            }
        }
    }

    fun update(form: GitHubRepositoryCreationForm) {
        state = state.copy(form = form, error = null, created = null)
    }

    fun create() {
        val current = state.form
        val normalizedName = current.name.trim()
        val normalizedDescription = current.description.trim()
        val normalizedHomepage = current.homepage.trim()
        val normalizedBranch = current.defaultBranch.trim().ifBlank { "main" }

        if (normalizedName.isBlank()) {
            state = state.copy(error = "Repository name is required.")
            return
        }
        if (!Regex("^[A-Za-z0-9_.-]{1,100}$").matches(normalizedName)) {
            state = state.copy(error = "Use only letters, numbers, dots, hyphens, and underscores.")
            return
        }
        if (!Regex("^[A-Za-z0-9._/-]{1,255}$").matches(normalizedBranch)) {
            state = state.copy(error = "Default branch name is invalid.")
            return
        }

        val normalizedTeamId = current.teamId.trim().takeIf { it.isNotBlank() }?.toLongOrNull()
        if (current.teamId.isNotBlank() && normalizedTeamId == null) {
            state = state.copy(error = "Team ID must be a numeric GitHub team ID.")
            return
        }
        if (current.organization != null && current.organization !in state.organizations) {
            state = state.copy(error = "Selected organization is not available for this account.")
            return
        }
        val properties = current.customPropertiesJson.trim()
        if (properties.isNotBlank()) {
            runCatching { org.json.JSONObject(properties) }.getOrElse {
                state = state.copy(error = "Custom properties must be a valid JSON object.")
                return
            }
        }

        val squashTitle = current.squashMergeTitle.trim().uppercase()
        val squashMessage = current.squashMergeMessage.trim().uppercase()
        val validSquashCombination = when (squashTitle to squashMessage) {
            "PR_TITLE" to "PR_BODY",
            "PR_TITLE" to "BLANK",
            "PR_TITLE" to "COMMIT_MESSAGES",
            "COMMIT_OR_PR_TITLE" to "COMMIT_MESSAGES" -> true
            else -> false
        }
        if (!validSquashCombination) {
            state = state.copy(error = "Select a valid GitHub squash merge title/message combination.")
            return
        }

        val mergeTitle = current.mergeCommitTitle.trim().uppercase()
        val mergeMessage = current.mergeCommitMessage.trim().uppercase()
        if (mergeTitle !in setOf("PR_TITLE", "MERGE_MESSAGE") ||
            mergeMessage !in setOf("PR_BODY", "PR_TITLE", "BLANK")) {
            state = state.copy(error = "Select a valid GitHub merge commit title/message combination.")
            return
        }

        state = state.copy(isCreating = true, error = null, created = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = gateway.createRepository(
                GitHubRepositoryCreationRequest(
                    name = normalizedName,
                    description = normalizedDescription,
                    homepage = normalizedHomepage,
                    private = current.private,
                    autoInit = current.autoInit,
                    gitignoreTemplate = current.gitignoreTemplate.trim(),
                    licenseTemplate = current.licenseTemplate.trim(),
                    hasIssues = current.hasIssues,
                    hasProjects = current.hasProjects,
                    hasWiki = current.hasWiki,
                    hasDiscussions = current.hasDiscussions,
                    isTemplate = current.isTemplate,
                    defaultBranch = normalizedBranch,
                    hasDownloads = current.hasDownloads,
                    allowSquashMerge = current.allowSquashMerge,
                    allowMergeCommit = current.allowMergeCommit,
                    allowRebaseMerge = current.allowRebaseMerge,
                    allowAutoMerge = current.allowAutoMerge,
                    mergeCommitMessage = mergeMessage,
                    visibility = current.visibility,
                    deleteBranchOnMerge = current.deleteBranchOnMerge,
                    squashMergeTitle = squashTitle,
                    squashMergeMessage = squashMessage,
                    mergeCommitTitle = mergeTitle,
                    organization = current.organization,
                    teamId = normalizedTeamId,
                    customPropertiesJson = properties,
                ),
            )
            launch(Dispatchers.Main.immediate) {
                when (result) {
                    is GitHubRepositoryResult.Success -> state = state.copy(
                        isCreating = false,
                        error = null,
                        created = result.repository,
                    )
                    is GitHubRepositoryResult.Failure -> state = state.copy(
                        isCreating = false,
                        error = result.message,
                    )
                }
            }
        }
    }
}
