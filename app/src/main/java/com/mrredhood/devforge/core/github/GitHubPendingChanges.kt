package com.mrredhood.devforge.core.github

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GitHubPendingChangeBatch(
    val workspaceId: String,
    val owner: String,
    val repository: String,
    val branch: String,
    val folderPath: String,
    val changes: List<GitHubTreeChange>,
)

object GitHubPendingChanges {
    private val state = MutableStateFlow<Map<String, GitHubPendingChangeBatch>>(emptyMap())

    val batches: StateFlow<Map<String, GitHubPendingChangeBatch>> = state.asStateFlow()

    @Synchronized
    fun queue(
        workspaceId: String,
        owner: String,
        repository: String,
        branch: String,
        folderPath: String,
        changes: List<GitHubTreeChange>,
    ) {
        require(changes.isNotEmpty()) { "There are no GitHub changes to queue." }
        val existing = state.value[workspaceId]
        val merged = LinkedHashMap<String, GitHubTreeChange>()
        existing?.changes?.forEach { merged[it.path] = it }
        changes.forEach { merged[it.path] = it }
        state.value = state.value + (
            workspaceId to GitHubPendingChangeBatch(
                workspaceId = workspaceId,
                owner = owner,
                repository = repository,
                branch = branch,
                folderPath = folderPath.trim('/'),
                changes = merged.values.toList().takeLast(MAX_CHANGES),
            )
        )
    }

    fun batch(workspaceId: String): GitHubPendingChangeBatch? = state.value[workspaceId]

    @Synchronized
    fun clear(workspaceId: String) {
        state.value = state.value - workspaceId
    }

    @Synchronized
    fun clearMany(workspaceIds: Collection<String>) {
        if (workspaceIds.isEmpty()) return
        state.value = state.value - workspaceIds.toSet()
    }

    @Synchronized
    fun replaceBranch(workspaceId: String, branch: String) {
        val current = state.value[workspaceId] ?: return
        state.value = state.value + (workspaceId to current.copy(branch = branch.trim().ifBlank { "main" }))
    }

    private const val MAX_CHANGES = 120
}
