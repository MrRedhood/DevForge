package com.mrredhood.devforge.core.workspace

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkspaceRepository(application)
    private val resolver: ContentResolver = application.contentResolver
    private val tree = WorkspaceFileTree(resolver)
    private val searchService = WorkspaceSearch(resolver)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var workspace by mutableStateOf(repository.current())
        private set
    var currentUri by mutableStateOf(workspace?.treeUri)
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
        workspace?.let {
            currentUri = it.treeUri
            currentName = it.name
            breadcrumbs = listOf(WorkspaceBreadcrumb(it.treeUri, it.name))
            refresh()
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
        workspace = newWorkspace
        currentUri = uri
        currentName = name
        breadcrumbs = listOf(WorkspaceBreadcrumb(uri, name))
        repository.save(newWorkspace)
        clearSearch()
        refresh()
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
        scope.launch {
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
        scope.launch {
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
        scope.cancel()
        super.onCleared()
    }
}

data class WorkspaceBreadcrumb(
    val uri: Uri,
    val name: String,
)
