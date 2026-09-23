package com.mrredhood.devforge.core.extension.api

/**
 * Stable public DevForge Platform API v1 contract.
 *
 * Marketplace packages depend on these interfaces, not DevForge's internal Android,
 * Compose, Room or ViewModel implementations.
 */
const val DEVFORGE_API_VERSION = "1.0"

interface DevForgeApi {
    val app: DevForgeAppApi
    val ui: DevForgeUiApi
    val workspace: DevForgeWorkspaceApi
    val files: DevForgeFilesApi
    val editor: DevForgeEditorApi
    val languages: DevForgeLanguagesApi
    val terminal: DevForgeTerminalApi
    val git: DevForgeGitApi
    val github: DevForgeGitHubApi
    val build: DevForgeBuildApi
    val artifacts: DevForgeArtifactApi
    val ai: DevForgeAiApi
    val agents: DevForgeAgentApi
    val tools: DevForgeToolApi
    val workflows: DevForgeWorkflowApi
    val automation: DevForgeAutomationApi
    val commands: DevForgeCommandApi
    val settings: DevForgeSettingsApi
    val storage: DevForgeStorageApi
    val network: DevForgeNetworkApi
    val notifications: DevForgeNotificationApi
    val events: DevForgeEventBus
    val capabilities: DevForgeCapabilityApi
}

data class DevForgePlatformInfo(
    val version: String,
    val apiVersion: String,
    val os: String = "android",
    val sdkInt: Int,
    val architecture: String,
)

interface DevForgeAppApi {
    suspend fun getVersion(): String
    suspend fun getApiVersion(): String
    suspend fun getPlatform(): DevForgePlatformInfo
    suspend fun getState(): DevForgeAppState
    suspend fun open(destination: String)
    suspend fun openUri(uri: String)
    suspend fun showMessage(message: String, options: DevForgeMessageOptions = DevForgeMessageOptions())
}
data class DevForgeAppState(val foreground: Boolean, val activeDestination: String?)
data class DevForgeMessageOptions(val kind: String = "info", val durationMs: Long = 3000L)

interface DevForgeUiApi {
    fun registerPanel(options: DevForgePanelOptions): DevForgeDisposable
    fun registerToolbarItem(options: DevForgeToolbarItemOptions): DevForgeDisposable
    fun registerCommandMenuItem(options: DevForgeMenuItemOptions): DevForgeDisposable
    suspend fun showDialog(options: DevForgeDialogOptions): DevForgeDialogResult
    suspend fun showSheet(options: DevForgeSheetOptions): DevForgeSheetResult
    fun showNotification(options: DevForgeNotificationOptions): DevForgeDisposable
}
data class DevForgePanelOptions(val id: String, val title: String, val location: String, val icon: String? = null)
data class DevForgeToolbarItemOptions(val id: String, val title: String, val location: String, val icon: String? = null, val commandId: String)
data class DevForgeMenuItemOptions(val id: String, val title: String, val commandId: String, val group: String? = null)
data class DevForgeDialogOptions(val title: String, val message: String? = null, val buttons: List<String> = listOf("OK"))
data class DevForgeDialogResult(val selectedButton: String?)
data class DevForgeSheetOptions(val title: String)
data class DevForgeSheetResult(val dismissed: Boolean)

interface DevForgeWorkspaceApi {
    suspend fun list(): List<DevForgeWorkspaceRef>
    suspend fun active(): DevForgeWorkspaceRef?
    suspend fun open(id: String)
    suspend fun close(id: String)
    suspend fun create(options: DevForgeCreateWorkspaceOptions): DevForgeWorkspaceRef
}
data class DevForgeWorkspaceRef(val id: String, val name: String, val root: String)
data class DevForgeCreateWorkspaceOptions(val name: String)

