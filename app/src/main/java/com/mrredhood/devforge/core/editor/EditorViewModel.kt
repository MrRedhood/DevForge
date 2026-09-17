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
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EditorRepository(application.contentResolver, application.getSharedPreferences(PREFERENCES, 0))
    private val durable = DurableStateRepository(DevForgeDatabase.get(application))

    var tabs by mutableStateOf<List<EditorTab>>(emptyList())
        private set
    var activeUri by mutableStateOf<Uri?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var recoveryJobs = mutableMapOf<Uri, Job>()

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
        val existing = tabs.firstOrNull { it.uri == entry.uri }
        if (existing != null) {
            activeUri = existing.uri
            viewModelScope.launch(Dispatchers.IO) { durable.saveEditorTab(existing, active = true) }
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.read(entry.uri) }
            result.onSuccess { content ->
                val legacyRecovery = withContext(Dispatchers.IO) { repository.recoveryDraft(entry.uri) }
                val existingDurable = withContext(Dispatchers.IO) { durable.loadEditorTabs().firstOrNull { it.uri == entry.uri } }
                val initial = existingDurable?.content ?: legacyRecovery?.content ?: content
                val tab = EditorTab(entry.uri, entry.name, initial, existingDurable?.savedContent ?: content)
                tabs = tabs.filterNot { it.uri == entry.uri } + tab
                activeUri = entry.uri
                isLoading = false
                withContext(Dispatchers.IO) {
                    durable.saveEditorTab(tab, active = true)
                    ensureBaselineSnapshot(entry.uri, entry.name, content)
                    if (legacyRecovery != null && legacyRecovery.content != content) {
                        saveSnapshotIfChanged(entry.uri, entry.name, legacyRecovery.content, SnapshotReason.RECOVERY)
                    }
                }
            }.onFailure { throwable ->
                error = throwable.message ?: "Unable to open file"
                isLoading = false
            }
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
        tabs = tabs.map { if (it.uri == uri) it.copy(content = content, updatedAt = System.currentTimeMillis()) else it }
        scheduleRecovery(uri)
    }

    fun saveActive() {
        val tab = activeTab ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.write(tab.uri, tab.content) }
            result.onSuccess {
                val saved = tab.content
                val persisted = tab.copy(savedContent = saved, updatedAt = System.currentTimeMillis())
                tabs = tabs.map { if (it.uri == tab.uri) persisted else it }
                withContext(Dispatchers.IO) {
                    durable.saveEditorTab(persisted, active = true)
                    saveSnapshotIfChanged(tab.uri, tab.name, saved, SnapshotReason.MANUAL)
                    repository.clearRecoveryDraft(tab.uri)
                }
                recoveryJobs.remove(tab.uri)?.cancel()
            }.onFailure { throwable -> error = throwable.message ?: "Unable to save file" }
        }
    }

    fun saveAndCloseActive() {
        val tab = activeTab ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.write(tab.uri, tab.content) }
            result.onSuccess {
                val saved = tab.content
                val persisted = tab.copy(savedContent = saved, updatedAt = System.currentTimeMillis())
                tabs = tabs.map { if (it.uri == tab.uri) persisted else it }
                withContext(Dispatchers.IO) {
                    saveSnapshotIfChanged(tab.uri, tab.name, saved, SnapshotReason.MANUAL)
                    repository.clearRecoveryDraft(tab.uri)
                    durable.deleteEditorTab(tab.uri)
                }
                recoveryJobs.remove(tab.uri)?.cancel()
                close(tab.uri, persist = false)
            }.onFailure { throwable -> error = throwable.message ?: "Unable to save file" }
        }
    }

    fun close(uri: Uri, discard: Boolean = false) = close(uri, persist = true)

    private fun close(uri: Uri, persist: Boolean) {
        val tab = tabs.firstOrNull { it.uri == uri } ?: return
        if (tab.isDirty && !persist) {
            // save-and-close already persisted the final state removal path
        }
        if (tab.isDirty && persist) return
        recoveryJobs.remove(uri)?.cancel()
        tabs = tabs.filterNot { it.uri == uri }
        activeUri = tabs.lastOrNull()?.uri
        viewModelScope.launch(Dispatchers.IO) {
            durable.deleteEditorTab(uri)
            activeUri?.let { next -> tabs.firstOrNull { it.uri == next }?.let { durable.saveEditorTab(it, active = true) } }
            if (discard) repository.clearRecoveryDraft(uri)
        }
    }

    fun dismissError() { error = null }

    private fun scheduleRecovery(uri: Uri) {
        recoveryJobs.remove(uri)?.cancel()
        recoveryJobs[uri] = viewModelScope.launch {
            delay(750)
            val tab = tabs.firstOrNull { it.uri == uri } ?: return@launch
            if (tab.isDirty) {
                withContext(Dispatchers.IO) {
                    durable.saveEditorTab(tab, active = tab.uri == activeUri)
                    repository.saveRecoveryDraft(
                        RecoveryDraft(tab.uri, tab.name, tab.content, System.currentTimeMillis()),
                    )
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
        recoveryJobs.values.forEach(Job::cancel)
        recoveryJobs.clear()
        super.onCleared()
    }

    private companion object {
        const val PREFERENCES = "devforge_editor"
    }
}
