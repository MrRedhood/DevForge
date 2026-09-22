package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LiveActionRunUi(
    val owner: String,
    val repository: String,
    val run: GitHubWorkflowRun,
    val jobs: List<GitHubJobLog> = emptyList(),
    val expanded: Boolean = false,
    val logsLoading: Boolean = false,
    val logsError: String? = null,
)

class LiveActionsViewModel(application: Application) : AndroidViewModel(application) {
    private val actions = GitHubActionsGateway.forBuildStore(CredentialSecurityStore(application))
    private val repositoriesGateway = GitHubRepositoryGateway(CredentialSecurityStore(application))
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private val logJobs = mutableMapOf<Long, Job>()

    var runs by mutableStateOf<List<LiveActionRunUi>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshJob?.isActive == true) return
        if (!silent) isLoading = true
        val previous = runs.associateBy { it.run.id }

        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val repoResult = repositoriesGateway.listRepositories()
            val repos = (repoResult as? GitHubRepositoryListResult.Success)
                ?.repositories
                ?.take(30)
                .orEmpty()
            val next = mutableListOf<LiveActionRunUi>()

            for (repo in repos) {
                val result = actions.listRecentRuns(repo.owner, repo.name, null, 10)
                val workflowRuns = (result as? GitHubWorkflowRunsResult.Success)
                    ?.runs
                    .orEmpty()
                workflowRuns
                    .filter { it.status in ACTIVE_STATES || it.status == "completed" }
                    .take(6)
                    .forEach { run ->
                        val cached = previous[run.id]
                        next += cached?.copy(
                            owner = repo.owner,
                            repository = repo.name,
                            run = run,
                        ) ?: LiveActionRunUi(repo.owner, repo.name, run)
                    }
            }

            val sorted = next
                .distinctBy { it.run.id }
                .sortedByDescending { it.run.createdAt ?: "" }
                .take(40)

            withContext(Dispatchers.Main.immediate) {
                isLoading = false
                if (repoResult is GitHubRepositoryListResult.Failure) {
                    error = repoResult.message
                    // Keep the last stable list during a transient repository/API failure.
                    // This prevents the UI from disappearing and rebuilding on each failed poll.
                    return@withContext
                } else {
                    error = null
                }

                val currentById = runs.associateBy { it.run.id }
                runs = sorted.map { item ->
                    val current = currentById[item.run.id]
                    if (current == null) {
                        item
                    } else {
                        item.copy(
                            jobs = current.jobs,
                            expanded = current.expanded,
                            logsLoading = current.logsLoading,
                            logsError = current.logsError,
                        )
                    }
                }
            }
        }.also { job ->
            job.invokeOnCompletion { refreshJob = null }
        }
    }

    fun toggle(runId: Long) {
        val item = runs.firstOrNull { it.run.id == runId } ?: return
        val expanded = !item.expanded
        runs = runs.map {
            if (it.run.id == runId) it.copy(expanded = expanded) else it
        }
        if (expanded) {
            refreshLogs(item.owner, item.repository, runId)
        } else {
            logJobs.remove(runId)?.cancel()
        }
    }

    private fun refreshLogs(owner: String, repository: String, runId: Long) {
        if (logJobs[runId]?.isActive == true) return

        runs = runs.map {
            if (it.run.id == runId) {
                it.copy(logsLoading = true, logsError = null)
            } else it
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            when (val result = actions.fetchLogs(owner, repository, runId, maxJobs = 12, maxBytes = 900_000)) {
                is GitHubLogsResult.Success -> withContext(Dispatchers.Main.immediate) {
                    runs = runs.map {
                        if (it.run.id == runId && it.expanded) {
                            it.copy(
                                jobs = result.jobs,
                                logsLoading = false,
                                logsError = null,
                            )
                        } else it
                    }
                }
                is GitHubLogsResult.Failure -> withContext(Dispatchers.Main.immediate) {
                    runs = runs.map {
                        if (it.run.id == runId && it.expanded) {
                            it.copy(logsLoading = false, logsError = result.message)
                        } else it
                    }
                }
            }
        }
        logJobs[runId] = job
        job.invokeOnCompletion { logJobs.remove(runId) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        refreshJob?.cancel()
        logJobs.values.forEach(Job::cancel)
        logJobs.clear()
        super.onCleared()
    }

    companion object {
        private val ACTIVE_STATES = setOf(
            "queued",
            "in_progress",
            "waiting",
            "requested",
            "pending",
        )
    }
}