interface DevForgeFilesApi {
    suspend fun exists(path: String): Boolean
    suspend fun stat(path: String): DevForgeFileStat
    suspend fun readText(path: String): String
    suspend fun readBytes(path: String): ByteArray
    suspend fun writeText(path: String, content: String)
    suspend fun writeBytes(path: String, content: ByteArray)
    suspend fun createFile(path: String)
    suspend fun createDirectory(path: String)
    suspend fun delete(path: String)
    suspend fun move(from: String, to: String)
    suspend fun copy(from: String, to: String)
    suspend fun rename(path: String, name: String)
    suspend fun list(path: String? = null): List<DevForgeFileEntry>
    suspend fun tree(options: DevForgeTreeOptions = DevForgeTreeOptions()): DevForgeFileTree
    suspend fun find(options: DevForgeFindOptions): List<DevForgeFileRef>
    suspend fun searchText(options: DevForgeTextSearchOptions): List<DevForgeSearchMatch>
}
data class DevForgeFileStat(val path: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long, val modifiedEpochMs: Long?)
data class DevForgeFileEntry(val path: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long? = null)
data class DevForgeFileTree(val root: String, val entries: List<DevForgeFileEntry>)
data class DevForgeTreeOptions(val path: String? = null, val depth: Int = 4, val limit: Int = 200)
data class DevForgeFindOptions(val query: String, val path: String? = null, val limit: Int = 40)
data class DevForgeTextSearchOptions(val query: String, val path: String? = null, val limit: Int = 40)
data class DevForgeFileRef(val uri: String, val path: String, val name: String)
data class DevForgeSearchMatch(val path: String, val line: Int, val column: Int, val snippet: String)

interface DevForgeEditorApi {
    suspend fun active(): DevForgeEditorRef?
    suspend fun list(): List<DevForgeEditorRef>
    suspend fun open(path: String): DevForgeEditorRef
    suspend fun close(id: String)
    suspend fun getDocument(id: String): DevForgeDocumentSnapshot
    suspend fun getText(id: String): String
    suspend fun setText(id: String, text: String)
    suspend fun insert(id: String, position: DevForgePosition, text: String)
    suspend fun replace(id: String, range: DevForgeRange, text: String)
    suspend fun delete(id: String, range: DevForgeRange)
    suspend fun applyEdits(id: String, edits: List<DevForgeTextEdit>)
    suspend fun getCursor(id: String): DevForgePosition
    suspend fun setCursor(id: String, position: DevForgePosition)
    suspend fun getSelections(id: String): List<DevForgeRange>
    suspend fun setSelections(id: String, ranges: List<DevForgeRange>)
    suspend fun save(id: String)
    suspend fun addDecoration(id: String, decoration: DevForgeDecoration): String
    suspend fun removeDecoration(id: String, decorationId: String)
    suspend fun addDiagnostic(id: String, diagnostic: DevForgeDiagnostic): String
    suspend fun removeDiagnostic(id: String, diagnosticId: String)
}
data class DevForgeEditorRef(val id: String, val path: String, val languageId: String)
data class DevForgeDocumentSnapshot(val id: String, val path: String, val languageId: String, val lineCount: Int, val dirty: Boolean)
data class DevForgePosition(val line: Int, val column: Int)
data class DevForgeRange(val start: DevForgePosition, val end: DevForgePosition)
data class DevForgeTextEdit(val range: DevForgeRange, val text: String)
data class DevForgeDecoration(val range: DevForgeRange, val kind: String, val message: String? = null)
data class DevForgeDiagnostic(val range: DevForgeRange, val severity: String, val message: String, val source: String? = null)

