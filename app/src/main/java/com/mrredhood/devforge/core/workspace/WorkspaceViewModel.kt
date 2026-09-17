package com.mrredhood.devforge.core.workspace

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkspaceRepository(application)
    private val tree = WorkspaceFileTree(application.contentResolver)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var workspace by mutableStateOf(repository.current())
        private set

    var entries by mutableStateOf<List<WorkspaceEntry>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun openWorkspace(uri: Uri, takePersistablePermission: Boolean = true) {
        if (takePersistablePermission) {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        val name = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Workspace"
        val newWorkspace = Workspace(name = name, treeUri = uri)
        workspace = newWorkspace
        repository.save(newWorkspace)
        refresh()
    }

    fun refresh() {
        val current = workspace ?: return
        isLoading = true
        scope.launch {
            val result = runCatching { tree.listRoot(current.treeUri) }.getOrDefault(emptyList())
            launch(Dispatchers.Main.immediate) {
                entries = result
                isLoading = false
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
