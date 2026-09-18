package com.mrredhood.devforge.core.terminal

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.settings.DevForgeSettingsRepository
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaceRepository = WorkspaceDatabaseRepository(application)
    private val sessionsRepository = TerminalSessionRepository(application)
    private val settings = DevForgeSettingsRepository(application)
    private val database = DevForgeDatabase.get(application)
    private val approvals = ApprovalRepository(database.approvalDao())
    private val capability = TerminalCapability(application, approvals, DurableStateRepository(database))
    private var workspaceJob: Job? = null
    private var approvalJob: Job? = null
    private var runJob: Job? = null

    var workspaceId by mutableStateOf<String?>(null)
        private set
    var sessions by mutableStateOf<List<TerminalSession>>(emptyList())
        private set
    var activeSessionId by mutableStateOf<String?>(null)
        private set
    var executable by mutableStateOf(TerminalExecutable.PWD)
        private set
    var argsText by mutableStateOf("")
    var output by mutableStateOf("")
        private set
    var isRunning by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    init {
        workspaceJob = viewModelScope.launch {
            workspaceRepository.activeWorkspace.collectLatest { workspace ->
                runJob?.cancel()
                workspaceId = workspace?.id
                output = ""
                sessions = workspace?.let { sessionsRepository.ensureDefault(it.id) } ?: emptyList()
                activeSessionId = sessions.firstOrNull()?.id
            }
        }
        approvalJob = viewModelScope.launch(Dispatchers.IO) {
            approvals.observeApproved("terminal:").collect { approved ->
                approved.forEach { executeApproved(it) }
            }
        }
    }

    fun selectExecutable(value: TerminalExecutable) {
        if (!isRunning) executable = value
    }

    fun selectSession(id: String) {
        if (!isRunning && sessions.any { it.id == id }) {
            activeSessionId = id
            output = ""
            statusMessage = null
        }
    }

    fun newSession() {
        val workspace = workspaceId ?: return
        val session = sessionsRepository.add(workspace)
        sessions = sessionsRepository.list(workspace)
        activeSessionId = session.id
        output = ""
    }

    fun closeSession() {
        val workspace = workspaceId ?: return
        val id = activeSessionId ?: return
        if (isRunning) return
        sessions = sessionsRepository.delete(workspace, id)
        if (sessions.isEmpty()) sessions = sessionsRepository.ensureDefault(workspace)
        activeSessionId = sessions.firstOrNull()?.id
        output = ""
    }

    fun run() {
        val workspace = workspaceId ?: run { statusMessage = "Choose a workspace first."; return }
        if (isRunning) return
        val session = sessions.firstOrNull { it.id == activeSessionId }
            ?: sessionsRepository.ensureDefault(workspace).first()
        val rawArgs = argsText.trim()
        val args = if (rawArgs.isBlank()) emptyList() else rawArgs.split(Regex("\\s+"))
        if (args.size > TerminalCommandPolicy.MAX_ARGS) {
            statusMessage = "Too many arguments."
            return
        }
        val command = TerminalCommand(
            executable = executable,
            args = args,
            workingDirectory = session.workingDirectory,
            timeoutMs = settings.snapshot().terminalTimeoutMs,
            sessionId = session.id,
        )
        isRunning = true
        statusMessage = null
        output = ""
        runJob = viewModelScope.launch(Dispatchers.IO) {
            val result = capability.executeStreaming(workspace, command) { chunk ->
                launch(Dispatchers.Main.immediate) {
                    output = (output + chunk).take(TerminalCommandPolicy.MAX_OUTPUT_BYTES)
                }
            }
            launch(Dispatchers.Main.immediate) {
                when (result) {
                    is TerminalCapabilityResult.ApprovalRequired -> statusMessage = "Approval required. Open Approvals; the command will resume after approval."
                    is TerminalCapabilityResult.Failure -> statusMessage = result.message
                    is TerminalCapabilityResult.Completed -> {
                        statusMessage = "Exited " + (result.execution.exitCode ?: "without an exit code") + " · " + result.execution.durationMs + " ms"
                        output = result.execution.output.take(TerminalCommandPolicy.MAX_OUTPUT_BYTES)
                    }
                }
                isRunning = false
            }
        }
    }

    private suspend fun executeApproved(approval: ApprovalEntity) {
        val payload = runCatching { JSONObject(approval.payload) }.getOrNull() ?: return
        val workspace = payload.optString("workspaceId").takeIf { it.isNotBlank() } ?: return
        if (workspace != workspaceId || isRunning) return
        val executable = runCatching { TerminalExecutable.valueOf(payload.optString("executable")) }.getOrNull() ?: return
        val array = payload.optJSONArray("args") ?: JSONArray()
        val args = buildList {
            for (i in 0 until minOf(array.length(), TerminalCommandPolicy.MAX_ARGS)) add(array.optString(i))
        }
        val sessionId = payload.optString("sessionId").takeIf { it.isNotBlank() } ?: return
        if (sessions.none { it.id == sessionId }) return
        withContext(Dispatchers.Main.immediate) {
            activeSessionId = sessionId
            output = ""
        }
        val command = TerminalCommand(
            executable = executable,
            args = args,
            workingDirectory = payload.optString("workingDirectory"),
            timeoutMs = payload.optLong("timeoutMs", settings.snapshot().terminalTimeoutMs),
            sessionId = sessionId,
        )
        isRunning = true
        statusMessage = "Executing approved command…"
        output = ""
        val result = capability.executeApprovedStreaming(approval.approvalId, workspace, command) { chunk ->
            launch(Dispatchers.Main.immediate) {
                output = (output + chunk).take(TerminalCommandPolicy.MAX_OUTPUT_BYTES)
            }
        }
        isRunning = false
        if (result is TerminalCapabilityResult.Failure) statusMessage = result.message
        else statusMessage = "Approved terminal command finished."
    }

    fun clearOutput() { output = "" }
    fun dismissStatus() { statusMessage = null }
    
    override fun onCleared() {
        workspaceJob?.cancel()
        approvalJob?.cancel()
        runJob?.cancel()
        super.onCleared()
    }
}