interface DevForgeLanguagesApi {
    fun registerLanguage(definition: DevForgeLanguageDefinition): DevForgeDisposable
    fun registerFormatter(provider: DevForgeFormatterProvider): DevForgeDisposable
    fun registerLinter(provider: DevForgeLinterProvider): DevForgeDisposable
    fun registerCompletionProvider(provider: DevForgeCompletionProvider): DevForgeDisposable
    fun registerHoverProvider(provider: DevForgeHoverProvider): DevForgeDisposable
    fun registerDefinitionProvider(provider: DevForgeDefinitionProvider): DevForgeDisposable
    fun registerReferenceProvider(provider: DevForgeReferenceProvider): DevForgeDisposable
    fun registerCodeActionProvider(provider: DevForgeCodeActionProvider): DevForgeDisposable
}
data class DevForgeLanguageDefinition(val id: String, val label: String, val extensions: List<String>, val aliases: List<String> = emptyList())
fun interface DevForgeFormatterProvider { suspend fun format(path: String, text: String): String }
fun interface DevForgeLinterProvider { suspend fun lint(path: String, text: String): List<DevForgeDiagnostic> }
fun interface DevForgeCompletionProvider { suspend fun complete(path: String, position: DevForgePosition): List<DevForgeCompletionItem> }
fun interface DevForgeHoverProvider { suspend fun hover(path: String, position: DevForgePosition): String? }
fun interface DevForgeDefinitionProvider { suspend fun definition(path: String, position: DevForgePosition): List<DevForgeFileRef> }
fun interface DevForgeReferenceProvider { suspend fun references(path: String, position: DevForgePosition): List<DevForgeFileRef> }
fun interface DevForgeCodeActionProvider { suspend fun actions(path: String, range: DevForgeRange): List<DevForgeCodeAction> }
data class DevForgeCompletionItem(val label: String, val detail: String? = null)
data class DevForgeCodeAction(val title: String, val edits: List<DevForgeTextEdit>)

interface DevForgeTerminalApi {
    suspend fun listSessions(): List<DevForgeTerminalSessionInfo>
    suspend fun createSession(options: DevForgeTerminalSessionOptions = DevForgeTerminalSessionOptions()): DevForgeTerminalSession
    suspend fun getSession(id: String): DevForgeTerminalSession
    suspend fun closeSession(id: String)
}
data class DevForgeTerminalSessionOptions(val name: String? = null, val workingDirectory: String? = null)
data class DevForgeTerminalSessionInfo(val id: String, val name: String, val running: Boolean)
interface DevForgeTerminalSession {
    val id: String
    suspend fun write(input: String)
    suspend fun resize(columns: Int, rows: Int)
    suspend fun signal(signal: String)
}

interface DevForgeGitApi {
    suspend fun status(): DevForgeGitStatus
    suspend fun diff(options: DevForgeGitDiffOptions = DevForgeGitDiffOptions()): DevForgeGitDiff
    suspend fun log(options: DevForgeGitLogOptions = DevForgeGitLogOptions()): List<DevForgeGitCommit>
    suspend fun stage(paths: List<String>)
    suspend fun unstage(paths: List<String>)
    suspend fun commit(options: DevForgeGitCommitOptions): DevForgeGitCommit
    suspend fun checkout(name: String)
    suspend fun fetch()
    suspend fun pull()
    suspend fun push()
    suspend fun stash(message: String? = null)
}
data class DevForgeGitStatus(val branch: String?, val clean: Boolean, val changedPaths: List<String>)
data class DevForgeGitDiffOptions(val path: String? = null)
data class DevForgeGitDiff(val patch: String)
data class DevForgeGitLogOptions(val limit: Int = 20)
data class DevForgeGitCommit(val sha: String, val message: String, val createdAtEpochMs: Long?)
data class DevForgeGitCommitOptions(val message: String)

