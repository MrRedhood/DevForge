package com.mrredhood.devforge.core.agent

import android.content.Context
import android.net.Uri
import com.mrredhood.devforge.core.git.GitCommitHistoryService
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.github.GitHubCommitHistoryResult
import com.mrredhood.devforge.core.github.GitHubRepositoryGateway
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Read-only AI Git history tool for local Git and GitHub-backed workspaces. */
class AgentGitToolProvider(context: Context) {
    private val appContext = context.applicationContext
    private val database = DevForgeDatabase.get(appContext)
    private val gitRepositoryService = GitRepositoryService(appContext.contentResolver)
    private val gitHistoryService = GitCommitHistoryService(appContext.contentResolver)
    private val githubStore = GitHubWorkspaceStore(appContext)
    private val githubGateway = GitHubRepositoryGateway(CredentialSecurityStore(appContext))

    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry = registry.register(GetGitLogTool())

    private inner class GetGitLogTool : AgentTool {
        override val definition = AgentToolDefinition(
            id = AgentToolId.GET_GIT_LOG,
            description = "Read bounded recent Git commit history from the selected local Git repository or GitHub-backed workspace for evidence during an AI operation.",
            capability = Capability.READ_WORKSPACE,
            risk = RiskLevel.R0,
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult =
            withContext(Dispatchers.IO) {
                try {
                    val args = JSONObject(request.argumentsJson)
                    val limit = args.optInt("limit", 20).coerceIn(1, 50)
                    val workspace = database.workspaceDao().findById(context.workspaceId)
                        ?: return@withContext AgentToolResult.Failure("The selected workspace is unavailable.")

                    val remote = githubStore.get(workspace.id)
                    if (remote != null) {
                        return@withContext when (val result =
                            githubGateway.listCommits(remote.owner, remote.repository, remote.branch, limit)) {
                            is GitHubCommitHistoryResult.Failure -> AgentToolResult.Failure(result.message)
                            is GitHubCommitHistoryResult.Success -> AgentToolResult.Success(
                                summary = "Read " + result.commits.size + " GitHub commits from " + remote.owner + "/" + remote.repository + ".",
                                output = JSONObject()
                                    .put("source", "github")
                                    .put("repository", remote.owner + "/" + remote.repository)
                                    .put("branch", remote.branch)
                                    .put("commits", JSONArray(result.commits.map {
                                        JSONObject()
                                            .put("sha", it.sha)
                                            .put("shortSha", it.sha.take(12))
                                            .put("subject", it.subject)
                                            .put("author", it.author)
                                            .put("authoredAt", it.authoredAt ?: JSONObject.NULL)
                                            .put("url", it.url ?: JSONObject.NULL)
                                    }))
                                    .toString(),
                            )
                        }
                    }

                    when (val detected = gitRepositoryService.detect(Uri.parse(workspace.treeUri))) {
                        is GitDetectionState.Detected -> {
                            val snapshot = gitHistoryService.load(detected.repository, limit)
                            AgentToolResult.Success(
                                summary = "Read " + snapshot.commits.size + " local Git commits.",
                                output = JSONObject()
                                    .put("source", "local")
                                    .put("branch", detected.repository.branchName ?: JSONObject.NULL)
                                    .put("head", detected.repository.headRevision ?: JSONObject.NULL)
                                    .put("truncated", snapshot.truncated)
                                    .put("commits", JSONArray(snapshot.commits.map { commit ->
                                        JSONObject()
                                            .put("sha", commit.commitId)
                                            .put("shortSha", commit.shortId)
                                            .put("subject", commit.subject)
                                            .put("author", commit.author)
                                            .put("authoredAtEpochMs", commit.authoredAtEpochMs ?: JSONObject.NULL)
                                            .put("parents", JSONArray(commit.parents))
                                            .put("changedFileCount", commit.changedFileCount)
                                            .put("changedFilesTruncated", commit.changedFilesTruncated)
                                    }))
                                    .toString(),
                            )
                        }
                        GitDetectionState.NotDetected -> AgentToolResult.Failure("No Git repository was detected in the selected workspace.")
                        GitDetectionState.Detecting -> AgentToolResult.Failure("Git repository detection is still running; retry the Git log tool.")
                        is GitDetectionState.Unsupported -> AgentToolResult.Failure("Git history is unavailable: " + detected.reason)
                    }
                } catch (error: Throwable) {
                    AgentToolResult.Failure(error.message ?: "Unable to read Git history.")
                }
            }
    }
}
