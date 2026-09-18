package com.mrredhood.devforge.core.agent

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.ai.AIProvider
import com.mrredhood.devforge.core.ai.AISettingsRepository
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AgentCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val settings = AISettingsRepository(application)
    private val durable = DurableStateRepository(com.mrredhood.devforge.core.storage.DevForgeDatabase.get(application))
    private val coordinator = ParallelAgentCoordinator(application)
    private var tasksJob: Job? = null

    var workspaceId by mutableStateOf<String?>(null)
        private set
    var workspaceName by mutableStateOf<String?>(null)
        private set
    var tasks by mutableStateOf<List<com.mrredhood.devforge.core.storage.AgentTaskEntity>>(emptyList())
        private set
    var title by mutableStateOf("")
    var instruction by mutableStateOf("")
    var provider by mutableStateOf(settings.selectedProvider())
        private set
    var modelId by mutableStateOf(settings.selectedModelId(provider).orEmpty())
        private set
    var modelName by mutableStateOf(modelId)
    var scopeText by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
        private set
    var assigning by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            workspaceRepository.activeWorkspace.collectLatest { workspace ->
                tasksJob?.cancel()
                workspaceId = workspace?.id
                workspaceName = workspace?.name
                tasks = emptyList()
                if (workspace == null) return@collectLatest
                coordinator.recoverWorkspace(workspace.id)
                tasksJob = launch {
                    durable.observeAgentTasks(workspace.id).collect { values -> tasks = values }
                }
            }
        }
    }

    fun selectProvider(value: AIProvider) {
        provider = value
        modelId = settings.selectedModelId(value).orEmpty()
        modelName = modelId
    }

    fun updateModelId(value: String) {
        modelId = value
        if (modelName.isBlank() || modelName == modelId.dropLast(0)) modelName = value
    }

    fun assign() {
        val workspace = workspaceId
        if (workspace == null) { message = "Choose a workspace before assigning an agent."; return }
        if (assigning) return
        val normalizedModel = modelId.trim()
        if (normalizedModel.isBlank()) { message = "Enter a model ID for this agent."; return }
        val normalizedScope = runCatching {
            val values = scopeText.split(',').map(String::trim).filter(String::isNotBlank)
            WorkspacePathScope(if (values.isEmpty()) listOf("") else values)
        }.getOrElse { error -> message = error.message ?: "Invalid agent path scope."; return }
        assigning = true
        message = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                coordinator.assign(
                    AgentAssignment(
                        workspaceId = workspace,
                        title = title.ifBlank { "Agent " + (tasks.size + 1) },
                        instruction = instruction,
                        model = AgentModelBinding(provider, normalizedModel, modelName.ifBlank { normalizedModel }),
                        pathScope = normalizedScope,
                    )
                )
            }.onSuccess { taskId ->
                launch(Dispatchers.Main.immediate) {
                    title = ""; instruction = ""; scopeText = ""; assigning = false
                    message = "Agent assigned: " + taskId.take(8)
                }
            }.onFailure { error ->
                launch(Dispatchers.Main.immediate) { assigning = false; message = error.message ?: "Unable to assign agent." }
            }
        }
    }

    fun pause(taskId: String) { viewModelScope.launch(Dispatchers.IO) { coordinator.pause(taskId) } }
    fun resume(taskId: String) { viewModelScope.launch(Dispatchers.IO) { coordinator.resume(taskId) } }
    fun cancel(taskId: String) { viewModelScope.launch(Dispatchers.IO) { coordinator.cancel(taskId) } }
    fun clearMessage() { message = null }

    override fun onCleared() {
        tasksJob?.cancel()
        coordinator.close()
        super.onCleared()
    }
}
