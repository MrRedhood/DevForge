package com.mrredhood.devforge.core.agent

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Read-only observer for AI-managed agent execution.
 *
 * No user control for launch, replay, pause, resume, cancellation or model selection
 * is exposed here. The UI is only a live view of AI-managed task state.
 */
class AgentActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val durable = DurableStateRepository(DevForgeDatabase.get(application))
    private var tasksJob: Job? = null

    var workspaceId by mutableStateOf<String?>(null)
        private set
    var workspaceName by mutableStateOf<String?>(null)
        private set
    var tasks by mutableStateOf<List<com.mrredhood.devforge.core.storage.AgentTaskEntity>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            workspaceRepository.activeWorkspace.collectLatest { workspace ->
                tasksJob?.cancel()
                workspaceId = workspace?.id
                workspaceName = workspace?.name
                tasks = emptyList()
                if (workspace == null) return@collectLatest
                tasksJob = launch {
                    durable.observeAgentTasks(workspace.id).collect { tasks = it }
                }
            }
        }
    }

    override fun onCleared() {
        tasksJob?.cancel()
        super.onCleared()
    }
}
