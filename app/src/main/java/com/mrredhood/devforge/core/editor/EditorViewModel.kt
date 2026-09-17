package com.mrredhood.devforge.core.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(
    private val repository: EditorRepository = EditorRepository,
    private val snapshots: SnapshotStore = SnapshotStore,
) : ViewModel() {
    var tabs: List<EditorTab> = emptyList()
        private set
    var activeUri: Uri? = null
        private set
    var isLoading: Boolean = false
        private set
    var error: String? = null
        private set

    private val recoveryJobs = mutableMapOf<Uri, Job>()

    val activeTab: EditorTab?
        get() = tabs.firstOrNull { it.uri == activeUri }

    fun open(entry: WorkspaceEntry) {
        if (entry.isDirectory) return
        val existing = tabs.firstOrNull { it.uri == entry.uri }
        if (existing != null) {
            activeUri = entry.uri
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = withContext(Dispatchers.IO) { repository.read(entry.uri) }
            result.onSuccess { content ->
                val draft = withContext(Dispatchers.IO) { repository.readRecoveryDraft(entry.uri) }
                val initial = draft ?: content
                tabs = tabs + EditorTab(entry.uri, entry.name, initial, content, System.currentTimeMillis())
                activeUri = entry.uri
                withContext(Dispatchers.IO) {
                    saveSnapshotIfChanged(entry.uri, entry.name, content, SnapshotReason.OPEN)
                }
            }.onFailure { throwable -> error = throwable.message ?: "Unable to open file" }
            isLoading = false
        }
    }

    fun select(uri: Uri) {
        if (tabs.any { it.uri == uri }) activeUri = uri
    }

    fun updateContent(content: String) {
        val uri = activeUri ?: return
        tabs = tabs.map { if (it.uri == uri) it.copy(content = content, updatedAt = System.currentTimeMillis()) else it }
        scheduleRecovery(uri)
    }

    fun saveActive() {
        val tab = activeTab ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.write(tab.uri, tab.content) }
            result.onSuccess {
                val saved = tab.content
                tabs = tabs.map { if (it.uri == tab.uri) it.copy(savedContent = saved, updatedAt = System.currentTimeMillis()) else it }
                withContext(Dispatchers.IO) {
                    saveSnapshotIfChanged(tab.uri, tab.name, saved, SnapshotReason.MANUAL)
                    repository.clearRecoveryDraft(tab.uri)
                }
                recoveryJobs.remove(tab.uri)?.cancel()
            }.onFailure { throwable -> error = throwable.message ?: "Unable to save file" }
        }
    }

    fun close(uri: Uri, discard: Boolean = false) {
        val tab = tabs.firstOrNull { it.uri == uri } ?: return
        if (tab.isDirty && !discard) return
        recoveryJobs.remove(uri)?.cancel()
        tabs = tabs.filterNot { it.uri == uri }
        activeUri = tabs.lastOrNull()?.uri
    }

    fun dismissError() { error = null }

    private fun scheduleRecovery(uri: Uri) {
        recoveryJobs[uri]?.cancel()
        recoveryJobs[uri] = viewModelScope.launch(Dispatchers.IO) {
            delay(1200)
            val tab = tabs.firstOrNull { it.uri == uri } ?: return@launch
            repository.writeRecoveryDraft(uri, tab.content)
        }
    }

    private fun saveSnapshotIfChanged(uri: Uri, name: String, content: String, reason: SnapshotReason) {
        val hash = ContentHasher.sha256(content)
        if (snapshots.latest(uri)?.contentHash != hash) {
            snapshots.save(snapshots.create(uri, name, content, reason))
        }
    }
}