interface DevForgeGitHubApi {
    suspend fun getUser(): DevForgeGitHubUser
    suspend fun listRepositories(): List<DevForgeGitHubRepository>
    suspend fun getRepository(owner: String, repository: String): DevForgeGitHubRepository
    suspend fun createRepository(options: DevForgeCreateRepositoryOptions): DevForgeGitHubRepository
    suspend fun deleteRepository(owner: String, repository: String)
    suspend fun getFile(owner: String, repository: String, path: String, ref: String? = null): DevForgeGitHubFile
    suspend fun writeFile(options: DevForgeGitHubWriteFileOptions)
    suspend fun listBranches(owner: String, repository: String): List<DevForgeGitHubBranch>
    suspend fun createBranch(owner: String, repository: String, name: String, baseRef: String): DevForgeGitHubBranch
    suspend fun listIssues(owner: String, repository: String): List<DevForgeGitHubIssue>
    suspend fun createIssue(options: DevForgeCreateIssueOptions): DevForgeGitHubIssue
    suspend fun listPullRequests(owner: String, repository: String): List<DevForgeGitHubPullRequest>
    suspend fun getPullRequest(owner: String, repository: String, number: Int): DevForgeGitHubPullRequest
    suspend fun createPullRequest(options: DevForgeCreatePullRequestOptions): DevForgeGitHubPullRequest
    suspend fun listWorkflowRuns(owner: String, repository: String): List<DevForgeWorkflowRun>
    suspend fun dispatchWorkflow(options: DevForgeWorkflowDispatchOptions): DevForgeWorkflowRun
}
data class DevForgeGitHubUser(val login: String)
data class DevForgeGitHubRepository(val owner: String, val name: String, val defaultBranch: String?)
data class DevForgeCreateRepositoryOptions(val name: String, val description: String? = null, val private: Boolean = true)
data class DevForgeGitHubFile(val path: String, val content: String, val sha: String?)
data class DevForgeGitHubWriteFileOptions(val owner: String, val repository: String, val path: String, val content: String, val message: String, val sha: String? = null)
data class DevForgeGitHubBranch(val name: String, val sha: String)
data class DevForgeGitHubIssue(val number: Int, val title: String, val state: String)
data class DevForgeCreateIssueOptions(val owner: String, val repository: String, val title: String, val body: String? = null)
data class DevForgeGitHubPullRequest(val number: Int, val title: String, val state: String)
data class DevForgeCreatePullRequestOptions(val owner: String, val repository: String, val title: String? = null, val body: String? = null, val head: String, val base: String)
data class DevForgeWorkflowRun(val id: Long, val name: String, val status: String, val conclusion: String?)
data class DevForgeWorkflowDispatchOptions(val owner: String, val repository: String, val workflow: String, val ref: String, val inputs: Map<String, String> = emptyMap())

interface DevForgeBuildApi {
    suspend fun listConfigurations(): List<DevForgeBuildConfiguration>
    suspend fun getConfiguration(id: String): DevForgeBuildConfiguration
    suspend fun create(options: DevForgeCreateBuildOptions): DevForgeBuildHandle
    suspend fun start(id: String): DevForgeBuildRun
    suspend fun cancel(id: String)
    suspend fun getStatus(id: String): DevForgeBuildStatus
    suspend fun getLogs(id: String): List<DevForgeBuildLog>
    suspend fun getArtifacts(id: String): List<DevForgeArtifactInfo>
}
data class DevForgeBuildConfiguration(val id: String, val name: String)
data class DevForgeCreateBuildOptions(val name: String, val target: String)
data class DevForgeBuildHandle(val id: String)
data class DevForgeBuildRun(val id: String, val status: String)
data class DevForgeBuildStatus(val id: String, val status: String, val conclusion: String?)
data class DevForgeBuildLog(val timestampEpochMs: Long?, val text: String)

interface DevForgeArtifactApi {
    suspend fun createFile(options: DevForgeCreateArtifactFileOptions): DevForgeArtifactInfo
    suspend fun createText(options: DevForgeCreateTextArtifactOptions): DevForgeArtifactInfo
    suspend fun createJson(options: DevForgeCreateJsonArtifactOptions): DevForgeArtifactInfo
    suspend fun createBinary(options: DevForgeCreateBinaryArtifactOptions): DevForgeArtifactInfo
    suspend fun createDirectory(options: DevForgeCreateArtifactDirectoryOptions): DevForgeArtifactInfo
    suspend fun createArchive(options: DevForgeCreateArchiveOptions): DevForgeArtifactInfo
    suspend fun createPatch(options: DevForgeCreatePatchOptions): DevForgeArtifactInfo
    suspend fun attachToChat(artifactId: String)
    suspend fun get(artifactId: String): DevForgeArtifactInfo
    suspend fun delete(artifactId: String)
}
data class DevForgeArtifactInfo(val id: String, val name: String, val mimeType: String, val sizeBytes: Long, val state: String)
data class DevForgeCreateArtifactFileOptions(val name: String, val content: ByteArray, val mimeType: String = "application/octet-stream")
data class DevForgeCreateTextArtifactOptions(val name: String, val content: String, val mimeType: String = "text/plain")
data class DevForgeCreateJsonArtifactOptions(val name: String, val json: String)
data class DevForgeCreateBinaryArtifactOptions(val name: String, val content: ByteArray, val mimeType: String)
data class DevForgeCreateArtifactDirectoryOptions(val name: String, val entries: List<String>)
data class DevForgeCreateArchiveOptions(val name: String, val entries: Map<String, ByteArray>, val mimeType: String = "application/zip")
data class DevForgeCreatePatchOptions(val name: String, val patch: String)

