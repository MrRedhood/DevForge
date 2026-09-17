package com.mrredhood.devforge.core.git

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GitDiffViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaces = WorkspaceDatabaseRepository(application)
    private val repositoryService = GitRepositoryService(application.contentResolver)
    private val diffService = GitDiffService(application.contentResolver)
    private var loadJob: Job? = null

    var documents by mutableStateOf<List<GitDiffDocument>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            workspaces.activeWorkspace.collectLatest { workspace ->
                load(workspace?.treeUri)
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        viewModelScope.launch {
            val workspace = workspaces.activeWorkspace.value
            load(workspace?.treeUri)
        }
    }

    private fun load(root: android.net.Uri?) {
        loadJob?.cancel()
        documents = emptyList()
        message = null
        if (root == null) {
            message = "Choose a workspace to inspect Git diffs."
            return
        }
        isLoading = true
        loadJob = viewModelScope.launch {
            when (val state = repositoryService.detect(root)) {
                is GitDetectionState.Detected -> {
                    val result = diffService.compute(root, state.repository.gitDirectoryUri, state.repository.headRevision)
                    documents = result
                    if (result.isEmpty()) message = "No bounded text diffs were found in the current worktree."
                }
                GitDetectionState.NotDetected -> message = "No Git repository was detected in the active workspace."
                GitDetectionState.Detecting -> Unit
                is GitDetectionState.Unsupported -> message = state.reason
            }
            isLoading = false
        }
    }
}
