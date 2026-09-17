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
    private val repositoryService = GitRepositoryService(application.contentResolver)
    private val statusService = GitWorkspaceStatusService(application.contentResolver)
    private val workspaces = WorkspaceDatabaseRepository(application)
    private var detectionJob: Job? = null
    private var statusJob: Job? = null

    var state by mutableStateOf<GitDetectionState>(GitDetectionState.NotDetected)
        private set
    var workspaceStatus by mutableStateOf<GitWorkspaceStatus?>(null)
        private set
    var isInspectingStatus by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            workspaces.activeWorkspace.collectLatest { workspace -> detect(workspace?.treeUri) }
        }
    }

    fun detect(root: Uri?) {
        detectionJob?.cancel()
        statusJob?.cancel()
        workspaceStatus = null
        if (root == null) {
            state = GitDetectionState.NotDetected
            return
        }
        state = GitDetectionState.Detecting
        detectionJob = viewModelScope.launch {
            val detected = repositoryService.detect(root)
            state = detected
            if (detected is GitDetectionState.Detected) inspectWorkspace(root)
        }
    }

    fun inspectWorkspace(root: Uri? = activeRoot()) {
        statusJob?.cancel()
        if (root == null) {
            workspaceStatus = null
            return
        }
        isInspectingStatus = true
        statusJob = viewModelScope.launch {
            workspaceStatus = statusService.inspect(root)
            isInspectingStatus = false
        }
    }

    private fun activeRoot(): Uri? = (state as? GitDetectionState.Detected)?.repository?.rootUri
}
