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
import com.mrredhood.devforge.core.git.GitRemoteTransportService
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.git.GitDetectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkspaceDatabaseRepository(application)
    private val resolver: ContentResolver = application.contentResolver
    private val tree = WorkspaceFileTree(resolver)
    private val fileOperations = WorkspaceFileOperations(resolver)
    private val searchService = WorkspaceSearch(resolver)
    private val symbolIndex = WorkspaceSymbolIndexStore(application)
    private val indexer = WorkspaceIndexer(resolver)
    private val knowledgeRepository = WorkspaceKnowledgeRepository(application)
    private val gitRepositoryService = GitRepositoryService(resolver)
    private val gitRemoteService = GitRemoteTransportService(application)
    private val gitSyncMutex = Mutex()
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var indexJob: kotlinx.coroutines.Job? = null

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
    var isIndexing by mutableStateOf(false)
        private set
    var indexedSymbolCount by mutableStateOf(0)
        private set
    var symbolQuery by mutableStateOf("")
    var symbolResults by mutableStateOf<List<WorkspaceSymbol>>(emptyList())
        private set
    var knowledge by mutableStateOf<List<WorkspaceKnowledgeNote>>(emptyList())
        private set
    var knowledgeTitle by mutableStateOf("")
    var knowledgeContent by mutableStateOf("")
    var knowledgeMessage by mutableStateOf<String?>(null)
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
                refreshJob?.cancel()
                searchJob?.cancel()
                indexJob?.cancel()
                isLoading = false
                isSearching = false
                isIndexing = false
                if (active == null) {
                    entries = emptyList()
                    indexedSymbolCount = 0
                    symbolResults = emptyList()
                    knowledge = emptyList()
                } else {
                    indexedSymbolCount = symbolIndex.list(active.id).size
                    knowledge = knowledgeRepository.list(active.id)
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            repository.workspaces.collectLatest { workspaces = it }
        }
    }

    fun openWorkspace(
        uri: Uri,
        workspaceName: String? = null,
        takePersistablePermission: Boolean = true,
    ) {
        var persistenceWarning: String? = null
        if (takePersistablePermission) {
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure {
                persistenceWarning = "Folder access is available for this session, but this provider did not allow persistent access."
            }
        }

        val fallbackName = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Workspace"
        val cleanName = workspaceName
            ?.replace(Regex("[\\r\\n]"), " ")
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName
        val newWorkspace = Workspace(name = cleanName, treeUri = uri)
        clearSearch()
        knowledgeMessage = persistenceWarning
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { tree.list(uri, 1) }
            result.onSuccess {
                repository.saveAndActivate(newWorkspace)
            }.onFailure { error ->
                launch(Dispatchers.Main.immediate) {
                    knowledgeMessage = error.message ?: "The selected workspace could not be opened."
                }
            }
        }
    }

    suspend fun importGitHubRepository(
        parentUri: Uri,
        owner: String,
        repositoryName: String,
        branch: String,
    ): Result<Workspace> = withContext(Dispatchers.IO) {
        runCatching {
            resolver.takePersistableUriPermission(
                parentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val clonedRoot = gitRemoteService.cloneRepositoryInto(
                parentUri = parentUri,
                owner = owner,
                repository = repositoryName,
                branch = branch,
                targetName = repositoryName,
            ).getOrThrow()
            val workspaceName = repositoryName.trim().ifBlank { "Git repository" }
            val imported = Workspace(name = workspaceName, treeUri = clonedRoot)
            repository.saveAndActivate(imported)
            imported
        }
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

    fun createFile(name: String) {
        val parent = currentUri ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                gitSyncMutex.withLock {
                    fileOperations.createFile(parent, cleanName)
                    syncGitHubAfterMutation("create " + cleanName)
                }
            }
                .onSuccess { message ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = message ?: "Created " + cleanName + "."
                        refresh()
                    }
                }
                .onFailure { error ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = error.message ?: "Unable to create file."
                        refresh()
                    }
                }
        }
    }

    fun createFolder(name: String) {
        val parent = currentUri ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                gitSyncMutex.withLock {
                    val folderUri = fileOperations.createFolder(parent, cleanName)
                    prepareGitHubFolderForSync(folderUri)
                    syncGitHubAfterMutation("create folder " + cleanName)
                }
            }
                .onSuccess { message ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = message ?: "Created " + cleanName + "."
                        refresh()
                    }
                }
                .onFailure { error ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = error.message ?: "Unable to create folder."
                        refresh()
                    }
                }
        }
    }

    fun deleteEntry(entry: WorkspaceEntry) {
        if (entry.name == ".git") return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                gitSyncMutex.withLock {
                    fileOperations.delete(entry.uri, entry.name)
                    syncGitHubAfterMutation("delete " + entry.name)
                }
            }
                .onSuccess { message ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = message ?: "Deleted " + entry.name + "."
                        refresh()
                    }
                }
                .onFailure { error ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = error.message ?: "Unable to delete " + entry.name + "."
                        refresh()
                    }
                }
        }
    }

    fun renameEntry(entry: WorkspaceEntry, newName: String) {
        if (entry.name == ".git") return
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                gitSyncMutex.withLock {
                    fileOperations.rename(entry.uri, entry.name, cleanName)
                    syncGitHubAfterMutation("rename " + entry.name + " to " + cleanName)
                }
            }
                .onSuccess { message ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = message ?: "Renamed to " + cleanName + "."
                        refresh()
                    }
                }
                .onFailure { error ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = error.message ?: "Unable to rename " + entry.name + "."
                        refresh()
                    }
                }
        }
    }

    private suspend fun prepareGitHubFolderForSync(folderUri: Uri) {
        val active = workspace ?: return
        val detected = gitRepositoryService.detect(active.treeUri)
        if (detected is GitDetectionState.Detected && !detected.repository.remoteUrl.isNullOrBlank()) {
            if (tree.list(folderUri, 1).isEmpty()) {
                runCatching { fileOperations.createFile(folderUri, ".gitkeep") }
            }
        }
    }

    private suspend fun syncGitHubAfterMutation(summary: String): String? {
        val active = workspace ?: return null
        return when (val detected = gitRepositoryService.detect(active.treeUri)) {
            is GitDetectionState.Detected -> {
                if (detected.repository.remoteUrl.isNullOrBlank()) {
                    null
                } else {
                    when (val result = gitRemoteService.autoSyncChanges(detected.repository, "DevForge: " + summary)) {
                        is com.mrredhood.devforge.core.git.GitRemoteResult.Success ->
                            result.message
                        is com.mrredhood.devforge.core.git.GitRemoteResult.Failure ->
                            "Local change saved. GitHub synchronization failed: " + result.message
                    }
                }
            }
            else -> null
        }
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
        refreshJob?.cancel()
        isLoading = true
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { tree.list(current) }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            launch(Dispatchers.Main.immediate) {
                if (currentUri == current) {
                    result.onSuccess { entries = it }
                        .onFailure { knowledgeMessage = it.message ?: "Unable to read this folder." }
                    isLoading = false
                }
            }
        }
    }

    fun search(query: String = searchQuery) {
        val root = workspace?.treeUri ?: return
        val workspaceIdAtStart = workspace?.id
        searchQuery = query
        if (query.isBlank()) {
            clearSearch()
            return
        }
        searchJob?.cancel()
        isSearching = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { searchService.search(root, query) }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            launch(Dispatchers.Main.immediate) {
                if (workspace?.id == workspaceIdAtStart && workspace?.treeUri == root && searchQuery == query) {
                    result.onSuccess { searchResults = it }
                        .onFailure {
                            searchResults = emptyList()
                            knowledgeMessage = it.message ?: "Workspace search failed."
                        }
                    isSearching = false
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        searchQuery = ""
        searchResults = emptyList()
        isSearching = false
    }

    fun rebuildSymbolIndex() {
        val active = workspace ?: return
        if (isIndexing) return
        isIndexing = true
        indexJob?.cancel()
        indexJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val symbols = indexer.build(active.treeUri)
                val persisted = symbolIndex.replace(active.id, symbols)
                withContext(Dispatchers.Main.immediate) {
                    if (workspace?.id == active.id) {
                        indexedSymbolCount = if (persisted) symbols.size else symbolIndex.list(active.id).size
                        symbolResults = emptyList()
                        symbolQuery = ""
                        knowledgeMessage = if (persisted) null else "Symbol index exceeded the local storage budget; the scan completed but was not persisted."
                        isIndexing = false
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    if (workspace?.id == active.id) {
                        knowledgeMessage = error.message ?: "Workspace index failed."
                        isIndexing = false
                    }
                }
            }
        }
    }

    fun searchSymbols(query: String) {
        symbolQuery = query
        val active = workspace ?: return
        symbolResults = if (query.isBlank()) emptyList() else symbolIndex.search(active.id, query)
    }

    fun rememberKnowledge() {
        val active = workspace ?: return
        knowledgeRepository.remember(active.id, knowledgeTitle, knowledgeContent, "manual")
            .onSuccess {
                knowledge = knowledgeRepository.list(active.id)
                knowledgeTitle = ""
                knowledgeContent = ""
                knowledgeMessage = "Workspace knowledge saved."
            }
            .onFailure { knowledgeMessage = it.message ?: "Unable to save workspace knowledge." }
    }

    fun removeKnowledge(id: String) {
        val active = workspace ?: return
        knowledgeRepository.remove(active.id, id)
        knowledge = knowledgeRepository.list(active.id)
    }

    fun reportWorkspacePickerError(error: Throwable?) {
        val detail = error?.message?.takeIf { it.isNotBlank() }?.take(180)
        knowledgeMessage = if (detail == null) {
            "Unable to open the folder picker on this device."
        } else {
            "Unable to open the folder picker: $detail"
        }
    }

    fun clearKnowledgeMessage() {
        knowledgeMessage = null
    }

    override fun onCleared() {
        refreshJob?.cancel()
        searchJob?.cancel()
        indexJob?.cancel()
        super.onCleared()
    }
}

data class WorkspaceBreadcrumb(
    val uri: Uri,
    val name: String,
)
