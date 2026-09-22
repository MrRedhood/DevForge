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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
    private var messageJob: Job? = null
    private var sendJob: Job? = null
    private var workspaceJob: Job? = null
    private var selectionJob: Job? = null
    private var selectionGeneration = 0L
    private var modelLoadJob: Job? = null
    private var modelLoadGeneration = 0L
    private var workspaceRoot: Uri? = null
    private var activeAgentTaskId: String? = null
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
                activeAgentTaskId?.let { taskId ->
                    launch(Dispatchers.IO) {
                        runCatching {
                            (getApplication<Application>() as? DevForgeApplication)?.agentRuntime?.cancel(taskId)
                        }
                    }
                }
                activeAgentTaskId = null
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

    fun selectCommand(command: AICommandDefinition) {
        updateInput("/${command.name} ")
        suggestions = emptyList()
    }

    fun submit() {
        val model = selectedModel ?: run {
            sendError = "Select a model first."
            return
        }
        if (isSending) return
        val requestProvider = provider
        if (model.provider != requestProvider) {
            sendError = "The selected model belongs to a different provider. Select it again."
            selectedModel = null
            return
        }
        val raw = input.trim()
        val pendingAttachments = attachments
        if (raw.isBlank() && pendingAttachments.isEmpty()) return
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
        isSending = true
        streamingText = ""
        sendError = null
        val requestGeneration = ++generationId
        sendJob = viewModelScope.launch(Dispatchers.IO) {
            var partialResponse = ""
            var retainAttachmentsForRetry = false
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
                val attachmentContext = prepareAttachmentContext(submittedAttachments)
                val compactWorkspaceContext = workspaceId?.let { workspaceContextService.compactPrompt(it) }.orEmpty()
                // Repeat only the compact workspace identity. Rich workspace state and source code are fetched on demand.
                val effectiveBase = if (compactWorkspaceContext.isBlank()) {
                    finalInstruction
                } else {
                    compactWorkspaceContext + "\n\n" + finalInstruction
                }
                val effectiveInstruction = if (attachmentContext.isBlank()) effectiveBase else effectiveBase + "\n\n" + attachmentContext
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
                if (shouldDelegateToWorkspaceAgent(raw, parsed, workspaceId)) {
                    val targetWorkspaceId = workspaceId ?: error("Create or select a workspace before asking the agent to change files.")
                    val agentInstruction = buildAgentInstruction(raw, parsed, effectiveInstruction)
                    val response = executeChatAgent(
                        workspaceId = targetWorkspaceId,
                        sessionId = sessionId,
                        model = model,
                        instruction = agentInstruction,
                    )
                    chatRepository.addMessage(sessionId, "assistant", response)
                } else if (parsed?.command?.name == "help") {
                    chatRepository.addMessage(sessionId, "assistant", effectiveInstruction)
                } else if (toolSettings.enabledToolIds().isNotEmpty()) {
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
                    chatRepository.addMessage(
                        sessionId,
                        "assistant",
                        partialResponse.ifBlank { "The model returned an empty response." },
                    )
                }
            } catch (cancelled: CancellationException) {
                retainAttachmentsForRetry = true
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
        val taskId = activeAgentTaskId
        generationId += 1
        toolActivities = emptyList()
        streamingText = ""
        isSending = false
        sendError = null
        activeAgentTaskId = null
        val runningJob = sendJob
        sendJob = null
        if (taskId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    (getApplication<Application>() as? DevForgeApplication)?.agentRuntime?.cancel(taskId)
                }
            }
        }
        runningJob?.cancel()
    }

    /** Stops only this Chat generation and any agent task created by this Chat turn. */
    fun stopGeneration() {
        stopGenerationLocally()
    }

    private fun shouldDelegateToWorkspaceAgent(
        raw: String,
        parsed: AICommandInvocation?,
        currentWorkspaceId: String?,
    ): Boolean {
        if (currentWorkspaceId.isNullOrBlank()) return false
        return parsed?.command?.name == "agent"
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
        sessionId: String,
        model: AIModelInfo,
        instruction: String,
    ): String {
        val runtime = (getApplication<Application>() as? DevForgeApplication)?.agentRuntime
            ?: error("The agent runtime is unavailable in this DevForge build.")
        val taskId = runtime.assign(
            AgentAssignment(
                workspaceId = workspaceId,
                title = "Chat coding task",
                instruction = instruction,
                model = AgentModelBinding(model.provider, model.id, model.displayName),
                pathScope = com.mrredhood.devforge.core.security.WorkspacePathScope(),
                access = AgentAccess.CODING_DEFAULT,
            ),
        )
        activeAgentTaskId = taskId
        try {
            var task = database.agentTaskDao().get(taskId)
            while (task != null && task.status !in AGENT_TERMINAL_STATUSES) {
                currentCoroutineContext().ensureActive()
                delay(500)
                task = database.agentTaskDao().get(taskId)
            }
            val completed = task ?: error("The agent task disappeared before completion.")
            return when (completed.status) {
                AgentTaskStatus.COMPLETED.name -> {
                    val details = completed.result
                        ?.lineSequence()
                        ?.map(String::trim)
                        ?.filter { it.isNotBlank() && !it.startsWith("receipt=", ignoreCase = true) && !it.startsWith("{") }
                        ?.distinct()
                        ?.take(12)
                        ?.joinToString("\n") { "• " + it }
                        .orEmpty()
                    buildString {
                        append("Agent completed the requested workspace changes.")
                        if (details.isNotBlank()) append("\n\n").append(details)
                    }
                }
                AgentTaskStatus.CANCELLED.name ->
                    "Agent task cancelled."
                else ->
                    "Agent task failed: " + (completed.errorMessage ?: "Unknown agent failure.")
            }
        } finally {
            activeAgentTaskId = null
        }
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
