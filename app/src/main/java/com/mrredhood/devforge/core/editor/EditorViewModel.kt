package com.mrredhood.devforge.core.editor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.diagnostics.Diagnostic
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitRemoteTransportService
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.github.GitHubTreeChange
import com.mrredhood.devforge.core.github.GitHubFileResult
import com.mrredhood.devforge.core.github.GitHubCommitResult
import com.mrredhood.devforge.core.github.GitHubPendingChanges
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceUris
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import com.mrredhood.devforge.core.workspace.WorkspaceSymbolExtractor
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EditorRepository(application.contentResolver, application.getSharedPreferences(PREFERENCES, 0))
    private val durable = DurableStateRepository(DevForgeDatabase.get(application))
    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val gitRepositoryService = GitRepositoryService(application.contentResolver)
    private val gitRemoteService = GitRemoteTransportService(application)
    private val githubStore = GitHubWorkspaceStore(application)
    private val githubGateway = GitHubRepositoryGateway(CredentialSecurityStore(application))
    private val gitSyncMutex = kotlinx.coroutines.sync.Mutex()

    var tabs by mutableStateOf<List<EditorTab>>(emptyList())
        private set
    var activeUri by mutableStateOf<Uri?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var diagnostics by mutableStateOf<List<Diagnostic>>(emptyList())
        private set
    var gitSyncMessage by mutableStateOf<String?>(null)
        private set
    var lastOpenEntry by mutableStateOf<WorkspaceEntry?>(null)
        private set
    var refreshConfirmationRequired by mutableStateOf(false)
        private set

    private var recoveryJobs = mutableMapOf<Uri, Job>()
    private var openJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var workspaceJob: Job? = null
    private var openGeneration = 0L
    private var activeWorkspaceId: String? = null
    private val undoStacks = mutableMapOf<Uri, ArrayDeque<String>>()
    private val undoBytes = mutableMapOf<Uri, Long>()
    private val redoStacks = mutableMapOf<Uri, ArrayDeque<String>>()

    val activeTab: EditorTab?
        get() = tabs.firstOrNull { it.uri == activeUri }

    init {
        viewModelScope.launch {
            val initialWorkspaceId = withContext(Dispatchers.IO) {
                workspaceRepository.activeWorkspace.first()?.id
            }
            val restored = withContext(Dispatchers.IO) {
                durable.loadEditorTabs()
            }
            activeWorkspaceId = initialWorkspaceId
            tabs = restored
            activeUri = restored.firstOrNull()?.uri
            workspaceJob = launch {
                workspaceRepository.activeWorkspace.collectLatest { workspace ->
                    val nextWorkspaceId = workspace?.id
                    if (activeWorkspaceId != null && nextWorkspaceId == null) {
                        clearWorkspaceEditorState()
                    }
                    activeWorkspaceId = nextWorkspaceId
                }
            }
        }
    }

    private fun clearWorkspaceEditorState() {
        val oldTabs = tabs
        openGeneration += 1
        openJob?.cancel()
        openJob = null
        recoveryJobs.values.forEach(Job::cancel)
        recoveryJobs.clear()
        diagnosticsJob?.cancel()
        diagnosticsJob = null
        undoStacks.clear()
        undoBytes.clear()
        redoStacks.clear()
        tabs = emptyList()
        activeUri = null
        lastOpenEntry = null
        diagnostics = emptyList()
        error = null
        refreshConfirmationRequired = false
        viewModelScope.launch(Dispatchers.IO) {
            oldTabs.forEach { tab ->
                durable.deleteEditorTab(tab.uri)
                repository.clearRecoveryDraft(tab.uri)
            }
        }
    }

    fun restoreBackupTab(uriString: String, name: String, content: String, savedContent: String) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val boundedContent = content.take(MAX_CONTENT_BYTES)
        val boundedSaved = savedContent.take(MAX_CONTENT_BYTES)
        val tab = EditorTab(
            uri = uri,
            name = name.take(500),
            content = boundedContent,
            savedContent = boundedSaved,
        )
        tabs = tabs.filterNot { it.uri == uri } + tab
        activeUri = uri
        scheduleDiagnostics(uri, immediate = true)
        viewModelScope.launch(Dispatchers.IO) {
            durable.saveEditorTab(tab, active = true)
        }
    }

    fun open(entry: WorkspaceEntry) {
        if (entry.isDirectory) return
        lastOpenEntry = entry
        openGeneration += 1
        val generation = openGeneration
        openJob?.cancel()
        val existing = tabs.firstOrNull { it.uri == entry.uri }
        if (existing != null) {
            isLoading = false
            activeUri = existing.uri
            viewModelScope.launch(Dispatchers.IO) { durable.saveEditorTab(existing, active = true) }
            return
        }
        isLoading = true
        error = null
        openJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { readDocument(entry) }
            if (generation != openGeneration) return@launch
            result.onSuccess { content ->
                val legacyRecovery = withContext(Dispatchers.IO) { repository.recoveryDraft(entry.uri) }
                val persisted = withContext(Dispatchers.IO) {
                    durable.loadEditorTabs().firstOrNull { it.uri == entry.uri }
                }
                val initial = persisted?.content ?: legacyRecovery?.content ?: content
                val tab = EditorTab(entry.uri, entry.name, initial, persisted?.savedContent ?: content)
                if (generation != openGeneration) return@onSuccess
                tabs = tabs.filterNot { it.uri == entry.uri } + tab
                activeUri = entry.uri
                scheduleDiagnostics(entry.uri, immediate = true)
                isLoading = false
                withContext(Dispatchers.IO) {
                    durable.saveEditorTab(tab, active = true)
                    ensureBaselineSnapshot(entry.uri, entry.name, content)
                    if (legacyRecovery != null && legacyRecovery.content != content) {
                        saveSnapshotIfChanged(entry.uri, entry.name, legacyRecovery.content, SnapshotReason.RECOVERY)
                    }
                }
            }.onFailure { throwable ->
                if (generation == openGeneration) {
                    error = throwable.message ?: "Unable to open file"
                    isLoading = false
                }
            }
        }
        openJob?.invokeOnCompletion {
            if (generation == openGeneration) isLoading = false
        }
    }

    private suspend fun readDocument(entry: WorkspaceEntry): Result<String> {
        if (!GitHubWorkspaceUris.isRemote(entry.uri)) return repository.read(entry.uri)
        val workspaceId = entry.uri.pathSegments.firstOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid GitHub workspace file URI."))
        val remote = githubStore.get(workspaceId)
            ?: return Result.failure(IllegalStateException("GitHub workspace metadata is missing."))
        return when (val result = githubGateway.readFile(
            remote.owner,
            remote.repository,
            GitHubWorkspaceUris.remotePath(entry.uri),
            remote.branch,
        )) {
            is GitHubFileResult.Success -> Result.success(result.content)
            is GitHubFileResult.Failure -> Result.failure(IllegalStateException(result.message))
        }
    }

    fun retryOpen() {
        lastOpenEntry?.let(::open)
    }

    fun select(uri: Uri) {
        activeUri = uri
        scheduleDiagnostics(uri, immediate = true)
        tabs.firstOrNull { it.uri == uri }?.let { selected ->
            viewModelScope.launch(Dispatchers.IO) { durable.saveEditorTab(selected, active = true) }
        }
    }

    fun updateContent(content: String) {
        val uri = activeUri ?: return
        val current = tabs.firstOrNull { it.uri == uri }?.content ?: return
        if (current == content) return
        recordUndo(uri, current)
        redoStacks.getOrPut(uri) { ArrayDeque() }.clear()
        applyContent(uri, content)
    }

    fun updateContentFor(uri: Uri, content: String) {
        if (tabs.none { it.uri == uri }) return
        val currentActive = activeUri
        if (currentActive != uri) {
            val current = tabs.firstOrNull { it.uri == uri }?.content ?: return
            if (current == content) return
            recordUndo(uri, current)
            redoStacks.getOrPut(uri) { ArrayDeque() }.clear()
            applyContent(uri, content)
        } else {
            updateContent(content)
        }
    }

    private fun recordUndo(uri: Uri, content: String) {
        val entryBytes = (content.length.toLong() * 2L).coerceAtLeast(1L)
        if (entryBytes > MAX_UNDO_CONTENT_BYTES) return
        val stack = undoStacks.getOrPut(uri) { ArrayDeque() }
        stack.addLast(content)
        undoBytes[uri] = (undoBytes[uri] ?: 0L) + entryBytes
        while (stack.size > MAX_UNDO) {
            val removed = stack.removeFirst()
            undoBytes[uri] = (undoBytes[uri] ?: 0L) - (removed.length.toLong() * 2L).coerceAtLeast(1L)
        }
        while ((undoBytes[uri] ?: 0L) > MAX_UNDO_TOTAL_BYTES && stack.isNotEmpty()) {
            val removed = stack.removeFirst()
            undoBytes[uri] = (undoBytes[uri] ?: 0L) - (removed.length.toLong() * 2L).coerceAtLeast(1L)
        }
    }

    fun undo() {
        val uri = activeUri ?: return
        val current = tabs.firstOrNull { it.uri == uri }?.content ?: return
        val stack = undoStacks[uri] ?: return
        val previous = stack.removeLastOrNull() ?: return
        redoStacks.getOrPut(uri) { ArrayDeque() }.addLast(current)
        applyContent(uri, previous, recordHistory = false)
    }

    fun redo() {
        val uri = activeUri ?: return
        val current = tabs.firstOrNull { it.uri == uri }?.content ?: return
        val next = redoStacks[uri]?.removeLastOrNull() ?: return
        undoStacks.getOrPut(uri) { ArrayDeque() }.addLast(current)
        applyContent(uri, next, recordHistory = false)
    }

    fun replaceAll(query: String, replacement: String): Int {
        val tab = activeTab ?: return 0
        val normalized = query
        if (normalized.isEmpty()) return 0
        var count = 0
        val result = buildString {
            var start = 0
            while (true) {
                val index = tab.content.indexOf(normalized, start, ignoreCase = false)
                if (index < 0) {
                    append(tab.content.substring(start))
                    break
                }
                append(tab.content.substring(start, index)).append(replacement)
                count++
                start = index + normalized.length
            }
        }
        if (count > 0) updateContent(result)
        return count
    }

    fun diagnosticsFor(uri: Uri?): List<Diagnostic> =
        uri?.let { current -> tabs.firstOrNull { it.uri == current }?.let { EditorDiagnostics.analyze(it.name, it.content).diagnostics } } ?: emptyList()

    fun lineStartOffset(line: Int): Int {
        val content = activeTab?.content ?: return 0
        val target = line.coerceAtLeast(1)
        var currentLine = 1
        for (i in content.indices) {
            if (currentLine == target) return i
            if (content[i] == '\n') currentLine++
        }
        return content.length
    }

    fun symbolCandidates(): List<WorkspaceSymbolCandidate> =
        activeTab?.let { tab ->
            WorkspaceSymbolExtractor.extract(tab.name, tab.content, 80).map { WorkspaceSymbolCandidate(it.name, it.kind, it.line) }
        }.orEmpty()

    private fun applyContent(uri: Uri, content: String, recordHistory: Boolean = true) {
        if (content.toByteArray(Charsets.UTF_8).size > MAX_CONTENT_BYTES) {
            error = "Editor content exceeds the supported 8 MB limit."
            return
        }
        tabs = tabs.map { if (it.uri == uri) it.copy(content = content, updatedAt = System.currentTimeMillis()) else it }
        if (uri == activeUri) diagnostics = emptyList()
        scheduleDiagnostics(uri)
        scheduleRecovery(uri)
    }

    private fun scheduleDiagnostics(uri: Uri, immediate: Boolean = false) {
        diagnosticsJob?.cancel()
        diagnosticsJob = viewModelScope.launch {
            if (!immediate) delay(DIAGNOSTICS_DEBOUNCE_MS)
            val tab = tabs.firstOrNull { it.uri == uri } ?: return@launch
            val content = tab.content
            val name = tab.name
            val result = if (content.length.toLong() * 2L > MAX_DIAGNOSTIC_CONTENT_BYTES) {
                emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    EditorDiagnostics.analyze(name, content).diagnostics
                }
            }
            if (activeUri == uri && tabs.firstOrNull { it.uri == uri }?.content == content) {
                diagnostics = result
            }
        }
    }

    fun saveActive() {
        val tab = activeTab ?: return
        val targetUri = tab.uri
        val contentToSave = tab.content
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (GitHubWorkspaceUris.isRemote(targetUri)) Result.success(Unit) else repository.write(targetUri, contentToSave)
            }
            result.onSuccess {
                val syncMessage = withContext(Dispatchers.IO) {
                    syncGitHubAfterSave("update " + (tabs.firstOrNull { it.uri == targetUri }?.name ?: "file"))
                }
                gitSyncMessage = syncMessage
                val current = tabs.firstOrNull { it.uri == targetUri } ?: return@onSuccess
                val changedDuringSave = current.content != contentToSave
                val persisted = current.copy(
                    savedContent = contentToSave,
                    updatedAt = System.currentTimeMillis(),
                )
                tabs = tabs.map { if (it.uri == targetUri) persisted else it }
                withContext(Dispatchers.IO) {
                    durable.saveEditorTab(persisted, active = persisted.uri == activeUri)
                    saveSnapshotIfChanged(targetUri, current.name, contentToSave, SnapshotReason.MANUAL)
                    if (!changedDuringSave) repository.clearRecoveryDraft(targetUri)
                }
                if (!changedDuringSave) recoveryJobs.remove(targetUri)?.cancel()
                else scheduleRecovery(targetUri)
            }.onFailure { throwable -> error = throwable.message ?: "Unable to save file" }
        }
    }

    fun saveAndCloseActive() {
        val tab = activeTab ?: return
        val targetUri = tab.uri
        val contentToSave = tab.content
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (GitHubWorkspaceUris.isRemote(targetUri)) Result.success(Unit) else repository.write(targetUri, contentToSave)
            }
            result.onSuccess {
                val syncMessage = withContext(Dispatchers.IO) {
                    syncGitHubAfterSave("update " + (tabs.firstOrNull { it.uri == targetUri }?.name ?: "file"))
                }
                gitSyncMessage = syncMessage
                val current = tabs.firstOrNull { it.uri == targetUri } ?: return@onSuccess
                val changedDuringSave = current.content != contentToSave
                val persisted = current.copy(
                    savedContent = contentToSave,
                    updatedAt = System.currentTimeMillis(),
                )
                tabs = tabs.map { if (it.uri == targetUri) persisted else it }
                withContext(Dispatchers.IO) {
                    saveSnapshotIfChanged(targetUri, current.name, contentToSave, SnapshotReason.MANUAL)
                    if (!changedDuringSave) {
                        repository.clearRecoveryDraft(targetUri)
                        durable.deleteEditorTab(targetUri)
                    } else {
                        durable.saveEditorTab(persisted, active = persisted.uri == activeUri)
                    }
                }
                if (!changedDuringSave) {
                    recoveryJobs.remove(targetUri)?.cancel()
                    close(targetUri)
                } else {
                    scheduleRecovery(targetUri)
                    error = "The file changed while saving; the newer edits were kept open."
                }
            }.onFailure { throwable -> error = throwable.message ?: "Unable to save file" }
        }
    }

    fun close(uri: Uri, discard: Boolean = false) {
        val tab = tabs.firstOrNull { it.uri == uri } ?: return
        if (tab.isDirty && !discard) return
        recoveryJobs.remove(uri)?.cancel()
        tabs = tabs.filterNot { it.uri == uri }
        undoStacks.remove(uri)
        undoBytes.remove(uri)
        redoStacks.remove(uri)
        activeUri = tabs.lastOrNull()?.uri
        diagnostics = activeUri?.let { diagnosticsFor(it) }.orEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            durable.deleteEditorTab(uri)
            activeUri?.let { next -> tabs.firstOrNull { it.uri == next }?.let { durable.saveEditorTab(it, active = true) } }
            if (discard) repository.clearRecoveryDraft(uri)
        }
    }

    fun restoreSnapshot(snapshot: ContentSnapshot) {
        val uri = snapshot.uri
        if (tabs.none { it.uri == uri }) return
        select(uri)
        updateContent(snapshot.content)
        gitSyncMessage = "Restored local history snapshot."
    }

    fun dismissError() { error = null }

    fun clearGitSyncMessage() {
        gitSyncMessage = null
    }

    private suspend fun syncGitHubAfterSave(summary: String, targetUri: Uri? = activeUri): String? {
        val workspace = workspaceRepository.activeWorkspace.first()
        if (workspace != null) {
            val remote = githubStore.get(workspace.id)
            if (remote != null && targetUri != null && GitHubWorkspaceUris.isRemote(targetUri)) {
                val path = GitHubWorkspaceUris.remotePath(targetUri)
                val content = tabs.firstOrNull { it.uri == targetUri }?.content ?: return null
                GitHubPendingChanges.queue(
                    workspaceId = workspace.id,
                    owner = remote.owner,
                    repository = remote.repository,
                    branch = remote.branch,
                    folderPath = path.substringBeforeLast('/', ""),
                    changes = listOf(GitHubTreeChange(path = path, content = content)),
                )
                return "GitHub change queued for commit to " + remote.branch
            }
        }
        var resultMessage: String? = null
        if (workspace != null) {
            when (val detected = gitRepositoryService.detect(workspace.treeUri)) {
                is GitDetectionState.Detected -> {
                    if (!detected.repository.remoteUrl.isNullOrBlank()) {
                        resultMessage = when (val result = gitSyncMutex.withLock {
                            gitRemoteService.autoSyncChanges(detected.repository, "DevForge: " + summary)
                        }) {
                            is com.mrredhood.devforge.core.git.GitRemoteResult.Success -> result.message
                            is com.mrredhood.devforge.core.git.GitRemoteResult.Failure ->
                                "Local save completed. GitHub synchronization failed: " + result.message
                        }
                    }
                }
                else -> Unit
            }
        }
        return resultMessage
    }

    fun refreshActive() {
        val tab = activeTab ?: return
        if (tab.isDirty) {
            refreshConfirmationRequired = true
        } else {
            reloadActiveTab(tab)
        }
    }

    fun confirmRefresh() {
        refreshConfirmationRequired = false
        activeTab?.let(::reloadActiveTab)
    }

    fun cancelRefreshConfirmation() {
        refreshConfirmationRequired = false
    }

    private fun reloadActiveTab(tab: EditorTab) {
        openGeneration += 1
        val generation = openGeneration
        openJob?.cancel()
        isLoading = true
        error = null
        openJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                readDocument(WorkspaceEntry(tab.uri, tab.name, false, null))
            }
            if (generation != openGeneration) return@launch
            result.onSuccess { freshContent ->
                val refreshed = tab.copy(content = freshContent, savedContent = freshContent)
                tabs = tabs.map { if (it.uri == tab.uri) refreshed else it }
                activeUri = tab.uri
                diagnostics = EditorDiagnostics.analyze(tab.name, freshContent).diagnostics
                undoStacks.remove(tab.uri)
                redoStacks.remove(tab.uri)
                withContext(Dispatchers.IO) {
                    durable.saveEditorTab(refreshed, active = true)
                    ensureBaselineSnapshot(tab.uri, tab.name, freshContent)
                }
                isLoading = false
            }.onFailure { throwable ->
                if (generation == openGeneration) {
                    error = throwable.message ?: "Unable to refresh file."
                    isLoading = false
                }
            }
        }
        openJob?.invokeOnCompletion {
            if (generation == openGeneration) isLoading = false
        }
    }

    private fun scheduleRecovery(uri: Uri) {
        recoveryJobs.remove(uri)?.cancel()
        recoveryJobs[uri] = viewModelScope.launch {
            delay(1_200)
            val tab = tabs.firstOrNull { it.uri == uri } ?: return@launch
            if (tab.isDirty) {
                withContext(Dispatchers.IO) {
                    durable.saveEditorTab(tab, active = tab.uri == activeUri)
                    repository.saveRecoveryDraft(RecoveryDraft(tab.uri, tab.name, tab.content, System.currentTimeMillis()))
                    saveSnapshotIfChanged(tab.uri, tab.name, tab.content, SnapshotReason.RECOVERY)
                }
            }
        }
    }

    private suspend fun ensureBaselineSnapshot(uri: Uri, name: String, content: String) {
        val latest = durable.latestSnapshot(uri)
        if (latest == null || latest.contentHash != ContentHasher.sha256(content)) {
            durable.saveSnapshot(durable.createSnapshot(uri, name, content, SnapshotReason.OPEN))
        }
    }

    private suspend fun saveSnapshotIfChanged(uri: Uri, name: String, content: String, reason: SnapshotReason) {
        val hash = ContentHasher.sha256(content)
        if (durable.latestSnapshot(uri)?.contentHash != hash) {
            durable.saveSnapshot(durable.createSnapshot(uri, name, content, reason))
        }
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        openJob?.cancel()
        diagnosticsJob?.cancel()
        openJob = null
        undoStacks.clear()
        undoBytes.clear()
        redoStacks.clear()
        recoveryJobs.values.forEach(Job::cancel)
        recoveryJobs.clear()
        super.onCleared()
    }

    private companion object {
        const val PREFERENCES = "devforge_editor"
        const val MAX_CONTENT_BYTES = 8 * 1024 * 1024
        const val MAX_UNDO = 100
        const val MAX_UNDO_CONTENT_BYTES = 512 * 1024
        const val MAX_UNDO_TOTAL_BYTES = 4 * 1024 * 1024
        const val DIAGNOSTICS_DEBOUNCE_MS = 450L
        const val MAX_DIAGNOSTIC_CONTENT_BYTES = 256L * 1024L
    }
}

data class WorkspaceSymbolCandidate(val name: String, val kind: String, val line: Int)
