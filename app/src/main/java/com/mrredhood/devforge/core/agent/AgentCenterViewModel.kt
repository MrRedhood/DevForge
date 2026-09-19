package com.mrredhood.devforge.core.agent

import android.app.Application
import com.mrredhood.devforge.DevForgeApplication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.ai.AIProvider
import com.mrredhood.devforge.core.ai.AISettingsRepository
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.ModelCatalogService
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

data class AgentLaunchDraft(
    val id: String,
    val profileId: String,
    val title: String,
    val instruction: String,
    val provider: AIProvider,
    val modelId: String,
    val modelName: String,
)

class AgentCenterViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val MAX_LAUNCH_AGENTS = 10
    }

    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val settings = AISettingsRepository(application)
    private val catalogService = ModelCatalogService()
    private val profileRepository = AgentProfileRepository()
    private val database = com.mrredhood.devforge.core.storage.DevForgeDatabase.get(application)
    private val durable = DurableStateRepository(database)
    private val agentRuntime = (application as DevForgeApplication).agentRuntime
    private val coordination = AgentCoordinationService(database)
    private var tasksJob: Job? = null
    private var memoryJob: Job? = null
    private var handoffsJob: Job? = null
    private var leaseRefreshJob: Job? = null
    private var auditJob: Job? = null

    var workspaceId by mutableStateOf<String?>(null)
        private set
    var workspaceName by mutableStateOf<String?>(null)
        private set
    var tasks by mutableStateOf<List<com.mrredhood.devforge.core.storage.AgentTaskEntity>>(emptyList())
        private set
    var sharedMemory by mutableStateOf<List<com.mrredhood.devforge.core.storage.AgentSharedMemoryEntity>>(emptyList())
        private set
    var handoffs by mutableStateOf<List<com.mrredhood.devforge.core.storage.AgentHandoffEntity>>(emptyList())
        private set
    var fileLeases by mutableStateOf<List<com.mrredhood.devforge.core.storage.AgentFileLeaseEntity>>(emptyList())
        private set
    var auditEvents by mutableStateOf<List<AuditEventEntity>>(emptyList())
        private set
    var title by mutableStateOf("")
    var instruction by mutableStateOf("")
    var provider by mutableStateOf(settings.selectedProvider())
        private set
    var modelId by mutableStateOf(settings.selectedModelId(provider).orEmpty())
        private set
    var modelName by mutableStateOf(modelId)
    var message by mutableStateOf<String?>(null)
        private set
    var assigning by mutableStateOf(false)
        private set
    var profiles by mutableStateOf<List<AgentProfile>>(profileRepository.list())
        private set
    var modelsByProvider by mutableStateOf<Map<AIProvider, List<AIModelInfo>>>(emptyMap())
        private set
    var loadingProviders by mutableStateOf<Set<AIProvider>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            workspaceRepository.activeWorkspace.collectLatest { workspace ->
                tasksJob?.cancel()
                memoryJob?.cancel()
                handoffsJob?.cancel()
                leaseRefreshJob?.cancel()
                auditJob?.cancel()
                workspaceId = workspace?.id
                workspaceName = workspace?.name
                tasks = emptyList()
                sharedMemory = emptyList()
                handoffs = emptyList()
                fileLeases = emptyList()
                auditEvents = emptyList()
                if (workspace == null) return@collectLatest
                agentRuntime.recoverWorkspace(workspace.id)
                tasksJob = launch {
                    durable.observeAgentTasks(workspace.id).collect { values -> tasks = values }
                }
                memoryJob = launch {
                    coordination.observeMemory(workspace.id).collect { values -> sharedMemory = values }
                }
                handoffsJob = launch {
                    coordination.observeHandoffs(workspace.id).collect { values -> handoffs = values }
                }
                leaseRefreshJob = launch(Dispatchers.IO) {
                    while (true) {
                        val leases = coordination.listFileLeases(workspace.id)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            fileLeases = leases
                        }
                        kotlinx.coroutines.delay(5_000L)
                    }
                }
                auditJob = launch {
                    durable.observeWorkspaceAudit(workspace.id, 100).collect { values -> auditEvents = values }
                }
            }
        }
    }

    fun modelOptions(provider: AIProvider): List<AIModelInfo> = modelsByProvider[provider].orEmpty()

    fun loadModels(provider: AIProvider, force: Boolean = false) {
        if (loadingProviders.contains(provider)) return
        if (!force && !modelsByProvider[provider].isNullOrEmpty()) return
        val key = settings.getApiKey(provider) ?: return
        loadingProviders = loadingProviders + provider
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = catalogService.load(provider, key, settings.customBaseUrl(provider))
            launch(Dispatchers.Main.immediate) {
                val savedModelId = settings.selectedModelId(provider)
                val fallbackModels = if (
                    provider == AIProvider.OPENAI_COMPATIBLE &&
                    catalog?.models.isNullOrEmpty() &&
                    !savedModelId.isNullOrBlank()
                ) {
                    listOf(
                        AIModelInfo(
                            provider = provider,
                            id = savedModelId,
                            displayName = savedModelId,
                            metadataSource = "Saved custom model",
                        ),
                    )
                } else {
                    catalog?.models.orEmpty()
                }
                modelsByProvider = modelsByProvider + (provider to fallbackModels)
                loadingProviders = loadingProviders - provider
                val warning = catalog.warning
                message = if (fallbackModels.isNotEmpty() && catalog?.models.isNullOrEmpty() && savedModelId != null) {
                    (warning ?: "Custom model catalog unavailable.") + " Using the saved custom model ID."
                } else {
                    warning
                }
            }
        }
    }

    fun assignBatch(drafts: List<AgentLaunchDraft>) {
        val workspace = workspaceId
        if (workspace == null) {
            message = "Choose a workspace before launching agents."
            return
        }
        if (assigning) return
        val valid = drafts.filter { it.instruction.isNotBlank() }.take(MAX_LAUNCH_AGENTS)
        if (valid.isEmpty()) {
            message = "Add at least one agent task."
            return
        }
        val selectedProfiles = valid.associateWith { draft -> profiles.firstOrNull { it.id == draft.profileId } }
        if (selectedProfiles.values.any { it == null }) {
            message = "Select a valid agent profile for every task."
            return
        }
        if (valid.any { it.modelId.isBlank() }) {
            message = "Every agent needs a model."
            return
        }

        assigning = true
        message = null
        viewModelScope.launch(Dispatchers.IO) {
            val deferred = valid.map { draft ->
                async {
                    try {
                        val profile = selectedProfiles[draft]
                            ?: return@async Result.failure<Unit>(
                                IllegalStateException("Agent profile is no longer available."),
                            )
                        Result.success(agentRuntime.assign(
                        AgentAssignment(
                            workspaceId = workspace,
                            title = draft.title.ifBlank { profile.name }.take(200),
                            instruction = buildString {
                                append(profile.instructions)
                                append("\n\nUser-assigned task:\n")
                                append(draft.instruction.take(60_000))
                            },
                            model = AgentModelBinding(
                                draft.provider,
                                draft.modelId.trim(),
                                draft.modelName.ifBlank { draft.modelId.trim() },
                            ),
                            pathScope = WorkspacePathScope(),
                            access = profile.access,
                        )
                        ))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Result.failure<Unit>(error)
                    }
                }
            }
            val results = deferred.awaitAll()
            val successes = results.count { it.isSuccess }
            val failures = results.mapNotNull { it.exceptionOrNull()?.message }.take(2)
            launch(Dispatchers.Main.immediate) {
                assigning = false
                message = if (failures.isEmpty()) {
                    "Started " + successes + " agent" + (if (successes == 1) "" else "s") + "."
                } else {
                    "Started " + successes + " agent" + (if (successes == 1) "" else "s") + "; " + failures.joinToString(" · ")
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
        modelName = value
    }

    fun assign() {
        val workspace = workspaceId
        if (workspace == null) { message = "Choose a workspace before assigning an agent."; return }
        if (assigning) return
        val normalizedModel = modelId.trim()
        if (normalizedModel.isBlank()) { message = "Enter a model ID for this agent."; return }
        val normalizedScope = WorkspacePathScope()
        assigning = true
        message = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val taskId = agentRuntime.assign(
                    AgentAssignment(
                        workspaceId = workspace,
                        title = title.ifBlank { "Agent " + (tasks.size + 1) },
                        instruction = instruction,
                        model = AgentModelBinding(provider, normalizedModel, modelName.ifBlank { normalizedModel }),
                        pathScope = normalizedScope,
                    )
                )
                launch(Dispatchers.Main.immediate) {
                    title = ""; instruction = ""; assigning = false
                    message = "Agent assigned: " + taskId.take(8)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                launch(Dispatchers.Main.immediate) {
                    assigning = false
                    message = error.message ?: "Unable to assign agent."
                }
            }
        }
    }

    fun pause(taskId: String) { viewModelScope.launch(Dispatchers.IO) { agentRuntime.pause(taskId) } }
    fun resume(taskId: String) { viewModelScope.launch(Dispatchers.IO) { agentRuntime.resume(taskId) } }
    fun cancel(taskId: String) { viewModelScope.launch(Dispatchers.IO) { agentRuntime.cancel(taskId) } }
    fun clearMessage() { message = null }

    override fun onCleared() {
        tasksJob?.cancel()
        memoryJob?.cancel()
        handoffsJob?.cancel()
        leaseRefreshJob?.cancel()
        auditJob?.cancel()
        super.onCleared()
    }
}
