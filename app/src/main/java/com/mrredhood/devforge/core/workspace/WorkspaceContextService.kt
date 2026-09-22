package com.mrredhood.devforge.core.workspace

import android.content.Context
import com.mrredhood.devforge.core.github.GitHubContentsResult
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.git.GitWorkspaceStatusService
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class WorkspaceContextScope {
    SUMMARY,
    FILES,
    GIT,
    BUILD,
    EDITOR,
    ALL,
}

data class WorkspaceIdentity(
    val id: String,
    val name: String,
    val githubOwner: String? = null,
    val githubRepository: String? = null,
    val branch: String? = null,
    val version: Long = 1L,
)

data class WorkspaceContextSnapshot(
    val identity: WorkspaceIdentity,
    val text: String,
)

object WorkspaceContextVersion {
    private val versions = ConcurrentHashMap<String, AtomicLong>()

    fun current(workspaceId: String): Long =
        versions.getOrPut(workspaceId) { AtomicLong(1L) }.get()

    fun invalidate(workspaceId: String?) {
        if (workspaceId.isNullOrBlank()) return
        versions.getOrPut(workspaceId) { AtomicLong(1L) }.incrementAndGet()
    }
}

/**
 * Compact, authoritative workspace context provider.
 *
 * The AI receives only identity by default. Rich workspace state is fetched by tool call
 * with an explicit scope, keeping normal chat requests small and preventing whole-repo
 * prompt injection/token waste.
 */
class WorkspaceContextService(context: Context) {
    private val appContext = context.applicationContext
    private val database = DevForgeDatabase.get(appContext)
    private val githubStore = GitHubWorkspaceStore(appContext)
    private val githubGateway = GitHubRepositoryGateway(CredentialSecurityStore(appContext))
    private val resolver = appContext.contentResolver
    private val tree = WorkspaceFileTree(resolver)
    private val gitRepositoryService = GitRepositoryService(resolver)
    private val gitStatusService = GitWorkspaceStatusService(resolver)
    private val ledger = WorkspaceContextLedgerRepository(database)

    suspend fun identity(workspaceId: String): WorkspaceIdentity? = withContext(Dispatchers.IO) {
        val workspace = database.workspaceDao().findById(workspaceId) ?: return@withContext null
        val remote = githubStore.get(workspaceId)
        WorkspaceIdentity(
            id = workspace.id,
            name = workspace.name.take(MAX_NAME),
            githubOwner = remote?.owner,
            githubRepository = remote?.repository,
            branch = remote?.branch,
            version = WorkspaceContextVersion.current(workspaceId),
        )
    }

    suspend fun compactPrompt(workspaceId: String): String {
        val value = identity(workspaceId) ?: return "DEVFORGE_WORKSPACE_CONTEXT_V1: workspace unavailable."
        return buildString {
            append("DEVFORGE_WORKSPACE_CONTEXT_V1")
            append("\nworkspace_id=").append(value.id)
            append("\nworkspace_name=").append(value.name)
            value.githubOwner?.let { append("\ngithub_repository=").append(it).append('/').append(value.githubRepository) }
            value.branch?.let { append("\nbranch=").append(it) }
            append("\ncontext_version=").append(value.version)
            ledger.get(workspaceId)?.let { state ->
                val paths = runCatching {
                    org.json.JSONArray(state.recentPathsJson).let { array ->
                        (0 until minOf(array.length(), 6)).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
                    }
                }.getOrDefault(emptyList())
                if (paths.isNotEmpty()) append("\nrecent_paths=").append(paths.joinToString(","))
            }
            append("\nWorkspace identity is authoritative. Do not invent repository/file state.")
            append("\nFor codebase questions, inspect with workspace tools; fetch only the relevant files/lines needed to answer.")
            append("\nNever request or inject the whole workspace just to answer a targeted question.")
        }.take(MAX_COMPACT_CHARS)
    }

    suspend fun snapshot(
        workspaceId: String,
        scope: WorkspaceContextScope = WorkspaceContextScope.SUMMARY,
        limit: Int = DEFAULT_LIMIT,
    ): WorkspaceContextSnapshot? = withContext(Dispatchers.IO) {
        val identity = identity(workspaceId) ?: return@withContext null
        val workspace = database.workspaceDao().findById(workspaceId)?.toDomain()
            ?: return@withContext null
        val boundedLimit = limit.coerceIn(1, MAX_LIMIT)
        val parts = mutableListOf<String>()

        parts += "Workspace: ${identity.name} (id=${identity.id})"
        identity.githubOwner?.let { parts += "GitHub: $it/${identity.githubRepository} @ ${identity.branch}" }
        parts += "Context version: ${identity.version}"

        if (scope == WorkspaceContextScope.SUMMARY || scope == WorkspaceContextScope.FILES || scope == WorkspaceContextScope.ALL) {
            parts += "Files/folders (bounded root listing): " + rootEntries(workspace, boundedLimit)
        }
        if (scope == WorkspaceContextScope.GIT || scope == WorkspaceContextScope.ALL) {
            parts += gitSummary(workspace, identity)
        }
        if (scope == WorkspaceContextScope.BUILD || scope == WorkspaceContextScope.ALL) {
            parts += buildSummary(identity)
        }
        if (scope == WorkspaceContextScope.EDITOR || scope == WorkspaceContextScope.ALL) {
            parts += editorSummary(workspace)
        }

        val text = parts.joinToString("\n").take(MAX_SNAPSHOT_CHARS)
        ledger.recordAccess(workspaceId, identity.version, emptyList(), snapshot = text)
        WorkspaceContextSnapshot(identity, text)
    }

