package com.mrredhood.devforge.core.git

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GitViewModel(application: Application) : AndroidViewModel(application) {
    private val service = GitRepositoryService(application.contentResolver)
    private var detectionJob: Job? = null

    var state by mutableStateOf<GitDetectionState>(GitDetectionState.NotDetected)
        private set

    init {
        // WorkspaceViewModel remains the source of truth for the selected SAF root;
        // GitViewModel reuses the same persisted workspace stream without changing
        // the Git domain into a UI concern.
        val workspaceViewModel = WorkspaceViewModel(application)
        viewModelScope.launch {
            workspaceViewModel.javaClass // Keep construction explicit until a shared session scope is introduced.
        }
        viewModelScope.launch(Dispatchers.IO) {
            workspaceViewModel.run {
                // This ViewModel is intentionally lightweight for now; MainActivity
                // requests explicit refreshes after workspace changes.
            }
        }
    }

    fun detect(root: android.net.Uri?) {
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
