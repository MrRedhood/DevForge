package com.mrredhood.devforge.core.editor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EditorRepository(
        application.contentResolver,
        application.getSharedPreferences(PREFERENCES, 0),
    )
    private val snapshots = SnapshotStore(application)

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

    fun open(entry: WorkspaceEntry) {
        if (entry.isDirectory) return
        val existing = tabs.firstOrNull { it.uri == entry.uri }
        if (existing != null) {
            activeUri = existing.uri
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.read(entry.uri) }
            result.onSuccess { content ->
                val recovery = withContext(Dispatchers.IO) { repository.recoveryDraft(entry.uri) }
                val initial = recovery?.content ?: content
                tabs = tabs + EditorTab(entry.uri, entry.name, initial, content)
                activeUri = entry.uri
                isLoading = false
                withContext(Dispatchers.IO) {
                    ensureBaselineSnapshot(entry.uri, entry.name, content)
                    if (recovery != null && recovery.content != content) {
                        saveSnapshotIfChanged(
                            entry.uri,
                            entry.name,
                            recovery.content,
                            SnapshotReason.RECOVERY,
                        )
                    }
                }
            }.onFailure { throwable ->
                error = throwable.message ?: "Unable to open file"
                isLoading = false
            }
        }
    }

    fun select(uri: Uri) { activeUri = uri }

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

    fun saveAndCloseActive() {
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
                close(tab.uri)
            }.onFailure { throwable -> error = throwable.message ?: "Unable to save file" }
        }
    }

    fun close(uri: Uri, discard: Boolean = false) {
        val tab = tabs.firstOrNull { it.uri == uri } ?: return
        if (tab.isDirty && !discard) return
        recoveryJobs.remove(uri)?.cancel()
        tabs = tabs.filterNot { it.uri == uri }
        activeUri = tabs.lastOrNull()?.uri
        if (discard) viewModelScope.launch(Dispatchers.IO) { repository.clearRecoveryDraft(uri) }
    }

    fun dismissError() { error = null }

    private fun scheduleRecovery(uri: Uri) {
        recoveryJobs.remove(uri)?.cancel()
        recoveryJobs[uri] = viewModelScope.launch {
            delay(750)
            val tab = tabs.firstOrNull { it.uri == uri } ?: return@launch
            if (tab.isDirty) {
                withContext(Dispatchers.IO) {
                    repository.saveRecoveryDraft(
                        RecoveryDraft(tab.uri, tab.name, tab.content, System.currentTimeMillis()),
                    )
                    saveSnapshotIfChanged(tab.uri, tab.name, tab.content, SnapshotReason.RECOVERY)
                }
            }
        }
    }

    private fun ensureBaselineSnapshot(uri: Uri, name: String, content: String) {
        val latest = snapshots.latest(uri)
        if (latest == null || latest.contentHash != ContentHasher.sha256(content)) {
            snapshots.save(snapshots.create(uri, name, content, SnapshotReason.OPEN))
        }
    }

    private fun saveSnapshotIfChanged(uri: Uri, name: String, content: String, reason: SnapshotReason) {
        val hash = ContentHasher.sha256(content)
        if (snapshots.latest(uri)?.contentHash != hash) {
            snapshots.save(snapshots.create(uri, name, content, reason))
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
