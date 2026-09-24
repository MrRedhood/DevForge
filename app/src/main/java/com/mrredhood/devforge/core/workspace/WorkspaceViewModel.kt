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
import com.mrredhood.devforge.core.git.GitRemoteResult
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.github.GitHubTreeChange
import com.mrredhood.devforge.core.github.GitHubCommitResult
import com.mrredhood.devforge.core.github.GitHubFileResult
import com.mrredhood.devforge.core.github.GitHubPendingChanges
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    private val githubStore = GitHubWorkspaceStore(application)
    private val localGitHubLinkStore = LocalGitHubRepositoryLinkStore(application)
    private val githubGateway = GitHubRepositoryGateway(CredentialSecurityStore(application))
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var workspaceChangeJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var indexJob: kotlinx.coroutines.Job? = null

    var workspace by mutableStateOf<Workspace?>(null)
        private set
    var remoteWorkspace by mutableStateOf<GitHubWorkspaceRemote?>(null)
        private set
    var localGitHubLink by mutableStateOf<LocalGitHubRepositoryLink?>(null)
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
                remoteWorkspace = active?.let { githubStore.get(it.id) }
                localGitHubLink = active?.let { localGitHubLinkStore.get(it.id) }
                currentUri = active?.treeUri
                currentName = active?.name ?: "Workspace"
                breadcrumbs = active?.let {
                    listOf(
                        WorkspaceBreadcrumb(
                            if (githubStore.get(it.id) != null) GitHubWorkspaceUris.root(it.id) else it.treeUri,
                            it.name,
                        ),
                    )
                } ?: emptyList()
                refreshJob?.cancel()
                workspaceChangeJob?.cancel()
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
                    workspaceChangeJob = launch {
                        WorkspaceChangeBus.events
                            .filter { it.workspaceId == active.id }
                            .collectLatest {
                                delay(120)
                                if (workspace?.id == active.id) refresh()
                            }
                    }
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            repository.workspaces.collectLatest { workspaces = it }
        }
    }

    suspend fun createLocalProject(parentUri: Uri, projectName: String): Result<Workspace> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = projectName.trim()
            require(cleanName.isNotBlank()) { "Project name is required." }
            val childUri = fileOperations.createFolder(parentUri, cleanName)
            val created = Workspace(name = cleanName.take(120), treeUri = childUri)
            tree.list(childUri, 1)
            repository.saveAndActivate(created)
            created
        }
    }

    suspend fun saveGitHubRepositoryLocally(
        parentUri: Uri,
        owner: String,
        repositoryName: String,
        branch: String,
    ): Result<Workspace> = withContext(Dispatchers.IO) {
        runCatching {
            val clonedUri = gitRemoteService.cloneRepositoryInto(
                parentUri = parentUri,
                owner = owner,
                repository = repositoryName,
                branch = branch,
                targetName = repositoryName,
            ).getOrThrow()
            val created = Workspace(name = repositoryName.trim().take(120), treeUri = clonedUri)
            tree.list(clonedUri, 1)
            localGitHubLinkStore.save(
                LocalGitHubRepositoryLink(
                    workspaceId = created.id,
                    owner = owner.trim(),
                    repository = repositoryName.trim(),
                    branch = branch.trim().ifBlank { "main" },
                ),
            )
            repository.saveAndActivate(created)
            created
        }
    }

    suspend fun publishLocalWorkspaceToGitHub(
        owner: String,
        repositoryName: String,
        branch: String,
        commitMessage: String = "DevForge: publish local workspace",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val active = workspace ?: throw IllegalStateException("Select a local workspace before publishing.")
            if (remoteWorkspace != null) throw IllegalStateException("This workspace is already GitHub-backed.")
            when (val result = gitRemoteService.publishWorkspaceToGitHub(
                workspaceRoot = active.treeUri,
                owner = owner,
                repository = repositoryName,
                branch = branch,
                commitMessage = commitMessage,
            )) {
                is GitRemoteResult.Failure -> throw IllegalStateException(result.message)
                is GitRemoteResult.Success -> {
                    localGitHubLinkStore.save(
                        LocalGitHubRepositoryLink(
                            workspaceId = active.id,
                            owner = owner.trim(),
                            repository = repositoryName.trim(),
                            branch = branch.trim().ifBlank { "main" },
                        ),
                    )
                    localGitHubLink = localGitHubLinkStore.get(active.id)
                    result.message
                }
            }
        }
    }


    val rootUri: Uri?
        get() = workspace?.let { if (remoteWorkspace != null) GitHubWorkspaceUris.root(it.id) else it.treeUri }

    suspend fun listDirectory(pathUri: Uri): List<WorkspaceEntry> = withContext(Dispatchers.IO) {
        val active = workspace ?: return@withContext emptyList()
        if (remoteWorkspace != null) {
            when (val response = githubGateway.listContents(
                remoteWorkspace!!.owner,
                remoteWorkspace!!.repository,
                GitHubWorkspaceUris.remotePath(pathUri),
                remoteWorkspace!!.branch,
            )) {
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Success ->
                    applyPendingRemoteEntries(
                        currentPath = GitHubWorkspaceUris.remotePath(pathUri),
                        baseEntries = response.entries.map {
                            WorkspaceEntry(
                                GitHubWorkspaceUris.path(active.id, it.path),
                                it.name,
                                it.type == "dir",
                                it.sizeBytes,
                            )
                        },
                    )
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Failure ->
                    throw IllegalStateException(response.message)
            }
        } else {
            tree.list(pathUri)
        }
    }

    fun moveEntry(entry: WorkspaceEntry, destinationDirectory: Uri) {
        if (entry.name == ".git") return
        val sourceParent = currentUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                gitSyncMutex.withLock {
                    if (remoteWorkspace != null) {
                        val oldPath = GitHubWorkspaceUris.remotePath(entry.uri)
                        val destinationPath = GitHubWorkspaceUris.remotePath(destinationDirectory)
                        moveRemoteEntry(oldPath, destinationPath, entry.isDirectory)
                    } else {
                        require(sourceParent.toString() != destinationDirectory.toString()) {
                            "The item is already in this folder."
                        }
                        fileOperations.move(
                            uri = entry.uri,
                            sourceParent = sourceParent,
                            destinationParent = destinationDirectory,
                            name = entry.name,
                        )
                        syncGitHubAfterMutation("move " + entry.name)
                    }
                }
            }
                .onSuccess { message ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = message ?: "Moved " + entry.name + "."
                        refresh()
                    }
                }
                .onFailure { error ->
                    launch(Dispatchers.Main.immediate) {
                        knowledgeMessage = error.message ?: "Unable to move " + entry.name + "."
                        refresh()
                    }
                }
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

    suspend fun openOrActivateGitHubRepository(
        owner: String,
        repositoryName: String,
        branch: String,
    ): Result<Workspace> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanOwner = owner.trim()
            val cleanRepository = repositoryName.trim()
            val cleanBranch = branch.trim().ifBlank { "main" }
            require(cleanOwner.isNotBlank() && cleanRepository.isNotBlank()) { "A GitHub repository is required." }
            val stored = repository.workspaces.first()
            val existing = stored.firstOrNull { item ->
                val remote = githubStore.get(item.id)
                remote?.let {
                    it.owner.equals(cleanOwner, ignoreCase = true) &&
                        it.repository.equals(cleanRepository, ignoreCase = true) &&
                        it.branch == cleanBranch
                } == true
            }
            val target = existing ?: Workspace(
                name = cleanRepository,
                treeUri = GitHubWorkspaceUris.root(java.util.UUID.randomUUID().toString()),
            )
            if (existing == null) {
                githubStore.save(
                    GitHubWorkspaceRemote(
                        workspaceId = target.id,
                        owner = cleanOwner,
                        repository = cleanRepository,
                        branch = cleanBranch,
                    ),
                )
            }
            repository.saveAndActivate(target)
            target
        }
    }

    suspend fun openGitHubRepository(
        owner: String,
        repositoryName: String,
        branch: String,
    ): Result<Workspace> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanOwner = owner.trim()
            val cleanRepository = repositoryName.trim()
            val cleanBranch = branch.trim().ifBlank { "main" }
            require(cleanOwner.isNotBlank() && cleanRepository.isNotBlank()) { "A GitHub repository is required." }

            val opened = Workspace(
                name = cleanRepository.ifBlank { "GitHub repository" },
                treeUri = GitHubWorkspaceUris.root(java.util.UUID.randomUUID().toString()),
            )
            githubStore.save(
                GitHubWorkspaceRemote(
                    workspaceId = opened.id,
                    owner = cleanOwner,
                    repository = cleanRepository,
                    branch = cleanBranch,
                ),
            )
            repository.saveAndActivate(opened)
            opened
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
                    if (remoteWorkspace != null) {
                        queueRemote(
                            "create " + joinRemotePath(GitHubWorkspaceUris.remotePath(parent), cleanName),
                            listOf(
                                GitHubTreeChange(
                                    joinRemotePath(GitHubWorkspaceUris.remotePath(parent), cleanName),
                                    content = "",
                                ),
                            ),
                            GitHubWorkspaceUris.remotePath(parent),
                        )
                    } else {
                        fileOperations.createFile(parent, cleanName)
                        syncGitHubAfterMutation("create " + cleanName)
                    }
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
                    if (remoteWorkspace != null) {
                        val folder = joinRemotePath(GitHubWorkspaceUris.remotePath(parent), cleanName)
                        queueRemote(
                            "create folder " + folder,
                            listOf(GitHubTreeChange(joinRemotePath(folder, ".gitkeep"), content = "")),
                            GitHubWorkspaceUris.remotePath(parent),
                        )
                    } else {
                        val folderUri = fileOperations.createFolder(parent, cleanName)
                        prepareGitHubFolderForSync(folderUri)
                        syncGitHubAfterMutation("create folder " + cleanName)
                    }
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
                    if (remoteWorkspace != null) {
                        val path = GitHubWorkspaceUris.remotePath(entry.uri)
                        val files = if (entry.isDirectory) collectRemoteFiles(path) else listOf(path)
                        queueRemote(
                            "delete " + path,
                            files.map { GitHubTreeChange(it, delete = true) },
                            path.substringBeforeLast('/', ""),
                        )
                    } else {
                        fileOperations.delete(entry.uri, entry.name)
                        syncGitHubAfterMutation("delete " + entry.name)
                    }
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
                    if (remoteWorkspace != null) {
                        renameRemoteEntry(GitHubWorkspaceUris.remotePath(entry.uri), cleanName, entry.isDirectory)
                    } else {
                        fileOperations.rename(entry.uri, entry.name, cleanName)
                        syncGitHubAfterMutation("rename " + entry.name + " to " + cleanName)
                    }
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
                fileOperations.createFile(folderUri, ".gitkeep")
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
        remoteWorkspace = workspace?.let { githubStore.get(it.id) }
        val current = currentUri ?: return
        refreshJob?.cancel()
        isLoading = true
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val result = if (remoteWorkspace != null) {
                runCatching {
                    when (val response = githubGateway.listContents(
                        remoteWorkspace!!.owner,
                        remoteWorkspace!!.repository,
                        GitHubWorkspaceUris.remotePath(current),
                        remoteWorkspace!!.branch,
                    )) {
                        is com.mrredhood.devforge.core.github.GitHubContentsResult.Success -> {
                            val base = response.entries.map {
                                WorkspaceEntry(
                                    GitHubWorkspaceUris.path(workspace?.id.orEmpty(), it.path),
                                    it.name,
                                    it.type == "dir",
                                    it.sizeBytes,
                                )
                            }
                            applyPendingRemoteEntries(
                                currentPath = GitHubWorkspaceUris.remotePath(current),
                                baseEntries = base,
                            )
                        }
                        is com.mrredhood.devforge.core.github.GitHubContentsResult.Failure -> error(response.message)
                    }
                }
            } else {
                runCatching { tree.list(current) }
            }
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
        if (remoteWorkspace != null) {
            searchQuery = query
            isSearching = true
            searchJob?.cancel()
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                val result = runCatching { searchRemote(query) }
                launch(Dispatchers.Main.immediate) {
                    result.onSuccess { searchResults = it }
                        .onFailure {
                            searchResults = emptyList()
                            knowledgeMessage = it.message ?: "GitHub search failed."
                        }
                    isSearching = false
                }
            }
            return
        }
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

    private fun joinRemotePath(parent: String, child: String): String =
        listOf(parent.trim('/'), child.trim('/')).filter(String::isNotBlank).joinToString("/")

    private suspend fun queueRemote(message: String, changes: List<GitHubTreeChange>, folderPath: String): String {
        val active = workspace ?: error("No workspace is active.")
        val remote = remoteWorkspace ?: error("No GitHub-backed workspace is active.")
        GitHubPendingChanges.queue(
            workspaceId = active.id,
            owner = remote.owner,
            repository = remote.repository,
            branch = remote.branch,
            folderPath = folderPath,
            changes = changes,
        )
        return "GitHub changes queued. Review and commit them when ready."
    }

    suspend fun commitPendingRemote(branch: String, commitMessage: String): Result<String> = withContext(Dispatchers.IO) {
        val active = workspace ?: return@withContext Result.failure(IllegalStateException("No active workspace."))
        val remote = remoteWorkspace ?: return@withContext Result.failure(IllegalStateException("The active workspace is not GitHub-backed."))
        val batch = GitHubPendingChanges.batch(active.id) ?: return@withContext Result.failure(IllegalStateException("There are no pending GitHub changes."))
        val targetBranch = branch.trim().ifBlank { remote.branch }
        when (val result = githubGateway.commitChanges(remote.owner, remote.repository, targetBranch, commitMessage, batch.changes)) {
            is GitHubCommitResult.Success -> {
                GitHubPendingChanges.clear(active.id)
                withContext(Dispatchers.Main.immediate) {
                    knowledgeMessage = "Committed to " + targetBranch + ": " + result.commitSha.take(12)
                    refresh()
                }
                Result.success(result.message)
            }
            is GitHubCommitResult.Failure -> Result.failure(IllegalStateException(result.message))
        }
    }

    fun discardPendingRemote() {
        workspace?.id?.let(GitHubPendingChanges::clear)
        knowledgeMessage = "Pending GitHub changes discarded."
    }

    fun pullRemote() {
        val active = workspace ?: return
        val remote = remoteWorkspace ?: run {
            refresh()
            return
        }
        if (GitHubPendingChanges.batch(active.id) != null) {
            knowledgeMessage = "Pull is blocked while GitHub changes are pending. Commit or discard them first."
            return
        }
        refresh()
        knowledgeMessage = "Pulled the latest repository tree from " + remote.owner + "/" + remote.repository + "."
    }

    private suspend fun collectRemoteFiles(prefix: String): List<String> {
        val remote = remoteWorkspace ?: return emptyList()
        val output = mutableListOf<String>()

        suspend fun visit(path: String) {
            if (output.size >= 600) return
            when (val response = githubGateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Success -> {
                    for (item in response.entries.take(200)) {
                        if (item.type == "dir") visit(item.path) else output += item.path
                        if (output.size >= 600) return
                    }
                }
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Failure -> error(response.message)
            }
        }
        visit(prefix)
        return output
    }

    private suspend fun renameRemoteEntry(oldPath: String, newName: String, directory: Boolean): String {
        val remote = remoteWorkspace ?: error("No GitHub-backed workspace is active.")
        val parent = oldPath.substringBeforeLast('/', "")
        val newPath = joinRemotePath(parent, newName)
        val files = if (directory) collectRemoteFiles(oldPath) else listOf(oldPath)
        val changes = mutableListOf<GitHubTreeChange>()
        files.forEach { oldFile ->
            val content = when (val result = githubGateway.readFile(remote.owner, remote.repository, oldFile, remote.branch)) {
                is com.mrredhood.devforge.core.github.GitHubFileResult.Success -> result.content
                is com.mrredhood.devforge.core.github.GitHubFileResult.Failure -> error(result.message)
            }
            val suffix = oldFile.removePrefix(oldPath).trimStart('/')
            val target = if (directory) joinRemotePath(newPath, suffix) else newPath
            changes += GitHubTreeChange(target, content = content)
            changes += GitHubTreeChange(oldFile, delete = true)
        }
        if (changes.isEmpty() && directory) {
            changes += GitHubTreeChange(joinRemotePath(newPath, ".gitkeep"), content = "")
        }
        return queueRemote("rename " + oldPath + " to " + newPath, changes, parent)
    }

    private suspend fun moveRemoteEntry(oldPath: String, destinationDirectory: String, directory: Boolean): String {
        val remote = remoteWorkspace ?: error("No GitHub-backed workspace is active.")
        val normalizedOld = oldPath.trim('/')
        val destination = destinationDirectory.trim('/')
        require(destination != normalizedOld && !destination.startsWith(normalizedOld + "/")) {
            "A folder cannot be moved into itself or one of its children."
        }
        val name = normalizedOld.substringAfterLast('/')
        val newPath = joinRemotePath(destination, name)
        require(newPath != normalizedOld) { "The item is already in this folder." }

        val files = if (directory) collectRemoteFiles(normalizedOld) else listOf(normalizedOld)
        val changes = mutableListOf<GitHubTreeChange>()
        files.forEach { oldFile ->
            val content = when (val result = githubGateway.readFile(remote.owner, remote.repository, oldFile, remote.branch)) {
                is GitHubFileResult.Success -> result.content
                is GitHubFileResult.Failure -> error(result.message)
            }
            val suffix = oldFile.removePrefix(normalizedOld).trimStart('/')
            val target = if (directory) joinRemotePath(newPath, suffix) else newPath
            changes += GitHubTreeChange(target, content = content)
            changes += GitHubTreeChange(oldFile, delete = true)
        }
        if (changes.isEmpty() && directory) {
            changes += GitHubTreeChange(joinRemotePath(newPath, ".gitkeep"), content = "")
        }
        return queueRemote("move " + normalizedOld + " to " + newPath, changes, destination)
    }

    private suspend fun searchRemote(query: String): List<WorkspaceSearchResult> {
        val remote = remoteWorkspace ?: return emptyList()
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val output = mutableListOf<WorkspaceSearchResult>()

        suspend fun visit(path: String) {
            if (output.size >= 80) return
            when (val response = githubGateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Success -> {
                    for (item in response.entries) {
                        if (item.name.lowercase().contains(q) || item.path.lowercase().contains(q)) {
                            output += WorkspaceSearchResult(
                                uri = GitHubWorkspaceUris.path(workspace?.id.orEmpty(), item.path),
                                name = item.name,
                                isDirectory = item.type == "dir",
                                sizeBytes = item.sizeBytes,
                            )
                        }
                        if (item.type == "dir") visit(item.path)
                        if (output.size >= 80) return
                    }
                }
                is com.mrredhood.devforge.core.github.GitHubContentsResult.Failure -> error(response.message)
            }
        }

        visit("")
        return output
    }

    private fun applyPendingRemoteEntries(
        currentPath: String,
        baseEntries: List<WorkspaceEntry>,
    ): List<WorkspaceEntry> {
        val active = workspace ?: return baseEntries
        val batch = GitHubPendingChanges.batch(active.id) ?: return baseEntries
        val normalizedCurrent = currentPath.trim('/')
        val direct = LinkedHashMap<String, WorkspaceEntry>()
        baseEntries.forEach { direct[it.name] = it }

        fun relative(path: String): String? {
            val clean = path.trim('/')
            if (normalizedCurrent.isBlank()) return clean
            val prefix = normalizedCurrent + "/"
            return clean.removePrefix(prefix).takeIf { it != clean || clean == normalizedCurrent }
        }

        batch.changes.forEach { change ->
            val rel = relative(change.path) ?: return@forEach
            if (rel.isBlank()) return@forEach
            val first = rel.substringBefore('/')
            val nested = rel.contains('/')

            if (change.delete) {
                if (!nested) {
                    direct.remove(first)
                } else {
                    val remainingNested = batch.changes.any { other ->
                        val otherRel = relative(other.path).orEmpty()
                        otherRel.startsWith(first + "/") && !other.delete
                    }
                    if (!remainingNested) direct.remove(first)
                }
            } else if (nested) {
                direct[first] = WorkspaceEntry(
                    uri = GitHubWorkspaceUris.path(active.id, listOf(normalizedCurrent, first).filter(String::isNotBlank).joinToString("/")),
                    name = first,
                    isDirectory = true,
                    sizeBytes = null,
                )
            } else {
                direct[first] = WorkspaceEntry(
                    uri = GitHubWorkspaceUris.path(active.id, change.path),
                    name = first,
                    isDirectory = false,
                    sizeBytes = change.content?.toByteArray(Charsets.UTF_8)?.size?.toLong(),
                )
            }
        }
        return direct.values.sortedWith(compareBy<WorkspaceEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
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

    fun showWorkspaceMessage(message: String) {
        knowledgeMessage = message.take(500)
    }

    fun clearKnowledgeMessage() {
        knowledgeMessage = null
    }

    override fun onCleared() {
        refreshJob?.cancel()
        workspaceChangeJob?.cancel()
        searchJob?.cancel()
        indexJob?.cancel()
        super.onCleared()
    }
}

data class WorkspaceBreadcrumb(
    val uri: Uri,
    val name: String,
)
