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
import com.mrredhood.devforge.core.workspace.WorkspaceSymbolExtractor
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EditorRepository(application.contentResolver, application.getSharedPreferences(PREFERENCES, 0))
    private val durable = DurableStateRepository(DevForgeDatabase.get(application))
    private val aiAssistant = EditorAiAssistant(application)

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
    var aiProposal by mutableStateOf<EditorAiProposal?>(null)
        private set
    var isAiBusy by mutableStateOf(false)
        private set

    private var recoveryJobs = mutableMapOf<Uri, Job>()
    private var openJob: Job? = null
    private var openGeneration = 0L
    private val undoStacks = mutableMapOf<Uri, ArrayDeque<String>>()
    private val redoStacks = mutableMapOf<Uri, ArrayDeque<String>>()

    val activeTab: EditorTab?
        get() = tabs.firstOrNull { it.uri == activeUri }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = durable.loadEditorTabs()
            withContext(Dispatchers.Main.immediate) {
                tabs = restored
                activeUri = restored.firstOrNull()?.uri
            }
        }
    }

    fun open(entry: WorkspaceEntry) {
        if (entry.isDirectory) return
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
            val result = withContext(Dispatchers.IO) { repository.read(entry.uri) }
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
                diagnostics = EditorDiagnostics.analyze(entry.name, initial).diagnostics
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

    fun select(uri: Uri) {
        activeUri = uri
        tabs.firstOrNull { it.uri == uri }?.let { selected ->
            viewModelScope.launch(Dispatchers.IO) { durable.saveEditorTab(selected, active = true) }
        }
    }

    fun updateContent(content: String) {
        val uri = activeUri ?: return
        val current = tabs.firstOrNull { it.uri == uri }?.content ?: return
        if (current == content) return
        if (current.toByteArray(Charsets.UTF_8).size <= MAX_UNDO_CONTENT_BYTES) {
            val undo = undoStacks.getOrPut(uri) { ArrayDeque() }
            undo.addLast(current)
            while (undo.size > MAX_UNDO) undo.removeFirst()
            while (undo.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } > MAX_UNDO_TOTAL_BYTES && undo.isNotEmpty()) {
                undo.removeFirst()
            }
        }
        redoStacks.getOrPut(uri) { ArrayDeque() }.clear()
        applyContent(uri, content)
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
        diagnostics = diagnosticsFor(uri)
        scheduleRecovery(uri)
    }

    fun saveActive() {
        val tab = activeTab ?: return
        val targetUri = tab.uri
        val contentToSave = tab.content
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.write(targetUri, contentToSave) }
            result.onSuccess {
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
            val result = withContext(Dispatchers.IO) { repository.write(targetUri, contentToSave) }
            result.onSuccess {
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
        redoStacks.remove(uri)
        activeUri = tabs.lastOrNull()?.uri
        diagnostics = activeUri?.let { diagnosticsFor(it) }.orEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            durable.deleteEditorTab(uri)
            activeUri?.let { next -> tabs.firstOrNull { it.uri == next }?.let { durable.saveEditorTab(it, active = true) } }
            if (discard) repository.clearRecoveryDraft(uri)
        }
    }

    fun dismissError() { error = null }

    fun requestAiEdit(instruction: String) {
        val tab = activeTab ?: return
        val request = instruction.trim().take(2_000)
        if (request.isBlank() || isAiBusy) return
        val targetUri = tab.uri
        val contentAtRequest = tab.content
        isAiBusy = true
        error = null
        viewModelScope.launch {
            val result = aiAssistant.propose(tab.name, contentAtRequest, request)
            if (activeUri != targetUri) {
                isAiBusy = false
                return@launch
            }
            result.onSuccess { proposed ->
                aiProposal = EditorAiProposal(
                    uri = targetUri,
                    fileName = tab.name,
                    original = contentAtRequest,
                    proposed = proposed,
                    instruction = request,
                )
            }.onFailure { throwable ->
                error = throwable.message ?: "AI edit failed."
            }
            isAiBusy = false
        }
    }

    fun acceptAiProposal() {
        val proposal = aiProposal ?: return
        if (activeUri != proposal.uri) return
        updateContent(proposal.proposed)
        aiProposal = null
    }

    fun rejectAiProposal() {
        aiProposal = null
    }

    private fun scheduleRecovery(uri: Uri) {
        recoveryJobs.remove(uri)?.cancel()
        recoveryJobs[uri] = viewModelScope.launch {
            delay(750)
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
        openJob?.cancel()
        openJob = null
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
    }
}

data class WorkspaceSymbolCandidate(val name: String, val kind: String, val line: Int)
