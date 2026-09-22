package com.mrredhood.devforge.core.ai

import android.app.Application
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.mrredhood.devforge.DevForgeApplication
import com.mrredhood.devforge.core.agent.AgentAccess
import com.mrredhood.devforge.core.agent.AgentAssignment
import com.mrredhood.devforge.core.agent.AgentModelBinding
import com.mrredhood.devforge.core.agent.AgentTaskStep
import com.mrredhood.devforge.core.agent.AgentToolId
import com.mrredhood.devforge.core.agent.AgentSquadPlan
import com.mrredhood.devforge.core.agent.AgentSquadPlanner
import com.mrredhood.devforge.core.agent.AgentTaskStatus
import com.mrredhood.devforge.core.agent.ToolSettingsStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.ChatMessageEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import com.mrredhood.devforge.core.workspace.WorkspaceContextService
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import com.mrredhood.devforge.core.github.GitHubFileResult
import com.mrredhood.devforge.core.github.GitHubContentsResult
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceUris
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.ai.workflow.AiActivity
import com.mrredhood.devforge.core.ai.workflow.AiActivityKind
import com.mrredhood.devforge.core.ai.workflow.AiActivityStatus
import com.mrredhood.devforge.core.ai.workflow.AiWorkflowEngine
import com.mrredhood.devforge.core.ai.workflow.AiWorkflowPhase
import com.mrredhood.devforge.core.ai.workflow.AiWorkflowSnapshot
import com.mrredhood.devforge.core.settings.AiRoutingMode
import com.mrredhood.devforge.core.settings.DevForgeSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.withContext

class AIChatViewModel(application: Application) : AndroidViewModel(application) {
    private val attachmentPermissionsOwned = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val settings = AISettingsRepository(application)
    private val appSettings = DevForgeSettingsRepository(application)
    private val catalogService = ModelCatalogService()
    private val resolver = application.contentResolver
    private val chatGateway = AIChatGateway(
        attachmentAdapters = mapOf(
            AIProvider.GEMINI to GeminiProviderAttachmentAdapter(resolver),
            AIProvider.OPENROUTER to OpenRouterProviderAttachmentAdapter(resolver),
        ),
    )
    private val database = DevForgeDatabase.get(application)
    private val chatRepository = ChatRepository(database.chatSessionDao(), database.chatMessageDao())
    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val workspaceContextService = WorkspaceContextService(application)
    private val githubStore = GitHubWorkspaceStore(application)
    private val githubGateway = GitHubRepositoryGateway(CredentialSecurityStore(application))
    private val toolOrchestrator = ChatToolOrchestrator(application, chatGateway)
    private val toolSettings = ToolSettingsStore(application)
    private val truthService = AiTruthService(application)
    private val aiWorkflowEngine = AiWorkflowEngine(application)
    private var messageJob: Job? = null
    private var sendJob: Job? = null
    private var workspaceJob: Job? = null
    private var selectionJob: Job? = null
    private var selectionGeneration = 0L
    private var modelLoadJob: Job? = null
    private var modelLoadGeneration = 0L
    private var workspaceRoot: Uri? = null
    private val activeAgentTaskIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var generationId = 0L

    var provider by mutableStateOf(settings.selectedProvider())
        private set
    var models by mutableStateOf<List<AIModelInfo>>(emptyList())
        private set
    var isLoadingModels by mutableStateOf(false)
        private set
    var modelError by mutableStateOf<String?>(null)
        private set
    var selectedModel by mutableStateOf<AIModelInfo?>(null)
        private set
    var activeFilter by mutableStateOf(ModelFilter.ALL)
        private set
    var modelQuery by mutableStateOf("")
    var isModelMenuOpen by mutableStateOf(false)
    var input by mutableStateOf("")
        private set
    var isSending by mutableStateOf(false)
        private set
    var streamingText by mutableStateOf("")
        private set
    var streamingAnimationKind by mutableStateOf(StreamingAnimationKind.HAMMER)
        private set
    var toolActivities by mutableStateOf<List<ChatToolActivity>>(emptyList())
        private set
    var attachments by mutableStateOf<List<ChatAttachment>>(emptyList())
        private set
    var sendError by mutableStateOf<String?>(null)
        private set
    var suggestions by mutableStateOf<List<AICommandDefinition>>(emptyList())
        private set
    var messages by mutableStateOf<List<ChatMessageEntity>>(emptyList())
        private set
    var activeSessionId by mutableStateOf<String?>(null)
        private set
    var workspaceId by mutableStateOf<String?>(null)
        private set
    var workspaceName by mutableStateOf<String?>(null)
        private set
    var apiKeyConfigured by mutableStateOf(settings.hasApiKey(provider))
        private set
    var editingMessageId by mutableStateOf<String?>(null)
        private set
    var agentRun by mutableStateOf<AgentRunState?>(null)
        private set
    var aiWorkflow by mutableStateOf<AiWorkflowSnapshot?>(aiWorkflowEngine.active())
        private set

    val isAgentWorkInProgress: Boolean
        get() = agentRun?.completedAtEpochMs == null

    val isEditingMessage: Boolean
        get() = editingMessageId != null

    val filteredModels: List<AIModelInfo>
        get() = models.asSequence()
            .filter { model ->
                when (activeFilter) {
                    ModelFilter.ALL -> true
                    ModelFilter.FREE -> model.priceClass == ModelPriceClass.FREE
                    ModelFilter.PAID -> model.priceClass == ModelPriceClass.PAID
                    ModelFilter.VOICE -> model.isVoiceCapable
                    ModelFilter.IMAGE -> model.isImageCapable
                    ModelFilter.VIDEO -> model.isVideoCapable
                    ModelFilter.AUDIO -> model.inputModalities.contains("audio") || model.outputModalities.contains("audio")
                    ModelFilter.EMBEDDING -> model.isEmbedding
                    ModelFilter.TOOLS -> model.supportsTools
                }
            }
            .filter { model ->
                val query = modelQuery.trim()
                query.isBlank() || model.displayName.contains(query, true) || model.id.contains(query, true)
            }
            .take(MAX_VISIBLE_MODELS)
            .toList()

    init {
        workspaceJob = viewModelScope.launch {
            workspaceRepository.activeWorkspace.collectLatest { workspace ->
                val staleAgentIds = activeAgentTaskIds.toList()
                activeAgentTaskIds.clear()
                staleAgentIds.forEach { taskId ->
                    launch(Dispatchers.IO) {
                        runCatching {
                            (getApplication<Application>() as? DevForgeApplication)?.agentRuntime?.cancel(taskId)
                        }
                    }
                }
                sendJob?.cancel()
                sendJob = null
                isSending = false
                streamingText = ""
                toolActivities = emptyList()
                workspaceId = workspace?.id
                workspaceName = workspace?.name
                editingMessageId = null
                workspaceRoot = workspace?.treeUri
                activeSessionId = null
                messages = emptyList()
                selectedModel?.let { model -> selectModelInternal(model) }
            }
        }
        val savedId = settings.selectedModelId(provider)
        if (!savedId.isNullOrBlank()) selectedModel = AIModelInfo(provider, savedId, savedId)
    }

    fun selectProvider(value: AIProvider) {
        if (provider == value) return
        selectionGeneration += 1
        selectionJob?.cancel()
        modelLoadGeneration += 1
        modelLoadJob?.cancel()
        modelLoadJob = null
        isLoadingModels = false
        messageJob?.cancel()
        messageJob = null
        provider = value
        settings.setSelectedProvider(value)
        apiKeyConfigured = settings.hasApiKey(value)
        models = emptyList()
        selectedModel = null
        activeSessionId = null
        messages = emptyList()
        modelError = null
        isModelMenuOpen = false
        activeFilter = ModelFilter.ALL
        modelQuery = ""
        sendError = null
        editingMessageId = null
        val savedId = settings.selectedModelId(value)
        if (!savedId.isNullOrBlank()) selectedModel = AIModelInfo(value, savedId, savedId)
        loadModels(force = true)
    }