    fun invalidate(workspaceId: String?) {
        WorkspaceContextVersion.invalidate(workspaceId)
    }

    private suspend fun rootEntries(workspace: com.mrredhood.devforge.core.workspace.Workspace, limit: Int): String {
        val remote = githubStore.get(workspace.id)
        return if (remote != null) {
            when (val result = githubGateway.listContents(remote.owner, remote.repository, "", remote.branch)) {
                is GitHubContentsResult.Success -> result.entries
                    .take(limit)
                    .joinToString(", ") { if (it.type == "dir") it.name + "/" else it.name }
                    .ifBlank { "(empty)" }
                is GitHubContentsResult.Failure -> "(unavailable: ${result.message.take(160)})"
            }
        } else {
            runCatching {
                tree.listRoot(workspace.treeUri, limit)
                    .joinToString(", ") { if (it.isDirectory) it.name + "/" else it.name }
                    .ifBlank { "(empty)" }
            }.getOrElse { "(unavailable: ${it.message?.take(160).orEmpty()})" }
        }
    }

    private suspend fun gitSummary(
        workspace: com.mrredhood.devforge.core.workspace.Workspace,
        identity: WorkspaceIdentity,
    ): String {
        identity.githubOwner?.let {
            return "Git: GitHub-backed repository ${it}/${identity.githubRepository} on branch ${identity.branch}."
        }
        return when (val detected = gitRepositoryService.detect(workspace.treeUri)) {
            is GitDetectionState.Detected -> {
                val repo = detected.repository
                val status = runCatching {
                    gitStatusService.inspect(
                        root = workspace.treeUri,
                        gitDirectory = repo.gitDirectoryUri,
                        headRevision = repo.headRevision,
                        maxFiles = 160,
                    )
                }.getOrNull()
                val counts = status?.files.orEmpty()
                    .groupingBy { it.gitStatus }
                    .eachCount()
                    .entries
                    .sortedBy { it.key.name }
                    .joinToString(", ") { "${it.key.name.lowercase()}=${it.value}" }
                "Git: branch=${repo.branchName ?: "(detached)"} head=${repo.headRevision?.take(12) ?: "(unknown)"} remote=${repo.remoteUrl ?: "(none)"} status=${counts.ifBlank { "unknown" }}."
            }
            is GitDetectionState.Unsupported -> "Git: unsupported (${detected.reason.take(180)})."
            GitDetectionState.NotDetected -> "Git: no repository detected."
            GitDetectionState.Detecting -> "Git: detection in progress."
        }
    }

    private suspend fun buildSummary(identity: WorkspaceIdentity): String {
        val owner = identity.githubOwner ?: return "Build: no GitHub repository binding is available in workspace context."
        val repository = identity.githubRepository ?: return "Build: repository binding is incomplete."
        val receipts = database.buildReceiptDao().observeRecent(10).first()
            .filter { it.githubOwner.equals(owner, true) && it.githubRepository.equals(repository, true) }
            .take(3)
        if (receipts.isEmpty()) return "Build: no recent DevForge build receipts."
        return "Build: " + receipts.joinToString(" | ") {
            "run#${it.runNumber} ${it.target} ${it.state}/${it.conclusion ?: "n/a"} artifact=${it.artifactName.ifBlank { "none" }}"
        }
    }

    private suspend fun editorSummary(
        workspace: com.mrredhood.devforge.core.workspace.Workspace,
    ): String {
        val tabs = database.editorTabDao().list(24)
        val workspaceTree = workspace.treeUri.toString()
        val active = tabs.firstOrNull { tab ->
            tab.isActive && (tab.uri.startsWith(workspaceTree) || workspaceTree.startsWith(tab.uri))
        }
        if (active == null) return "Editor: no active editor tab detected for this workspace."
        val dirty = active.content != active.savedContent
        return "Editor: active=${active.name} dirty=$dirty."
    }

    companion object {
        private const val DEFAULT_LIMIT = 40
        private const val MAX_LIMIT = 120
        private const val MAX_NAME = 120
        private const val MAX_COMPACT_CHARS = 900
        private const val MAX_SNAPSHOT_CHARS = 10_000
    }
}
