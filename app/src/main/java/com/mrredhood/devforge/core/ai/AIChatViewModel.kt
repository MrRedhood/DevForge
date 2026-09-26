package com.mrredhood.devforge.core.ai

import android.app.Application
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
    private var activePrompt: String? = null
    private var pauseRequestGeneration: Long? = null
    private var workspaceJob: Job? = null
    private var selectionJob: Job? = null
    private var selectionGeneration = 0L
    private var modelLoadJob: Job? = null
    private var modelLoadGeneration = 0L
    private var workspaceRoot: Uri? = null
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
    var isPausing by mutableStateOf(false)
        private set
    var thinkingModeEnabled by mutableStateOf(false)
        private set
    var thinkingActive by mutableStateOf(false)
        private set
    var thinkingSummary by mutableStateOf<String?>(null)
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
    var aiWorkflow by mutableStateOf<AiWorkflowSnapshot?>(aiWorkflowEngine.active())
        private set

    val customThinkingAvailable: Boolean
        get() = selectedModel?.supportsNativeThinking != true

    fun toggleThinkingMode() {
        if (!customThinkingAvailable) {
            thinkingModeEnabled = false
            thinkingActive = false
            return
        }
        thinkingModeEnabled = !thinkingModeEnabled
        if (!thinkingModeEnabled) thinkingActive = false
    }

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
                val sameWorkspace = workspace?.id == workspaceId && workspace?.treeUri == workspaceRoot
                workspaceId = workspace?.id
                workspaceName = workspace?.name
                workspaceRoot = workspace?.treeUri

                if (sameWorkspace) {
                    return@collectLatest
                }

                sendJob?.cancel()
                sendJob = null
                isSending = false
                streamingText = ""
                toolActivities = emptyList()
                editingMessageId = null
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
        if (model.supportsNativeThinking) {
            thinkingModeEnabled = false
            thinkingActive = false
        }
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
                    if (enriched.supportsNativeThinking) {
                        thinkingModeEnabled = false
                        thinkingActive = false
                    }
                    models = models.map { if (it.provider == enriched.provider && it.id == enriched.id) enriched else it }
                }
            }
            val session = chatRepository.getOrCreateSession(scope, enriched)
            if (generation != selectionGeneration) return@launch
            val persistedMessages = database.chatMessageDao()
                .recentDescending(session.sessionId, ChatRepository.MAX_MESSAGES)
                .asReversed()
            withContext(Dispatchers.Main.immediate) {
                if (generation == selectionGeneration) {
                    activeSessionId = session.sessionId
                    messages = persistedMessages
                }
            }
            messageJob?.cancel()
            messageJob = launch {
                chatRepository.observeMessages(session.sessionId).collect { values ->
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
        input = stripChatMessageAttachmentMetadata(message.content)
            .substringBefore("Device attachments:")
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
            stripChatMessageAttachmentMetadata(message.content)
                .substringBefore("Device attachments:")
                .trimEnd()
                .ifBlank { message.content.substringBefore("Device attachment:").trimEnd() }
        } else stripChatThinkingMetadata(message.content)
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
                "Start by creating a clear plan, inspect the workspace before editing, execute the plan sequentially with the main AI tools, " +
                "verify the result with tests/lint/build where applicable, and summarize everything that changed. " +
                "Do not create or require a GitHub repository for this task.\n\nProject goal: " + cleanGoal,
        )
    }

    fun selectCommand(command: AICommandDefinition) {
        updateInput("/${command.name} ")
        suggestions = emptyList()
    }

    fun submit() {
        if (isSending || isPausing) return
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
            isSending = true
            isPausing = false
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
                            isPausing = false
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
        val useCustomThinking = thinkingModeEnabled && !model.supportsNativeThinking
        thinkingSummary = null
        thinkingActive = useCustomThinking
        streamingAnimationKind = StreamingAnimationKind.random()
        toolActivities = emptyList()
        isSending = true
        isPausing = false
        streamingText = ""
        sendError = null
        activePrompt = raw
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
                    if (parsed.command.name == "help") AiCommandCatalog.systemSummary() else parsed.toAgentInstruction()
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
                    if (useCustomThinking) {
                        append("\n\nThinking mode is enabled. Before answering or using any tool, write exactly one concise user-visible action summary inside <devforge_thinking>...</devforge_thinking>. Describe what you are about to inspect, change, or verify. Do not reveal private chain-of-thought, hidden reasoning, credentials, or internal deliberation. Keep the summary brief, practical, and understandable.")
                    }
                }
                val visibleUserMessage = buildString {
                    append(
                        raw.ifBlank {
                            submittedAttachments.joinToString(", ") { it.name }.ifBlank { "Attachment" }
                        },
                    )
                    if (submittedAttachments.isNotEmpty()) {
                        append(encodeChatMessageAttachments(submittedAttachments))
                    }
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
                val historySource = database.chatMessageDao()
                    .recentDescending(sessionId, ChatRepository.MAX_MESSAGES)
                    .asReversed()
                withContext(Dispatchers.Main.immediate) {
                    if (requestGeneration == generationId) messages = historySource
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
                        onPlan = { plan ->
                            withContext(Dispatchers.Main.immediate) {
                                if (requestGeneration != generationId) return@withContext
                                workflowSnapshot = aiWorkflowEngine.setPlan(workflowSnapshot, plan)
                                aiWorkflow = workflowSnapshot
                            }
                        },
                        onPlanProgress = { progress ->
                            withContext(Dispatchers.Main.immediate) {
                                if (requestGeneration != generationId) return@withContext
                                workflowSnapshot = aiWorkflowEngine.updatePlanStep(
                                    workflowSnapshot,
                                    index = progress.index,
                                    status = progress.status,
                                    detail = progress.detail,
                                )
                                aiWorkflow = workflowSnapshot
                            }
                        },
                        onThinking = { summary ->
                            withContext(Dispatchers.Main.immediate) {
                                if (requestGeneration != generationId) return@withContext
                                thinkingSummary = summary.take(2_000)
                                thinkingActive = false
                            }
                        },
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
                        appendChatThinkingSummary(toolResult.response, thinkingSummary),
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
                    val responseThinking = parseChatThinkingSummary(response)
                    if (responseThinking != null) {
                        withContext(Dispatchers.Main.immediate) {
                            thinkingSummary = responseThinking
                            thinkingActive = false
                        }
                    }
                    val cleanResponse = stripChatThinkingMetadata(response)
                    chatRepository.addMessage(
                        sessionId,
                        "assistant",
                        appendChatThinkingSummary(cleanResponse, thinkingSummary),
                    )
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
                        val rawVisible = builder.toString()
                        val responseThinking = parseChatThinkingSummary(rawVisible)
                        if (responseThinking != null) {
                            launch(Dispatchers.Main.immediate) {
                                if (requestGeneration == generationId) {
                                    thinkingSummary = responseThinking
                                    thinkingActive = false
                                }
                            }
                        }
                        partialResponse = stripChatThinkingMetadata(rawVisible).take(MAX_STREAM_VISIBLE_CHARS)
                        val visible = partialResponse
                        launch(Dispatchers.Main.immediate) {
                            if (requestGeneration == generationId && !isPausing) streamingText = visible
                        }
                    }
                    val finalText = stripChatThinkingMetadata(builder.toString())
                        .ifBlank { "The model returned an empty response." }
                    if (useCustomThinking && thinkingSummary == null) {
                        withContext(Dispatchers.Main.immediate) {
                            if (requestGeneration == generationId) {
                                thinkingSummary = "I reviewed your request and am proceeding with the requested answer or changes."
                                thinkingActive = false
                            }
                        }
                    }
                    chatRepository.addMessage(
                        sessionId,
                        "assistant",
                        appendChatThinkingSummary(finalText, thinkingSummary),
                    )
                    workflowSnapshot = aiWorkflowEngine.verifyAndComplete(
                        aiWorkflowEngine.phase(workflowSnapshot, AiWorkflowPhase.VERIFY, "Verifying workspace state"),
                        workspaceRoot = workspaceRoot,
                        changedPaths = emptyList(),
                        summary = finalText,
                    )
                    withContext(Dispatchers.Main.immediate) { aiWorkflow = workflowSnapshot }
                }
            } catch (cancelled: CancellationException) {
                val requestWasPaused = pauseRequestGeneration == requestGeneration
                retainAttachmentsForRetry = true
                if (requestWasPaused) {
                    withContext(NonCancellable) {
                        if (partialResponse.isNotBlank()) {
                            chatRepository.addMessage(
                                sessionId,
                                "assistant",
                                appendChatThinkingSummary(partialResponse + "\n\n[Generation paused]", thinkingSummary),
                            )
                        }
                    }
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
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
                        if (requestGeneration == generationId) {
                            sendError = "AI paused. Press Send to continue from the current workspace state."
                        }
                    }
                } else {
                    workflowSnapshot = aiWorkflowEngine.fail(workflowSnapshot, "AI execution cancelled.")
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        if (workflowSnapshot.status == AiWorkflowSnapshot.Status.RUNNING ||
                            workflowSnapshot.status == AiWorkflowSnapshot.Status.WAITING
                        ) {
                            aiWorkflow = workflowSnapshot
                        }
                    }
                    withContext(NonCancellable) {
                        if (partialResponse.isNotBlank()) {
                            chatRepository.addMessage(
                                sessionId,
                                "assistant",
                                appendChatThinkingSummary(partialResponse + "\n\n[Generation stopped]", thinkingSummary),
                            )
                        }
                    }
                }
                throw cancelled
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
                }
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (requestGeneration == generationId) {
                        streamingText = ""
                        isSending = false
                        isPausing = false
                        if (pauseRequestGeneration == requestGeneration) {
                            input = activePrompt.orEmpty()
                            sendError = "AI paused. Press Send to continue from the current workspace state."
                        }
                        aiWorkflow = null
                        thinkingActive = false
                        if (pauseRequestGeneration == requestGeneration) pauseRequestGeneration = null
                        if (sendJob === currentCoroutineContext()[Job]) sendJob = null
                    }
                }
            }
        }
    }

    private fun stopGenerationLocally() {
        if (!isSending || isPausing) return
        val pausedGeneration = generationId
        pauseRequestGeneration = pausedGeneration
        generationId += 1
        input = activePrompt.orEmpty()
        streamingText = ""
        toolActivities = emptyList()
        aiWorkflow = null
        sendError = "AI paused. Press Send to continue from the current workspace state."
        isSending = false
        isPausing = false
        activePrompt = null
        val job = sendJob
        sendJob = null
        toolOrchestrator.cancelActiveExecution()
        job?.cancel(CancellationException("AI paused by user"))
    }

    fun stopGeneration() {
        stopGenerationLocally()
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
            -> "The file is retained in Chat. The configured AI provider may still reject unsupported binary transport when you send it."
        }

    fun addAttachments(uris: List<Uri>, type: ChatAttachmentType) {
        if (uris.isEmpty()) return
        val requestProvider = provider
        viewModelScope.launch(Dispatchers.IO) {
            val failureMessages = mutableListOf<String>()
            val additions = uris.mapNotNull { sourceUri ->
                runCatching {
                    val metadata = readAttachmentMetadata(sourceUri, type.maxBytes)
                    require(type.accepts(metadata.mimeType, metadata.name)) {
                        "The selected item is not a supported " + type.label.lowercase() + " attachment."
                    }
                    require(metadata.sizeBytes in 1..type.maxBytes) {
                        "The attachment exceeds the " + formatSize(type.maxBytes) + " limit."
                    }
                    if (type != ChatAttachmentType.ANY_FILE) {
                        require(supportsProviderAttachment(requestProvider, metadata.name, metadata.mimeType)) {
                            "Binary attachments are not supported by " + requestProvider.displayName + " for this file type."
                        }
                    }
                    val managedUri = ChatAttachmentStore.importAttachment(
                        context = getApplication<Application>(),
                        sourceUri = sourceUri,
                        displayName = metadata.name,
                        maxBytes = type.maxBytes,
                    )
                    ChatAttachment(managedUri, metadata.name, metadata.mimeType, metadata.sizeBytes, type)
                }.onFailure { error ->
                    error.message?.takeIf { it.isNotBlank() }?.let(failureMessages::add)
                }.getOrNull()
            }
            withContext(Dispatchers.Main.immediate) {
                val baseline = attachments
                var bounded = (baseline + additions).distinctBy { it.uri }
                while (bounded.size > MAX_ATTACHMENTS && bounded.isNotEmpty()) bounded = bounded.dropLast(1)
                while (bounded.sumOf { it.sizeBytes } > MAX_TOTAL_ATTACHMENT_BYTES && bounded.isNotEmpty()) bounded = bounded.dropLast(1)
                attachments = bounded
                val finalUris = bounded.asSequence().map { it.uri }.toSet()
                val accepted = additions.count { it.uri in finalUris }
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

    }
}

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

private const val CHAT_THINKING_OPEN = "<devforge_thinking>"
private const val CHAT_THINKING_CLOSE = "</devforge_thinking>"

fun appendChatThinkingSummary(content: String, summary: String?): String {
    val clean = summary?.trim()?.take(2_000).orEmpty()
    return if (clean.isBlank()) content else content + "\n\n" + CHAT_THINKING_OPEN + clean + CHAT_THINKING_CLOSE
}

fun parseChatThinkingSummary(content: String): String? {
    val start = content.indexOf(CHAT_THINKING_OPEN, ignoreCase = true)
    if (start < 0) return null
    val bodyStart = start + CHAT_THINKING_OPEN.length
    val end = content.indexOf(CHAT_THINKING_CLOSE, bodyStart, ignoreCase = true)
    return if (end > bodyStart) content.substring(bodyStart, end).trim().takeIf { it.isNotBlank() } else null
}

fun stripChatThinkingMetadata(content: String): String =
    content.replace(
        Regex("<devforge_thinking>.*?</devforge_thinking>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        "",
    ).trim()