    fun syncProviderFromSettings() {
        val persisted = settings.selectedProvider()
        if (persisted != provider) {
            selectProvider(persisted)
        }
    }

    fun loadModels(force: Boolean = false) {
        if (isLoadingModels) return
        if (settings.isApiKeyLocked(provider)) {
            apiKeyConfigured = true
            modelError = "Unlock protected credentials in Settings before loading models."
            return
        }
        val key = settings.getApiKey(provider)
        apiKeyConfigured = !key.isNullOrBlank()
        if (key.isNullOrBlank()) {
            modelError = "Save a ${provider.displayName} API key in Settings before loading models."
            return
        }
        if (!force && models.isNotEmpty()) return
        isLoadingModels = true
        modelError = null
        val requestProvider = provider
        val generation = ++modelLoadGeneration
        modelLoadJob?.cancel()
        modelLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val result = catalogService.load(requestProvider, key, settings.customBaseUrl(requestProvider))
            launch(Dispatchers.Main.immediate) {
                if (generation != modelLoadGeneration || provider != requestProvider) return@launch
                val savedModelId = settings.selectedModelId(requestProvider)
                val fallbackModels = if (
                    result.models.isEmpty() &&
                    !savedModelId.isNullOrBlank() &&
                    savedModelId.length <= 180 &&
                    Regex("^[A-Za-z0-9_.:/-]+$").matches(savedModelId) &&
                    !savedModelId.contains("..")
                ) {
                    listOf(
                        AIModelInfo(
                            provider = requestProvider,
                            id = savedModelId,
                            displayName = savedModelId,
                            inputModalities = setOf("text"),
                            outputModalities = setOf("text"),
                            metadataSource = "Saved model fallback",
                        ),
                    )
                } else {
                    result.models
                }
                models = fallbackModels
                modelError = if (fallbackModels !== result.models && result.warning != null) {
                    result.warning + " Using the saved model ID."
                } else {
                    result.warning
                }
                isLoadingModels = false
                selectedModel = AIModelRouter.choose(
                    models = fallbackModels,
                    savedModelId = savedModelId,
                    mode = appSettings.snapshot().aiRoutingMode,
                ) ?: selectedModel?.takeIf { it.provider == requestProvider }
                selectedModel?.let { selectModelInternal(it) }
            }
        }
    }

    fun refreshModels() = loadModels(force = true)
    fun setFilter(filter: ModelFilter) { activeFilter = filter }

    fun updateModelMenuOpen(open: Boolean) {
        isModelMenuOpen = open
        if (open) loadModels()
    }

    fun selectModel(model: AIModelInfo) {
        if (model.provider != provider) {
            modelError = "The selected model belongs to a different provider."
            return
        }
        selectedModel = model
        settings.setSelectedModelId(provider, model.id)
        isModelMenuOpen = false
        modelError = null
        selectModelInternal(model)
    }

    private fun selectModelInternal(model: AIModelInfo) {
        selectionGeneration += 1
        val generation = selectionGeneration
        selectionJob?.cancel()
        val scope = workspaceId ?: ChatRepository.GLOBAL_SCOPE
        selectionJob = viewModelScope.launch(Dispatchers.IO) {
            val enriched = catalogService.resolveMissingContext(model)
            if (generation != selectionGeneration) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (generation == selectionGeneration) {
                    selectedModel = enriched
                    models = models.map { if (it.provider == enriched.provider && it.id == enriched.id) enriched else it }
                }
            }
            val session = chatRepository.getOrCreateSession(scope, enriched)
            if (generation != selectionGeneration) return@launch
            messageJob?.cancel()
            messageJob = launch {
                chatRepository.observeMessages(session.sessionId).collectLatest { values ->
                    if (generation == selectionGeneration) {
                        launch(Dispatchers.Main.immediate) {
                            if (generation == selectionGeneration) messages = values
                        }
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                if (generation == selectionGeneration) activeSessionId = session.sessionId
            }
        }
    }

    fun beginEditMessage(message: ChatMessageEntity) {
        if (isSending || message.role != "user") return
        editingMessageId = message.messageId
        input = message.content.substringBefore("Device attachments:")
            .trimEnd()
            .ifBlank { message.content.substringBefore("Device attachment:").trimEnd() }
        suggestions = emptyList()
        sendError = null
    }

    fun cancelEditMessage() {
        editingMessageId = null
        input = ""
        suggestions = emptyList()
    }

    private suspend fun prepareEditedMessage(messageId: String, sessionId: String, replacement: String): Boolean {
        val message = chatRepository.getMessage(messageId) ?: return false
        chatRepository.editMessage(messageId, replacement)
        if (message.role == "user") {
            chatRepository.deleteMessagesAfter(sessionId, message.createdAtEpochMs)
        }
        return true
    }

    fun copyMessageText(message: ChatMessageEntity) {
        val clean = if (message.role == "user") {
            message.content.substringBefore("Device attachments:")
                .trimEnd()
                .ifBlank { message.content.substringBefore("Device attachment:").trimEnd() }
        } else message.content
        val clipboard = getApplication<Application>().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DevForge message", clean))
    }

    fun updateInput(value: String) {
        input = value
        suggestions = if (value.trimStart().startsWith("/")) {
            val token = value.trimStart().substringAfter('/').substringBefore(' ')
            AICommandRegistry.suggestions(token).take(MAX_COMMAND_SUGGESTIONS)
        } else emptyList()
        sendError = null
    }

    fun prepareBuildWithAiPrompt(goal: String) {
        val cleanGoal = goal.trim().take(MAX_AGENT_CHAT_INSTRUCTION_CHARS)
        if (cleanGoal.isBlank()) {
            sendError = "Describe what you want to build first."
            return
        }
        updateInput(
            "Build this project in the active DevForge workspace. " +
                "Start by creating a clear engineering plan, inspect the workspace before editing, execute the plan sequentially, " +
                "use internal agents only when useful, verify the result with tests/lint/build where applicable, and summarize everything that changed. " +
                "Do not create or require a GitHub repository for this task.\n\nProject goal: " + cleanGoal,
        )
    }

    fun selectCommand(command: AICommandDefinition) {
        updateInput("/${command.name} ")
        suggestions = emptyList()
    }

    fun submit() {
        if (isSending) return
        val raw = input.trim()
        val pendingAttachments = attachments
        if (raw.isBlank() && pendingAttachments.isEmpty()) return

        val authoritativeAnswer = if (editingMessageId == null && pendingAttachments.isEmpty()) {
            truthService.answerIfKnown(raw)
        } else null

        if (authoritativeAnswer != null) {
            val sessionId = activeSessionId ?: run {
                sendError = "Chat session is not ready yet."
                return
            }
            input = ""
            suggestions = emptyList()
            attachments = emptyList()
            toolActivities = emptyList()
            agentRun = null
            isSending = true
            streamingAnimationKind = StreamingAnimationKind.HAMMER
            streamingText = authoritativeAnswer.take(MAX_STREAM_VISIBLE_CHARS)
            sendError = null
            val requestGeneration = ++generationId
            sendJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    var workflowSnapshot = aiWorkflowEngine.start(
                        request = raw,
                        workspaceId = workspaceId,
                        workspaceName = workspaceName,
                    )
                    chatRepository.addMessage(sessionId, "user", raw)
                    workflowSnapshot = aiWorkflowEngine.phase(
                        workflowSnapshot,
                        AiWorkflowPhase.VERIFY,
                        "Answering from live DevForge tool registry",
                    )
                    chatRepository.addMessage(sessionId, "assistant", authoritativeAnswer)
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        workflowSnapshot,
                        workspaceRoot = workspaceRoot,
                        changedPaths = emptyList(),
                        summary = authoritativeAnswer,
                    )
                    withContext(Dispatchers.Main.immediate) {
                        if (requestGeneration == generationId) {
                            aiWorkflow = workflowSnapshot
                            streamingText = authoritativeAnswer.take(MAX_STREAM_VISIBLE_CHARS)
                        }
                    }
                } catch (error: Throwable) {
                    withContext(Dispatchers.Main.immediate) {
                        if (requestGeneration == generationId) {
                            sendError = error.message ?: "Unable to produce the authoritative answer."
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main.immediate) {
                        if (requestGeneration == generationId) {
                            streamingText = ""
                            isSending = false
                            if (sendJob === currentCoroutineContext()[Job]) sendJob = null
                        }
                    }
                }
            }
            return
        }

        val model = selectedModel ?: run {
            sendError = "Select a model first."
            return
        }
        val requestProvider = provider
        if (model.provider != requestProvider) {
            sendError = "The selected model belongs to a different provider. Select it again."
            selectedModel = null
            return
        }
        val sessionId = activeSessionId ?: run {
            sendError = "Chat session is not ready yet."
            return
        }
        val editingId = editingMessageId
        input = ""
        suggestions = emptyList()
        val submittedAttachments = pendingAttachments
        attachments = emptyList()
        streamingAnimationKind = StreamingAnimationKind.random()
        toolActivities = emptyList()
        agentRun = null
        isSending = true
        streamingText = ""
        sendError = null
        val requestGeneration = ++generationId
        sendJob = viewModelScope.launch(Dispatchers.IO) {
            var partialResponse = ""
            var retainAttachmentsForRetry = false
            var workflowSnapshot = aiWorkflowEngine.start(
                request = raw,
                workspaceId = workspaceId,
                workspaceName = workspaceName,
            )
            withContext(Dispatchers.Main.immediate) {
                aiWorkflow = workflowSnapshot
            }
            try {
                if (settings.isApiKeyLocked(requestProvider)) {
                    error("Unlock protected credentials in Settings before sending AI requests.")
                }
                val mentions = resolveMentionsForActiveWorkspace(raw)
                val parsed = AICommandRegistry.parse(raw)?.let { it.copy(mentions = mentions) }
                val finalInstruction = if (parsed != null) {
                    if (parsed.command.name == "help") AgentCommandCatalog.systemSummary() else parsed.toAgentInstruction()
                } else {
                    buildString {
                        append(raw)
                        if (mentions.isNotEmpty()) {
                            append("\n\nReferenced file context:\n")
                            mentions.forEach { mention ->
                                if (!mention.content.isNullOrBlank()) {
                                    append("--- @").append(mention.name).append(" ---\n")
                                    append(mention.content)
                                    append("\n")
                                }
                            }
                        }
                    }
                }
                val key = settings.getApiKey(requestProvider) ?: error("API key is not configured.")
                workflowSnapshot = aiWorkflowEngine.phase(
                    workflowSnapshot,
                    AiWorkflowPhase.PLAN,
                    "Building execution plan",
                )
                withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                val attachmentContext = prepareAttachmentContext(submittedAttachments)
                workflowSnapshot = aiWorkflowEngine.phase(
                    workflowSnapshot,
                    AiWorkflowPhase.INSPECT,
                    "Inspecting workspace context",
                )
                withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                val compactWorkspaceContext = workspaceId?.let { workspaceContextService.compactPrompt(it) }.orEmpty()
                // Repeat only the compact workspace identity. Rich workspace state and source code are fetched on demand.
                val effectiveBase = if (compactWorkspaceContext.isBlank()) {
                    finalInstruction
                } else {
                    compactWorkspaceContext + "\n\n" + finalInstruction
                }
                val effectiveInstruction = buildString {
                    append(if (attachmentContext.isBlank()) effectiveBase else effectiveBase + "\n\n" + attachmentContext)
                    append("\n\n")
                    append(truthService.groundingInstruction(raw))
                }
                val visibleUserMessage = raw.ifBlank {
                    submittedAttachments.joinToString(", ") { it.name }.ifBlank { "Attachment" }
                }
                if (editingId != null) {
                    check(submittedAttachments.isEmpty()) { "Attachments are not supported while editing a sent message." }
                    check(prepareEditedMessage(editingId, sessionId, visibleUserMessage)) {
                        "The user message is no longer available."
                    }
                    withContext(Dispatchers.Main.immediate) {
                        editingMessageId = null
                    }
                } else {
                    chatRepository.addMessage(sessionId, "user", visibleUserMessage, parsed?.command?.name)
                }
                val historySource = if (editingId != null) {
                    database.chatMessageDao()
                        .recentDescending(sessionId, ChatRepository.MAX_MESSAGES)
                        .asReversed()
                } else {
                    messages
                }
                val history = buildBoundedHistory(model, historySource, effectiveInstruction.length)
                val authoritativeAnswer = if (editingId == null && submittedAttachments.isEmpty()) {
                    truthService.answerIfKnown(raw)
                } else null
                if (authoritativeAnswer != null) {
                    partialResponse = authoritativeAnswer.take(MAX_STREAM_VISIBLE_CHARS)
                    withContext(Dispatchers.Main.immediate) { streamingText = partialResponse }
                    chatRepository.addMessage(sessionId, "assistant", authoritativeAnswer)
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        aiWorkflowEngine.phase(workflowSnapshot, AiWorkflowPhase.VERIFY, "Verifying authoritative DevForge state"),
                        workspaceRoot = workspaceRoot,
                        changedPaths = emptyList(),
                        summary = authoritativeAnswer,
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                } else if (parsed?.command?.name == "help") {
                    chatRepository.addMessage(sessionId, "assistant", effectiveInstruction)
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        aiWorkflowEngine.phase(workflowSnapshot, AiWorkflowPhase.VERIFY, "Verifying request"),
                        workspaceRoot = workspaceRoot,
                        changedPaths = emptyList(),
                        summary = effectiveInstruction,
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                } else if (toolSettings.enabledToolIds().isNotEmpty()) {
                    workflowSnapshot = aiWorkflowEngine.phase(
                        workflowSnapshot,
                        AiWorkflowPhase.EXECUTE,
                        "Executing bounded tools",
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                    val toolResult = toolOrchestrator.run(
                        model = model,
                        apiKey = key,
                        history = history,
                        instruction = effectiveInstruction,
                        attachments = submittedAttachments,
                        customBaseUrl = settings.customBaseUrl(requestProvider),
                        workspaceId = workspaceId,
                        onActivity = { activity ->
                            withContext(Dispatchers.Main.immediate) {
                                if (requestGeneration != generationId) return@withContext
                                val kind = when {
                                    activity.toolId.wireName.contains("search", true) -> AiActivityKind.SEARCH
                                    activity.toolId.wireName.contains("read", true) ||
                                        activity.toolId.wireName.contains("context", true) -> AiActivityKind.READ
                                    activity.toolId.wireName.contains("write", true) ||
                                        activity.toolId.wireName.contains("patch", true) ||
                                        activity.toolId.wireName.contains("create", true) -> AiActivityKind.WRITE
                                    activity.toolId.wireName.contains("delete", true) -> AiActivityKind.DELETE
                                    activity.toolId.wireName.contains("terminal", true) ||
                                        activity.toolId.wireName.contains("command", true) -> AiActivityKind.COMMAND
                                    else -> AiActivityKind.INFO
                                }
                                val status = when (activity.status) {
                                    ChatToolActivity.Status.RUNNING -> AiActivityStatus.RUNNING
                                    ChatToolActivity.Status.COMPLETED -> AiActivityStatus.COMPLETED
                                    ChatToolActivity.Status.FAILED -> AiActivityStatus.FAILED
                                }
                                workflowSnapshot = aiWorkflowEngine.activity(
                                    workflowSnapshot,
                                    AiActivity(
                                        id = activity.callId,
                                        kind = kind,
                                        status = status,
                                        title = activity.toolId.wireName,
                                        detail = activity.detail,
                                        createdAtEpochMs = System.currentTimeMillis(),
                                    ),
                                )
                                aiWorkflow = workflowSnapshot
                                val current = toolActivities.toMutableList()
                                val index = current.indexOfFirst { it.callId == activity.callId }
                                if (index >= 0) current[index] = activity else current += activity
                                toolActivities = current
                            }
                        },
                    )
                    partialResponse = toolResult.response.take(MAX_STREAM_VISIBLE_CHARS)
                    withContext(Dispatchers.Main.immediate) { streamingText = partialResponse }
                    chatRepository.addMessage(
                        sessionId,
                        "assistant",
                        toolResult.response,
                    )
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        aiWorkflowEngine.phase(workflowSnapshot, AiWorkflowPhase.VERIFY, "Verifying workspace state"),
                        workspaceRoot = workspaceRoot,
                        changedPaths = toolResult.activities
                            .filter { it.status == ChatToolActivity.Status.COMPLETED }
                            .map { it.detail }
                            .filter { it.contains("/") }
                            .take(20),
                        summary = toolResult.response,
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                } else if (truthService.requiresAuthoritativeEvidence(raw)) {
                    val response = "I cannot verify that current fact from DevForge's live state in this turn, so I will not guess. Enable the relevant DevForge tool/state lookup and ask again."
                    chatRepository.addMessage(sessionId, "assistant", response)
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        aiWorkflowEngine.phase(workflowSnapshot, AiWorkflowPhase.VERIFY, "Verification required"),
                        workspaceRoot = workspaceRoot,
                        changedPaths = emptyList(),
                        summary = response,
                    )
                    withContext(Dispatchers.Main.immediate) { streamingText = response; aiWorkflow = workflowSnapshot }
                } else if (!model.supportsStreaming) {
                    val response = chatGateway.send(
                        model,
                        key,
                        history,
                        effectiveInstruction,
                        submittedAttachments,
                        settings.customBaseUrl(requestProvider),
                    )
                    chatRepository.addMessage(sessionId, "assistant", response)
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        aiWorkflowEngine.phase(workflowSnapshot, AiWorkflowPhase.VERIFY, "Verifying workspace state"),
                        workspaceRoot = workspaceRoot,
                        changedPaths = emptyList(),
                        summary = response,
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                } else {
                    val builder = StringBuilder()
                    chatGateway.stream(
                        model,
                        key,
                        history,
                        effectiveInstruction,
                        submittedAttachments,
                        settings.customBaseUrl(requestProvider),
                    ).collect { chunk ->
                        builder.append(chunk)
                        partialResponse = builder.toString().take(MAX_STREAM_VISIBLE_CHARS)
                        val visible = partialResponse
                        launch(Dispatchers.Main.immediate) {
                            if (requestGeneration == generationId) streamingText = visible
                        }
                    }
                    val finalText = partialResponse.ifBlank { "The model returned an empty response." }
                    chatRepository.addMessage(sessionId, "assistant", finalText)
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        aiWorkflowEngine.phase(workflowSnapshot, AiWorkflowPhase.VERIFY, "Verifying workspace state"),
                        workspaceRoot = workspaceRoot,
                        changedPaths = emptyList(),
                        summary = finalText,
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                }
            } catch (cancelled: CancellationException) {
                retainAttachmentsForRetry = true
                workflowSnapshot = aiWorkflowEngine.fail(workflowSnapshot, "AI Mission cancelled.")
                withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                withContext(Dispatchers.Main.immediate) {
                    val merged = attachments.toMutableList()
                    submittedAttachments.forEach { attachment ->
                        if (merged.none { it.uri == attachment.uri } &&
                            merged.size < MAX_ATTACHMENTS &&
                            merged.sumOf { it.sizeBytes } + attachment.sizeBytes <= MAX_TOTAL_ATTACHMENT_BYTES
                        ) {
                            merged += attachment
                        }
                    }
                    attachments = merged
                }
                withContext(NonCancellable) {
                    if (partialResponse.isNotBlank()) {
                        chatRepository.addMessage(
                            sessionId,
                            "assistant",
                            partialResponse + "\n\n[Generation stopped]",
                        )
                    }
                }
            } catch (error: Throwable) {
                retainAttachmentsForRetry = true
                if (workflowSnapshot.status == AiWorkflowSnapshot.Status.RUNNING ||
                    workflowSnapshot.status == AiWorkflowSnapshot.Status.WAITING
                ) {
                    workflowSnapshot = aiWorkflowEngine.fail(
                        workflowSnapshot,
                        error.message ?: "AI Mission failed.",
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                }
                withContext(NonCancellable + Dispatchers.IO) {
                    cancelActiveAgentTasks()
                }
                withContext(Dispatchers.Main.immediate) {
                    val merged = attachments.toMutableList()
                    submittedAttachments.forEach { attachment ->
                        if (merged.none { it.uri == attachment.uri } &&
                            merged.size < MAX_ATTACHMENTS &&
                            merged.sumOf { it.sizeBytes } + attachment.sizeBytes <= MAX_TOTAL_ATTACHMENT_BYTES
                        ) {
                            merged += attachment
                        }
                    }
                    attachments = merged
                    sendError = error.message ?: "AI request failed. The attachments were kept so you can retry."
                }
            } finally {
                if (retainAttachmentsForRetry) {
                    val retainedUris = withContext(Dispatchers.Main.immediate) { attachments.map { it.uri }.toSet() }
                    submittedAttachments.filterNot { it.uri in retainedUris }.forEach { releaseAttachmentPermission(it.uri) }
                } else {
                    submittedAttachments.forEach { releaseAttachmentPermission(it.uri) }
                }
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (requestGeneration == generationId) {
                        streamingText = ""
                        isSending = false
                        if (sendJob === currentCoroutineContext()[Job]) sendJob = null
                    }
                }
            }
        }
    }

    private fun stopGenerationLocally() {
        val taskIds = activeAgentTaskIds.toList()
        generationId += 1
        toolActivities = emptyList()
        streamingText = ""
        isSending = false
        sendError = null
        activeAgentTaskIds.clear()
        val runningJob = sendJob
        sendJob = null
        if (taskIds.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                taskIds.forEach { taskId ->
                    runCatching {
                        (getApplication<Application>() as? DevForgeApplication)?.agentRuntime?.cancel(taskId)
                    }
                }
            }
        }
        runningJob?.cancel()
    }

    /** Stops only this Chat generation and any agent task created by this Chat turn. */
    fun stopGeneration() {
        if (isAgentWorkInProgress) return
        stopGenerationLocally()
    }

    private fun shouldDelegateToWorkspaceAgent(
        raw: String,
        parsed: AICommandInvocation?,
        currentWorkspaceId: String?,
    ): Boolean {
        if (currentWorkspaceId.isNullOrBlank()) return false
        if (parsed != null) return false
        val normalized = raw.trim().lowercase()
        val explanatory = normalized.startsWith("how ") ||
            normalized.startsWith("what ") ||
            normalized.startsWith("why ")
        if (explanatory) return false
        return MUTATION_INTENT.containsMatchIn(raw) ||
            implementationIntent.containsMatchIn(normalized)
    }

    private val implementationIntent = Regex(
        "\\b(implement|build|create|add|fix|refactor|migrate|optimize|update|modify|remove|rename|upgrade|release|prepare)\\b",
        RegexOption.IGNORE_CASE,
    )

    private suspend fun cancelActiveAgentTasks() {
        val taskIds = activeAgentTaskIds.toList()
        activeAgentTaskIds.clear()
        val runtime = (getApplication<Application>() as? DevForgeApplication)?.agentRuntime ?: return
        taskIds.forEach { taskId ->
            runCatching { runtime.cancel(taskId) }
        }
    }

    private fun buildAgentInstruction(
        raw: String,
        parsed: AICommandInvocation?,
        effectiveInstruction: String,
    ): String {
        val explicitGoal = parsed?.takeIf { it.command.name == "agent" }?.arguments?.trim().orEmpty()
        val goal = explicitGoal.ifBlank { raw.trim() }
        return buildString {
            append("Act as the coding agent for the current DevForge workspace. ")
            append("Execute the requested file and folder changes directly; do not merely describe them. ")
            append("Inspect the workspace first, choose exact workspace-relative paths, make the requested changes, and verify the resulting state.")
            append("\nUser goal: ").append(goal)
            if (effectiveInstruction != raw.trim() && explicitGoal.isBlank()) {
                append("\nAdditional workspace context:\n").append(effectiveInstruction.take(MAX_AGENT_CHAT_INSTRUCTION_CHARS))
            }
        }.take(MAX_AGENT_CHAT_INSTRUCTION_CHARS)
    }

    private suspend fun executeChatAgent(
        workspaceId: String,
        model: AIModelInfo,
        instruction: String,
    ): String {
        val runtime = (getApplication<Application>() as? DevForgeApplication)?.agentRuntime
            ?: error("The agent runtime is unavailable in this DevForge build.")
        val squadPlanner = AgentSquadPlanner(getApplication<Application>())
        val startedAt = System.currentTimeMillis()

        withContext(Dispatchers.Main.immediate) {
            agentRun = AgentRunState(
                goal = instruction,
                planItems = emptyList(),
                tasks = emptyList(),
                startedAtEpochMs = startedAt,
                completedAtEpochMs = null,
                planning = true,
            )
        }

        val squad = squadPlanner.plan(
            model = model,
            workspaceId = workspaceId,
            goal = instruction,
            customBaseUrl = settings.customBaseUrl(model.provider),
        )
        val planItems = squad.members.mapIndexed { index, member ->
            AgentRunPlanItem(
                id = "plan-$index",
                phase = member.phase,
                title = member.title,
                instruction = member.instruction,
                status = "Waiting",
                taskId = null,
            )
        }.toMutableList()

        publishAgentRun(
            goal = instruction,
            planItems = planItems,
            taskIds = emptyList(),
            startedAt = startedAt,
            planning = false,
        )

        var failureMessage: String? = null
        for (phase in squad.phases) {
            currentCoroutineContext().ensureActive()
            phase.forEach { member ->
                val planIndex = planItems.indexOfFirst { it.title == member.title && it.phase == member.phase && it.taskId == null }
                if (planIndex >= 0) planItems[planIndex] = planItems[planIndex].copy(status = "Starting")
            }
            publishAgentRun(
                goal = instruction,
                planItems = planItems,
                taskIds = activeAgentTaskIds.toList(),
                startedAt = startedAt,
                planning = false,
            )
            val results = coroutineScope {
                phase.map { member ->
                    async {
                        runCatching {
                            runtime.assign(
                                AgentAssignment(
                                    workspaceId = workspaceId,
                                    title = member.title,
                                    instruction = member.instruction,
                                    model = AgentModelBinding(model.provider, model.id, model.displayName),
                                    pathScope = com.mrredhood.devforge.core.security.WorkspacePathScope(),
                                    access = AgentAccess.CODING_DEFAULT,
                                ),
                            )
                        }
                    }
                }.awaitAll()
            }

            val phaseTaskIds = mutableListOf<String>()
            results.forEachIndexed { index, result ->
                val member = phase[index]
                val planIndex = planItems.indexOfFirst { it.title == member.title && it.phase == member.phase && it.taskId == null }
                result.onSuccess { taskId ->
                    phaseTaskIds += taskId
                    activeAgentTaskIds.add(taskId)
                    if (planIndex >= 0) {
                        planItems[planIndex] = planItems[planIndex].copy(status = "Running", taskId = taskId)
                    }
                }.onFailure { error ->
                    failureMessage = error.message ?: "Agent could not be deployed."
                    if (planIndex >= 0) {
                        planItems[planIndex] = planItems[planIndex].copy(status = "Failed")
                    }
                }
            }

            publishAgentRun(
                goal = instruction,
                planItems = planItems,
                taskIds = activeAgentTaskIds.toList(),
                startedAt = startedAt,
                planning = false,
            )

            if (failureMessage != null || phaseTaskIds.isEmpty()) break

            var phaseComplete = false
            while (!phaseComplete) {
                currentCoroutineContext().ensureActive()
                val snapshot = phaseTaskIds.mapNotNull { database.agentTaskDao().get(it) }
                publishAgentRun(
                    goal = instruction,
                    planItems = planItems,
                    taskIds = activeAgentTaskIds.toList(),
                    startedAt = startedAt,
                    planning = false,
                )
                phaseComplete = snapshot.size == phaseTaskIds.size &&
                    snapshot.all { it.status in AGENT_TERMINAL_STATUSES }
                if (!phaseComplete) delay(350)
            }

            val completedPhase = phaseTaskIds.mapNotNull { database.agentTaskDao().get(it) }
            completedPhase.forEach { task ->
                val planIndex = planItems.indexOfFirst { it.taskId == task.taskId }
                if (planIndex >= 0) {
                    planItems[planIndex] = planItems[planIndex].copy(
                        status = task.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercaseChar() },
                    )
                }
                activeAgentTaskIds.remove(task.taskId)
            }
            if (completedPhase.any { it.status != AgentTaskStatus.COMPLETED.name }) {
                failureMessage = completedPhase.firstOrNull { it.status != AgentTaskStatus.COMPLETED.name }
                    ?.errorMessage
                    ?: "One or more agents did not complete successfully."
                break
            }
        }

        val finishedAt = System.currentTimeMillis()
        val allTaskIds = planItems.mapNotNull { it.taskId }.distinct()
        publishAgentRun(
            goal = instruction,
            planItems = planItems,
            taskIds = allTaskIds,
            startedAt = startedAt,
            planning = false,
            completedAt = finishedAt,
        )

        val finalTasks = allTaskIds.mapNotNull { database.agentTaskDao().get(it) }
        val overview = buildAgentOverview(
            instruction = instruction,
            plan = squad,
            tasks = finalTasks,
            failureMessage = failureMessage,
        )
        withContext(Dispatchers.Main.immediate) {
            agentRun = agentRun?.copy(
                completedAtEpochMs = finishedAt,
                planning = false,
                overview = overview,
            )
        }
        activeAgentTaskIds.clear()
        return overview
    }

    private suspend fun publishAgentRun(
        goal: String,
        planItems: List<AgentRunPlanItem>,
        taskIds: List<String>,
        startedAt: Long,
        planning: Boolean,
        completedAt: Long? = null,
    ) {
        val tasks = taskIds.mapNotNull { database.agentTaskDao().get(it) }
        val snapshots = tasks.map { task -> snapshotAgentTask(task) }
        withContext(Dispatchers.Main.immediate) {
            agentRun = AgentRunState(
                goal = goal,
                planItems = planItems.toList(),
                tasks = snapshots,
                startedAtEpochMs = startedAt,
                completedAtEpochMs = completedAt,
                planning = planning,
            )
        }
    }

    private fun snapshotAgentTask(task: com.mrredhood.devforge.core.storage.AgentTaskEntity): AgentRunTaskSnapshot {
        val steps = runCatching {
            val activities = extractStepActivities(task.result)
            com.mrredhood.devforge.core.agent.AgentTaskPlanCodec.decode(task.payload).steps.mapIndexed { index, step ->
                AgentRunStepSnapshot(
                    index = index,
                    label = step.label,
                    toolId = step.toolId.wireName,
                    status = when {
                        index < task.currentStep -> "Done"
                        index == task.currentStep && task.status == AgentTaskStatus.RUNNING.name -> "Running"
                        index == task.currentStep && task.status == AgentTaskStatus.WAITING_APPROVAL.name -> "Approval"
                        index == task.currentStep && task.status == AgentTaskStatus.FAILED.name -> "Failed"
                        task.status == AgentTaskStatus.COMPLETED.name -> "Done"
                        else -> "Pending"
                    },
                    activity = activities.getOrNull(index),
                    detail = describeAgentStep(step),
                )
            }
        }.getOrDefault(emptyList())
        return AgentRunTaskSnapshot(
            taskId = task.taskId,
            title = task.title,
            status = task.status,
            provider = task.modelProviderId ?: "Unknown provider",
            model = task.modelName ?: task.modelId ?: "Unknown model",
            startedAtEpochMs = task.startedAtEpochMs ?: task.createdAtEpochMs,
            completedAtEpochMs = task.completedAtEpochMs,
            currentStep = task.currentStep,
            stepCount = task.stepCount,
            lastToolId = task.lastToolId,
            steps = steps,
            affectedPaths = extractAffectedPaths(task.result),
        )
    }

    private fun describeAgentStep(step: AgentTaskStep): String? {
        val args = runCatching { JSONObject(step.argumentsJson) }.getOrNull() ?: return null
        fun value(vararg keys: String): String? =
            keys.asSequence()
                .map { args.optString(it).trim() }
                .firstOrNull { it.isNotBlank() }

        val detail = when (step.toolId) {
            AgentToolId.SEARCH_WORKSPACE,
            AgentToolId.SEARCH_CONTENT,
            AgentToolId.FIND_FILES,
            -> value("query", "pattern")?.let { "Search: $it" }

            AgentToolId.READ_FILE,
            AgentToolId.LIST_FILES,
            AgentToolId.FILE_INFO,
            AgentToolId.COUNT_LINES,
            AgentToolId.HASH_FILE,
            AgentToolId.DIRECTORY_TREE,
            -> value("path")?.let { "Path: $it" }

            AgentToolId.PATCH_FILE,
            AgentToolId.WRITE_FILE,
            AgentToolId.CREATE_FILE,
            AgentToolId.CREATE_FOLDER,
            AgentToolId.DELETE_PATH,
            -> value("path")?.let { "Path: $it" }

            AgentToolId.RUN_COMMAND,
            -> value("command")?.let { "Command: $it" }

            AgentToolId.WEB_SEARCH,
            -> value("query")?.let { "Web search: $it" }

            AgentToolId.SCRAPE_URL,
            AgentToolId.FETCH_URL,
            AgentToolId.EXTRACT_LINKS,
            -> value("url")?.let { "URL: $it" }

            AgentToolId.GET_WORKSPACE_CONTEXT,
            AgentToolId.RETRIEVE_RELEVANT_CONTEXT,
            -> value("scope", "query")?.let { "Context: $it" }

            else -> value("summary", "expression")?.let { "Detail: $it" }
        }

        return detail?.let { com.mrredhood.devforge.core.security.SecretRedactor.redact(it, 240) }
    }

    private fun extractStepActivities(result: String?): List<String> =
        result.orEmpty()
            .split("\n\n")
            .mapNotNull { block ->
                block.lineSequence().firstOrNull()
                    ?.substringAfter(": ", missingDelimiterValue = "")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .take(12)

    private fun extractAffectedPaths(result: String?): List<String> =
        result.orEmpty()
            .lineSequence()
            .filter { it.trimStart().startsWith("receipt=", ignoreCase = true) }
            .mapNotNull { line ->
                runCatching {
                    val json = org.json.JSONObject(line.trim().removePrefix("receipt="))
                    val paths = json.optJSONArray("affectedPaths") ?: return@runCatching emptyList<String>()
                    buildList {
                        for (index in 0 until minOf(paths.length(), 20)) {
                            paths.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.getOrNull()
            }
            .flatten()
            .distinct()
            .take(40)
            .toList()

    private fun buildAgentOverview(
        instruction: String,
        plan: AgentSquadPlan,
        tasks: List<com.mrredhood.devforge.core.storage.AgentTaskEntity>,
        failureMessage: String?,
    ): String = buildString {
        append("## DevForge agent plan\n")
        plan.members.forEachIndexed { index, member ->
            append(index + 1).append(". Phase ").append(member.phase).append(" — ").append(member.title).append("\n")
        }
        append("\n## Agent overview\n")
        if (tasks.isEmpty()) {
            append("No agent task completed. ")
            append(failureMessage ?: "The plan could not be started.")
            return@buildString
        }
        tasks.forEach { task ->
            val elapsed = formatAgentDuration(task.startedAtEpochMs ?: task.createdAtEpochMs, task.completedAtEpochMs ?: System.currentTimeMillis())
            append("- **").append(task.title).append("** — ").append(task.status.replace('_', ' ').lowercase())
                .append(" · ").append(task.modelProviderId ?: "Unknown provider")
                .append(" / ").append(task.modelName ?: task.modelId ?: "Unknown model")
                .append(" · ").append(elapsed).append("\n")
            val steps = runCatching { com.mrredhood.devforge.core.agent.AgentTaskPlanCodec.decode(task.payload).steps }
                .getOrDefault(emptyList())
            steps.take(12).forEachIndexed { index, step ->
                append("  - ").append(index + 1).append(". ").append(step.label.take(160))
                    .append(" (").append(step.toolId.wireName).append(")\n")
            }
            val affected = extractAffectedPaths(task.result)
            if (affected.isNotEmpty()) {
                append("  - Changed: ").append(affected.joinToString(", ")).append("\n")
            }
            task.errorMessage?.let { append("  - Error: ").append(it).append("\n") }
        }
        failureMessage?.let { append("\nPlan stopped: ").append(it) }
    }

    private fun formatAgentDuration(start: Long, end: Long): String {
        val seconds = ((end - start).coerceAtLeast(0L) / 1000L)
        return if (seconds < 60L) seconds.toString() + "s" else (seconds / 60L).toString() + "m " + (seconds % 60L).toString() + "s"
    }

    fun isAttachmentTypeAvailable(type: ChatAttachmentType): Boolean = when (type) {
        ChatAttachmentType.PHOTO,
        ChatAttachmentType.VIDEO,
        ChatAttachmentType.AUDIO,
        -> provider == AIProvider.GEMINI || (provider == AIProvider.OPENROUTER && type == ChatAttachmentType.PHOTO)
        ChatAttachmentType.DOCUMENT,
        ChatAttachmentType.ANY_FILE,
        -> true
    }

    fun attachmentTypeUnavailableMessage(type: ChatAttachmentType): String =
        when (type) {
            ChatAttachmentType.PHOTO,
            ChatAttachmentType.VIDEO,
            ChatAttachmentType.AUDIO,
            -> "${type.label} attachments are not supported by ${provider.displayName}. Choose Gemini, or choose OpenRouter for images."
            ChatAttachmentType.DOCUMENT,
            ChatAttachmentType.ANY_FILE,
            -> "The selected provider may reject binary files it cannot transport; text-like files remain supported."
        }

    fun addAttachments(uris: List<Uri>, type: ChatAttachmentType) {
        if (uris.isEmpty()) return
        val requestProvider = provider
        viewModelScope.launch(Dispatchers.IO) {
            val failureMessages = mutableListOf<String>()
            val additions = uris.mapNotNull { uri ->
                runCatching {
                    val alreadyPersisted = resolver.persistedUriPermissions.any {
                        it.uri == uri && it.isReadPermission
                    }
                    if (!alreadyPersisted) {
                        runCatching {
                            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }.onSuccess {
                            attachmentPermissionsOwned.add(uri.toString())
                        }
                    }
                    val metadata = readAttachmentMetadata(uri, type.maxBytes)
                    require(type.accepts(metadata.mimeType, metadata.name)) {
                        "The selected item is not a supported ${type.label.lowercase()} attachment."
                    }
                    require(metadata.sizeBytes in 1..type.maxBytes) {
                        "The attachment exceeds the ${formatSize(type.maxBytes)} limit."
                    }
                    require(supportsProviderAttachment(requestProvider, metadata.name, metadata.mimeType)) {
                        "Binary attachments are not supported by ${requestProvider.displayName} for this file type."
                    }
                    ChatAttachment(uri, metadata.name, metadata.mimeType, metadata.sizeBytes, type)
                }.onFailure { error ->
                    error.message?.takeIf { it.isNotBlank() }?.let(failureMessages::add)
                }.getOrNull()
            }
            withContext(Dispatchers.Main.immediate) {
                val baseline = attachments
                val baselineUris = baseline.asSequence().map { it.uri }.toSet()
                var bounded = (baseline + additions).distinctBy { it.uri }
                while (bounded.size > MAX_ATTACHMENTS && bounded.isNotEmpty()) {
                    bounded = bounded.dropLast(1)
                }
                while (bounded.sumOf { it.sizeBytes } > MAX_TOTAL_ATTACHMENT_BYTES && bounded.isNotEmpty()) {
                    bounded = bounded.dropLast(1)
                }
                attachments = bounded
                val finalUris = bounded.asSequence().map { it.uri }.toSet()
                val finalUriStrings = finalUris.map(Uri::toString).toSet()
                additions.asSequence()
                    .map { it.uri }
                    .filter { it !in baselineUris && it.toString() !in finalUriStrings }
                    .distinct()
                    .forEach(::releaseAttachmentPermission)
                val accepted = additions.count { it.uri in finalUris && it.uri !in baselineUris }
                val rejected = uris.size - accepted
                if (rejected > 0) {
                    sendError = failureMessages.firstOrNull()
                        ?: (rejected.toString() + " attachment(s) were rejected by type, size, or pending limits.")
                }
            }
        }
    }

    fun removeAttachment(uri: Uri) {
        if (attachments.any { it.uri == uri }) {
            attachments = attachments.filterNot { it.uri == uri }
            releaseAttachmentPermission(uri)
        }
    }

    private fun readAttachmentMetadata(uri: Uri, maxBytes: Long): AttachmentMetadata {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "attachment" } ?: "attachment"
        var mime = resolver.getType(uri).orEmpty().trim()
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                        ?.take(MAX_ATTACHMENT_NAME_CHARS)
                        .orEmpty()
                        .ifBlank { name }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (mime.isBlank() || mime.equals("application/octet-stream", ignoreCase = true)) {
            mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                .orEmpty()
        }
        if (mime.isBlank()) mime = "application/octet-stream"
        if (size <= 0L || size > maxBytes) size = countBytesBounded(uri, maxBytes)
        require(size in 1..maxBytes)
        return AttachmentMetadata(name, mime, size)
    }

    private fun countBytesBounded(uri: Uri, maxBytes: Long): Long {
        var total = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (total <= maxBytes) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return total
            }
        } ?: return -1L
        return total
    }

    private suspend fun prepareAttachmentContext(values: List<ChatAttachment>): String {
        if (values.isEmpty()) return ""
        return buildString {
            var remainingTextBytes = MAX_ATTACHMENT_CONTEXT_BYTES
            append("Device attachments:")
            values.forEach { attachment ->
                append("\n- ").append(attachment.name)
                    .append(" · ").append(attachment.mimeType)
                    .append(" · ").append(formatSize(attachment.sizeBytes))
                val textAllowed = remainingTextBytes > 0 && (
                    attachment.mimeType.startsWith("text/") ||
                        attachment.mimeType == "application/json" ||
                        attachment.mimeType == "application/xml" ||
                        attachment.name.endsWith(".kt", true) ||
                        attachment.name.endsWith(".java", true) ||
                        attachment.name.endsWith(".js", true) ||
                        attachment.name.endsWith(".ts", true) ||
                        attachment.name.endsWith(".py", true) ||
                        attachment.name.endsWith(".md", true)
                )
                if (textAllowed) {
                    val snippet = readTextSnippet(attachment.uri, remainingTextBytes)
                    if (snippet.isNotBlank()) {
                        append("\n  Text content:\n").append(snippet)
                        remainingTextBytes -= snippet.toByteArray(Charsets.UTF_8).size.toLong()
                    }
                } else {
                    append("\n  Binary content will be uploaded using the selected provider attachment adapter when supported.")
                }
            }
        }.trimEnd()
    }
    private fun readTextSnippet(uri: Uri, maxBytes: Long): String {
        val limit = maxBytes.coerceAtMost(MAX_TEXT_ATTACHMENT_BYTES).toInt()
        val bytes = java.io.ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (bytes.size() < limit) {
                val read = input.read(buffer, 0, minOf(buffer.size, limit - bytes.size()))
                if (read < 0) break
                if (read == 0) continue
                bytes.write(buffer, 0, read)
            }
        } ?: return ""
        val data = bytes.toByteArray()
        if (data.any { it == 0.toByte() }) return ""
        return data.toString(Charsets.UTF_8).take(MAX_TEXT_ATTACHMENT_CHARS)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> (bytes / (1024L * 1024L)).toString() + " MB"
        bytes >= 1024L -> (bytes / 1024L).toString() + " KB"
        else -> bytes.toString() + " B"
    }

    private fun releaseAttachmentPermission(uri: Uri) {
        if (attachmentPermissionsOwned.remove(uri.toString())) {
            runCatching {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun reportAttachmentPickerError(error: Throwable?) {
        if (error is CancellationException) return
        val detail = error?.message?.takeIf { it.isNotBlank() }?.take(180)
        sendError = if (detail == null) {
            "Unable to open the attachment picker on this device."
        } else {
            "Unable to open the attachment picker: $detail"
        }
    }

    fun dismissError() { sendError = null }

    private suspend fun resolveMentionsForActiveWorkspace(message: String): List<FileMention> {
        val workspaceIdValue = workspaceId ?: return emptyList()
        val remote = githubStore.get(workspaceIdValue)
        if (remote == null) {
            return AICommandRegistry.resolveMentions(resolver, workspaceRoot, message)
        }

        val tokens = Regex("(?<!\\S)@([A-Za-z0-9_./\\-]+)").findAll(message)
            .map { it.groupValues[1] }
            .distinct()
            .take(8)
            .toList()
        if (tokens.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            tokens.mapNotNull { query ->
                val found = mutableListOf<com.mrredhood.devforge.core.github.GitHubContentEntry>()
                suspend fun visit(path: String) {
                    if (found.isNotEmpty()) return
                    when (val result = githubGateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                        is GitHubContentsResult.Success -> {
                            for (entry in result.entries) {
                                if (entry.type == "dir") {
                                    if (entry.name.equals(query, true) || entry.path.endsWith("/" + query, true)) {
                                        // continue descending so a file with the exact name can still be found
                                    }
                                    visit(entry.path)
                                } else if (
                                    entry.name.equals(query, true) ||
                                    entry.name.contains(query, true) ||
                                    entry.path.equals(query, true)
                                ) {
                                    found += entry
                                    return
                                }
                                if (found.isNotEmpty()) return
                            }
                        }
                        is GitHubContentsResult.Failure -> Unit
                    }
                }
                visit("")
                val entry = found.firstOrNull() ?: return@mapNotNull null
                val content = when (val result = githubGateway.readFile(remote.owner, remote.repository, entry.path, remote.branch)) {
                    is GitHubFileResult.Success -> result.content
                    is GitHubFileResult.Failure -> ""
                }
                FileMention(
                    token = "@" + query,
                    query = query,
                    uri = GitHubWorkspaceUris.path(workspaceIdValue, entry.path).toString(),
                    name = entry.name,
                    content = content,
                )
            }
        }
    }

    private fun buildBoundedHistory(
        model: AIModelInfo,
        source: List<ChatMessageEntity>,
        instructionChars: Int = 0,
    ): List<Pair<String, String>> {
        val contextChars = model.contextLimit?.let {
            minOf(it.coerceAtLeast(0L), MAX_REQUEST_CHARS / CHARS_PER_TOKEN) * CHARS_PER_TOKEN
        }?.coerceAtLeast(1L) ?: DEFAULT_REQUEST_CHARS
        val outputReserve = ((model.outputTokenLimit ?: 4_096L).coerceIn(512L, 8_192L) * CHARS_PER_TOKEN)
        val hardLimit = (contextChars - outputReserve - instructionChars.toLong())
            .coerceAtLeast(16L * CHARS_PER_TOKEN)
        val result = ArrayDeque<Pair<String, String>>()
        var used = 0L
        source.asReversed().forEach { message ->
            val chars = message.content.length.toLong()
            if (used + chars > hardLimit) return@forEach
            result.addFirst(message.role to message.content)
            used += chars
        }
        return result.toList()
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        messageJob?.cancel()
        sendJob?.cancel()
        selectionJob?.cancel()
        modelLoadJob?.cancel()
        super.onCleared()
    }

    private data class AttachmentMetadata(val name: String, val mimeType: String, val sizeBytes: Long)

    companion object {
        private const val MAX_ATTACHMENTS = 8
        private const val MAX_TOTAL_ATTACHMENT_BYTES = 80L * 1024L * 1024L
        private const val MAX_ATTACHMENT_NAME_CHARS = 180
        private const val MAX_ATTACHMENT_CONTEXT_BYTES = 256 * 1024L
        private const val MAX_TEXT_ATTACHMENT_BYTES = 128 * 1024L
        private const val MAX_TEXT_ATTACHMENT_CHARS = 128 * 1024
        private const val MAX_VISIBLE_MODELS = 120
        private const val MAX_COMMAND_SUGGESTIONS = 12
        private const val CHARS_PER_TOKEN = 4L
        private const val MAX_REQUEST_CHARS = 1_000_000L
        private const val DEFAULT_REQUEST_CHARS = 256_000L
        private const val MAX_STREAM_VISIBLE_CHARS = 512 * 1024
        private const val MAX_AGENT_CHAT_INSTRUCTION_CHARS = 60_000
        private val AGENT_TERMINAL_STATUSES = setOf(
            AgentTaskStatus.COMPLETED.name,
            AgentTaskStatus.FAILED.name,
            AgentTaskStatus.CANCELLED.name,
        )
        private val MUTATION_INTENT = Regex(
            "(?is)\\b(create|make|add|new|write|modify|edit|change|update|rewrite|replace|delete|remove|rename|move|fix|implement)\\b.{0,180}(?:\\b(file|files|folder|folders|directory|directories|path|script|source|code|class|function)\\b|(?:^|[\\s`(])[^\\s`]+\\.(?:kt|kts|java|py|js|ts|tsx|jsx|json|xml|yml|yaml|md|txt|gradle|properties|toml|sh|html|css|scss|c|cpp|h|hpp|rs|go|swift|sql)\\b)",
        )
    }
}

data class AgentRunPlanItem(
    val id: String,
    val phase: Int,
    val title: String,
    val instruction: String,
    val status: String,
    val taskId: String?,
)

data class AgentRunStepSnapshot(
    val index: Int,
    val label: String,
    val toolId: String,
    val status: String,
    val activity: String? = null,
    val detail: String? = null,
)

data class AgentRunTaskSnapshot(
    val taskId: String,
    val title: String,
    val status: String,
    val provider: String,
    val model: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val currentStep: Int,
    val stepCount: Int,
    val lastToolId: String?,
    val steps: List<AgentRunStepSnapshot>,
    val affectedPaths: List<String>,
)

data class AgentRunState(
    val goal: String,
    val planItems: List<AgentRunPlanItem>,
    val tasks: List<AgentRunTaskSnapshot>,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val planning: Boolean,
    val overview: String? = null,
)


data class ChatAttachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val type: ChatAttachmentType,
)

enum class ChatAttachmentType(val maxBytes: Long) {
    ANY_FILE(15L * 1024L * 1024L),
    PHOTO(15L * 1024L * 1024L),
    VIDEO(50L * 1024L * 1024L),
    AUDIO(30L * 1024L * 1024L),
    DOCUMENT(10L * 1024L * 1024L);

    fun accepts(mime: String, name: String = ""): Boolean {
        val normalizedMime = mime.trim().lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        return when (this) {
            ANY_FILE -> true
            PHOTO -> normalizedMime.startsWith("image/") || extension in PHOTO_EXTENSIONS
            VIDEO -> normalizedMime.startsWith("video/") || extension in VIDEO_EXTENSIONS
            AUDIO -> normalizedMime.startsWith("audio/") || extension in AUDIO_EXTENSIONS
            DOCUMENT ->
                normalizedMime.startsWith("text/") ||
                    normalizedMime == "application/pdf" ||
                    normalizedMime == "application/json" ||
                    normalizedMime == "application/xml" ||
                    normalizedMime == "application/rtf" ||
                    normalizedMime == "text/csv" ||
                    normalizedMime == "application/msword" ||
                    normalizedMime == "application/vnd.ms-excel" ||
                    normalizedMime == "application/vnd.ms-powerpoint" ||
                    normalizedMime.startsWith("application/vnd.openxmlformats-officedocument.") ||
                    normalizedMime == "application/epub+zip" ||
                    extension in DOCUMENT_EXTENSIONS
        }
    }

    val label: String get() = when (this) {
        ANY_FILE -> "File"
        PHOTO -> "Photo"
        VIDEO -> "Video"
        AUDIO -> "Audio"
        DOCUMENT -> "Document"
    }

    private companion object {
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "avif")
        val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp", "avi")
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus")
        val DOCUMENT_EXTENSIONS = setOf(
            "txt", "md", "csv", "tsv", "rtf", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "odt", "ods", "odp", "epub", "html", "xml", "json",
        )
    }
}