interface DevForgeAiApi {
    suspend fun listModels(): List<DevForgeAiModel>
    suspend fun listProviders(): List<DevForgeAiProvider>
    suspend fun chat(request: DevForgeAiRequest): DevForgeAiResponse
    suspend fun registerTool(definition: DevForgeAiToolDefinition): DevForgeDisposable
}
data class DevForgeAiModel(val providerId: String, val id: String, val displayName: String)
data class DevForgeAiProvider(val id: String, val displayName: String)
data class DevForgeAiRequest(val prompt: String, val modelId: String? = null)
data class DevForgeAiResponse(val text: String)
data class DevForgeAiToolDefinition(val id: String, val description: String, val schemaJson: String)

interface DevForgeAgentApi {
    suspend fun create(definition: DevForgeAgentDefinition): DevForgeAgentHandle
    suspend fun run(agentId: String, input: String): DevForgeAgentRun
    suspend fun get(runId: String): DevForgeAgentRun
    suspend fun list(): List<DevForgeAgentRun>
    suspend fun cancel(runId: String)
    suspend fun createPlan(definition: DevForgeAgentPlanDefinition): DevForgeAgentPlan
    suspend fun executePlan(planId: String): DevForgeAgentRun
}
data class DevForgeAgentDefinition(val id: String, val name: String, val description: String)
data class DevForgeAgentHandle(val id: String)
data class DevForgeAgentPlanDefinition(val title: String, val steps: List<String>)
data class DevForgeAgentPlan(val id: String, val title: String, val steps: List<String>)
data class DevForgeAgentRun(val id: String, val agentId: String, val status: String)

interface DevForgeToolApi {
    fun register(definition: DevForgeToolDefinition): DevForgeDisposable
    suspend fun unregister(id: String)
    suspend fun list(): List<DevForgeToolInfo>
    suspend fun invoke(id: String, argumentsJson: String): String
}
data class DevForgeToolDefinition(val id: String, val description: String, val schemaJson: String)
data class DevForgeToolInfo(val id: String, val description: String)

interface DevForgeWorkflowApi {
    fun register(definition: DevForgeWorkflowDefinition): DevForgeDisposable
    suspend fun run(id: String, inputJson: String? = null): DevForgeWorkflowRun
    suspend fun get(runId: String): DevForgeWorkflowRun
    suspend fun cancel(runId: String)
    suspend fun list(): List<DevForgeWorkflowRun>
}
data class DevForgeWorkflowDefinition(val id: String, val name: String, val steps: List<String>)
data class DevForgeWorkflowRun(val id: String, val workflowId: String, val status: String)

interface DevForgeAutomationApi {
    fun register(definition: DevForgeAutomationDefinition): DevForgeDisposable
    suspend fun enable(id: String)
    suspend fun disable(id: String)
    suspend fun run(id: String): DevForgeAutomationRun
    suspend fun list(): List<DevForgeAutomationDefinition>
}
data class DevForgeAutomationDefinition(val id: String, val name: String, val trigger: String, val action: String)
data class DevForgeAutomationRun(val id: String, val automationId: String, val status: String)

interface DevForgeCommandApi {
    fun register(definition: DevForgeCommandDefinition): DevForgeDisposable
    suspend fun execute(id: String, argumentsJson: String? = null): String
    suspend fun list(): List<DevForgeCommandInfo>
}
data class DevForgeCommandDefinition(val id: String, val title: String, val description: String = "")
data class DevForgeCommandInfo(val id: String, val title: String, val description: String)

