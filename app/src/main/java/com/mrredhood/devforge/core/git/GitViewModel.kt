package com.mrredhood.devforge.core.git

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GitViewModel(application: Application) : AndroidViewModel(application) {
    private val service = GitRepositoryService(application.contentResolver)
    private val workspaces = WorkspaceDatabaseRepository(application)
    private var detectionJob: Job? = null

    var state by mutableStateOf<GitDetectionState>(GitDetectionState.NotDetected)
        private set

    init {
        viewModelScope.launch {
            workspaces.activeWorkspace.collectLatest { workspace ->
                detect(workspace?.treeUri)
            }
        }
    }

    fun detect(root: Uri?) {
        detectionJob?.cancel()
        if (root == null) {
            state = GitDetectionState.NotDetected
            return
        }
        state = GitDetectionState.Detecting
        detectionJob = viewModelScope.launch {
            state = service.detect(root)
        }
    }
}
