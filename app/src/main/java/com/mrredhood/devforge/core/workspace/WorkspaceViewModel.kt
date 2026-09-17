package com.mrredhood.devforge.core.workspace

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkspaceDatabaseRepository(application)
    private val resolver: ContentResolver = application.contentResolver
    private val tree = WorkspaceFileTree(resolver)
    private val searchService = WorkspaceSearch(resolver)

    var workspace by mutableStateOf<Workspace?>(null)
        private set
    var workspaces by mutableStateOf<List<Workspace>>(emptyList())
        private set
    var currentUri by mutableStateOf<Uri?>(null)
        private set
    var currentName by mutableStateOf("Workspace")
        private set
    var breadcrumbs by mutableStateOf<List<WorkspaceBreadcrumb>>(emptyList())
        private set
    var entries by mutableStateOf<List<WorkspaceEntry>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<WorkspaceSearchResult>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var isSearching by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureLegacyWorkspaceMigrated()
        }
        viewModelScope.launch {
            repository.activeWorkspace.collectLatest { active ->
                workspace = active
                currentUri = active?.treeUri
                currentName = active?.name ?: "Workspace"
                breadcrumbs = active?.let { listOf(WorkspaceBreadcrumb(it.treeUri, it.name)) } ?: emptyList()
                if (active == null) entries = emptyList() else refresh()
            }
        }
        viewModelScope.launch {
            repository.workspaces.collectLatest { workspaces = it }
        }
    }

    fun openWorkspace(uri: Uri, takePersistablePermission: Boolean = true) {
        if (takePersistablePermission) {
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        val name = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Workspace"
        val newWorkspace = Workspace(name = name, treeUri = uri)
        clearSearch()
        viewModelScope.launch(Dispatchers.IO) { repository.saveAndActivate(newWorkspace) }
    }

    fun switchWorkspace(id: String) {
        if (id == workspace?.id) return
        clearSearch()
        viewModelScope.launch(Dispatchers.IO) { repository.activate(id) }
    }

    fun openDirectory(entry: WorkspaceEntry) {
        if (!entry.isDirectory) return
        currentUri = entry.uri
        currentName = entry.name
        breadcrumbs = breadcrumbs + WorkspaceBreadcrumb(entry.uri, entry.name)
        clearSearch()
        refresh()
    }

    fun goToBreadcrumb(index: Int) {
        val target = breadcrumbs.getOrNull(index) ?: return
        breadcrumbs = breadcrumbs.take(index + 1)
        currentUri = target.uri
        currentName = target.name
        clearSearch()
        refresh()
    }

    fun goUp(): Boolean {
        if (breadcrumbs.size <= 1) return false
        goToBreadcrumb(breadcrumbs.lastIndex - 1)
        return true
    }

    fun goToRoot() {
        if (breadcrumbs.isNotEmpty()) goToBreadcrumb(0)
    }

    fun refresh() {
        val current = currentUri ?: return
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { tree.list(current) }.getOrDefault(emptyList())
            launch(Dispatchers.Main.immediate) {
                entries = result
                isLoading = false
            }
        }
    }

    fun search(query: String = searchQuery) {
        val root = workspace?.treeUri ?: return
        searchQuery = query
        if (query.isBlank()) {
            clearSearch()
            return
        }
        isSearching = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = searchService.search(root, query)
            launch(Dispatchers.Main.immediate) {
                searchResults = result
                isSearching = false
            }
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
        isSearching = false
    }

    override fun onCleared() {
        super.onCleared()
    }
}

data class WorkspaceBreadcrumb(
    val uri: Uri,
    val name: String,
)