interface DevForgeSettingsApi {
    suspend fun <T> get(key: String): T?
    suspend fun <T> set(key: String, value: T)
    suspend fun reset(key: String)
    fun register(schema: DevForgeSettingsSchema): DevForgeDisposable
}
data class DevForgeSettingsSchema(val namespace: String, val fields: Map<String, DevForgeSettingField>)
data class DevForgeSettingField(val type: String, val defaultValue: String? = null)

interface DevForgeStorageApi {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun delete(key: String)
    suspend fun clear()
}

interface DevForgeNetworkApi {
    suspend fun fetch(request: DevForgeNetworkRequest): DevForgeNetworkResponse
}
data class DevForgeNetworkRequest(val url: String, val method: String = "GET", val headers: Map<String, String> = emptyMap(), val body: ByteArray? = null)
data class DevForgeNetworkResponse(val statusCode: Int, val headers: Map<String, String>, val body: ByteArray)

interface DevForgeNotificationApi {
    suspend fun show(options: DevForgeNotificationOptions): String
    suspend fun update(id: String, options: DevForgeNotificationOptions)
    suspend fun dismiss(id: String)
}
data class DevForgeNotificationOptions(val title: String, val message: String, val kind: String = "info")

interface DevForgeEventBus {
    fun <T : Any> on(event: DevForgeEventType<T>, listener: (T) -> Unit): DevForgeDisposable
}
interface DevForgeEventType<T : Any> {
    val name: String
    val version: Int
}
object DevForgeEvents {
    data object AppStarted : DevForgeEventType<DevForgeLifecycleEvent> { override val name = "app.started"; override val version = 1 }
    data object WorkspaceOpened : DevForgeEventType<DevForgeWorkspaceEvent> { override val name = "workspace.opened"; override val version = 1 }
    data object WorkspaceClosed : DevForgeEventType<DevForgeWorkspaceEvent> { override val name = "workspace.closed"; override val version = 1 }
    data object FileCreated : DevForgeEventType<DevForgeFileEvent> { override val name = "file.created"; override val version = 1 }
    data object FileModified : DevForgeEventType<DevForgeFileEvent> { override val name = "file.modified"; override val version = 1 }
    data object FileDeleted : DevForgeEventType<DevForgeFileEvent> { override val name = "file.deleted"; override val version = 1 }
    data object EditorChanged : DevForgeEventType<DevForgeEditorEvent> { override val name = "editor.changed"; override val version = 1 }
    data object EditorSaved : DevForgeEventType<DevForgeEditorEvent> { override val name = "editor.saved"; override val version = 1 }
    data object GitChanged : DevForgeEventType<DevForgeGitEvent> { override val name = "git.changed"; override val version = 1 }
    data object BuildCompleted : DevForgeEventType<DevForgeBuildEvent> { override val name = "build.completed"; override val version = 1 }
    data object BuildFailed : DevForgeEventType<DevForgeBuildEvent> { override val name = "build.failed"; override val version = 1 }
    data object AiToolCall : DevForgeEventType<DevForgeAiEvent> { override val name = "ai.toolCall"; override val version = 1 }
    data object AgentStepCompleted : DevForgeEventType<DevForgeAgentEvent> { override val name = "agent.stepCompleted"; override val version = 1 }
    data object ArtifactCreated : DevForgeEventType<DevForgeArtifactEvent> { override val name = "artifact.created"; override val version = 1 }
}
data class DevForgeLifecycleEvent(val timestampEpochMs: Long)
data class DevForgeWorkspaceEvent(val workspace: DevForgeWorkspaceRef)
data class DevForgeFileEvent(val path: String)
data class DevForgeEditorEvent(val editorId: String, val path: String)
data class DevForgeGitEvent(val branch: String?, val changedPaths: List<String>)
data class DevForgeBuildEvent(val buildId: String, val status: String, val conclusion: String?)
data class DevForgeAiEvent(val toolId: String, val status: String)
data class DevForgeAgentEvent(val runId: String, val stepIndex: Int, val status: String)
data class DevForgeArtifactEvent(val artifact: DevForgeArtifactInfo)

interface DevForgeCapabilityApi {
    suspend fun has(name: String): Boolean
    suspend fun list(): Set<String>
}

interface DevForgeDisposable {
    fun dispose()
}
