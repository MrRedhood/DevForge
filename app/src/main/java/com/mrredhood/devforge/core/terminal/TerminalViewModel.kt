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
import kotlinx.coroutines.CancellationException
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
    var commandLine by mutableStateOf("")
        private set
    var historyIndex by mutableStateOf(-1)
        private set
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
                historyIndex = -1
                sessions = workspace?.let { sessionsRepository.ensureDefault(it.id) } ?: emptyList()
                activeSessionId = sessions.firstOrNull()?.id
                if (workspace != null) {
                    capability.prepareWorkspace(workspace.id, workspace.treeUri)
                        .onFailure { error ->
                            statusMessage = error.message ?: "Unable to prepare Terminal."
                        }
                }
            }
        }
        approvalJob = viewModelScope.launch(Dispatchers.IO) {
            approvals.observeApproved("terminal:").collect { approved ->
                approved.forEach { executeApproved(it) }
            }
        }
    }

    fun updateCommandLine(value: String) {
        commandLine = value.take(TerminalCommandPolicy.MAX_COMMAND_BYTES)
        historyIndex = -1
    }

    fun historyPrevious() {
        val history = currentSession()?.history.orEmpty()
        if (history.isEmpty()) return
        historyIndex = if (historyIndex < 0) history.lastIndex else (historyIndex - 1).coerceAtLeast(0)
        commandLine = history[historyIndex]
    }

    fun historyNext() {
        val history = currentSession()?.history.orEmpty()
        if (history.isEmpty() || historyIndex < 0) return
        historyIndex += 1
        if (historyIndex > history.lastIndex) {
            historyIndex = -1
            commandLine = ""
        } else {
            commandLine = history[historyIndex]
        }
    }

    fun selectSession(id: String) {
        if (!isRunning && sessions.any { it.id == id }) {
            activeSessionId = id
            commandLine = ""
            historyIndex = -1
            output = ""
            statusMessage = null
        }
    }

    fun newSession() {
        val workspace = workspaceId ?: run {
            statusMessage = "Create a workspace in Files first."
            return
        }
        val session = sessionsRepository.add(workspace)
        sessions = sessionsRepository.list(workspace)
        activeSessionId = session.id
        commandLine = ""
        output = ""
        historyIndex = -1
    }

    fun closeSession() {
        val workspace = workspaceId ?: return
        val id = activeSessionId ?: return
        if (isRunning) return
        sessions = sessionsRepository.delete(workspace, id)
        if (sessions.isEmpty()) sessions = sessionsRepository.ensureDefault(workspace)
        activeSessionId = sessions.firstOrNull()?.id
        commandLine = ""
        output = ""
        historyIndex = -1
    }

    fun run() {
        val workspace = workspaceId ?: run {
            statusMessage = "Create a workspace in Files first."
            return
        }
        if (isRunning) return
        val session = currentSession() ?: sessionsRepository.ensureDefault(workspace).first()
        val line = commandLine.trim()
        if (line.isBlank()) return

        val parsed = runCatching {
            TerminalCommandParser.parse(
                line,
                session.workingDirectory,
                settings.snapshot().terminalTimeoutMs,
                session.id,
            )
        }.getOrElse {
            appendTerminalLine(prompt(session) + " " + line)
            appendTerminalLine("devforge: " + (it.message ?: "invalid command"))
            commandLine = ""
            return
        }

        val history = (session.history + line).takeLast(TerminalSessionRepository.MAX_HISTORY)
        sessionsRepository.updateHistory(workspace, session.id, history)
        sessions = sessionsRepository.list(workspace)
        historyIndex = -1
        commandLine = ""

        when (parsed) {
            TerminalParsedCommand.Clear -> output = ""
            TerminalParsedCommand.History -> {
                appendTerminalLine(prompt(session) + " history")
                history.forEachIndexed { index, item ->
                    appendTerminalLine("%3d  %s".format(index + 1, item))
                }
            }
            TerminalParsedCommand.Help -> {
                appendTerminalLine(prompt(session) + " help")
                appendTerminalLine("pwd ls cd cat grep find head tail wc sort uniq cut tr sed mkdir touch rm cp mv chmod echo printf history clear")
            }
            is TerminalParsedCommand.ChangeDirectory -> {
                val next = normalizeDirectory(session.workingDirectory, parsed.path)
                val updated = sessionsRepository.updateWorkingDirectory(workspace, session.id, next)
                sessions = sessionsRepository.list(workspace)
                activeSessionId = updated.id
                appendTerminalLine(prompt(session) + " cd " + parsed.path)
            }
            is TerminalParsedCommand.External -> {
                appendTerminalLine(prompt(session) + " " + line)
                executeExternal(workspace, parsed.command)
            }
        }
    }

    private fun executeExternal(workspace: String, command: TerminalCommand) {
        isRunning = true
        statusMessage = null
        runJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = capability.executeStreaming(workspace, command) { chunk ->
                    withContext(Dispatchers.Main.immediate) {
                        output = (output + chunk).takeLast(TerminalCommandPolicy.MAX_OUTPUT_BYTES * 8)
                    }
                }
                withContext(Dispatchers.Main.immediate) {
                    when (result) {
                        is TerminalCapabilityResult.ApprovalRequired -> statusMessage = "Approval needed in More → Approvals."
                        is TerminalCapabilityResult.Failure -> statusMessage = result.message
                        is TerminalCapabilityResult.Completed -> {
                            statusMessage = "exit " + (result.execution.exitCode ?: -1) +
                                " · " + result.execution.durationMs + " ms"
                        }
                    }
                    isRunning = false
                }
            } catch (cancelled: CancellationException) {
                withContext(Dispatchers.Main.immediate) {
                    isRunning = false
                    statusMessage = "Command stopped."
                }
                throw cancelled
            } catch (error: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    isRunning = false
                    statusMessage = error.message ?: "Terminal command failed."
                }
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
        val command = TerminalCommand(
            executable,
            args,
            payload.optString("workingDirectory"),
            payload.optLong("timeoutMs", settings.snapshot().terminalTimeoutMs),
            sessionId,
        )
        withContext(Dispatchers.Main.immediate) {
            activeSessionId = sessionId
            output = ""
            isRunning = true
            statusMessage = "Running approved command…"
        }
        val result = capability.executeApprovedStreaming(approval.approvalId, workspace, command) { chunk ->
            withContext(Dispatchers.Main.immediate) {
                output = (output + chunk).takeLast(TerminalCommandPolicy.MAX_OUTPUT_BYTES * 8)
            }
        }
        withContext(Dispatchers.Main.immediate) {
            isRunning = false
            statusMessage = when (result) {
                is TerminalCapabilityResult.Failure -> result.message
                is TerminalCapabilityResult.Completed -> "exit " + (result.execution.exitCode ?: -1)
                is TerminalCapabilityResult.ApprovalRequired -> "Approval needed."
            }
        }
    }

    fun clearOutput() { output = "" }
    fun dismissStatus() { statusMessage = null }

    private fun currentSession(): TerminalSession? = sessions.firstOrNull { it.id == activeSessionId }

    private fun prompt(session: TerminalSession): String {
        val path = session.workingDirectory.takeIf { it.isNotBlank() }?.let { "~/" + it } ?: "~"
        return "devforge:" + path + "$"
    }

    private fun appendTerminalLine(line: String) {
        output = (if (output.isBlank()) line else output.trimEnd() + "\n" + line)
            .takeLast(TerminalCommandPolicy.MAX_OUTPUT_BYTES * 8)
    }

    private fun normalizeDirectory(current: String, requested: String): String {
        val target = requested.trim().ifBlank { "~" }
        val base = if (target.startsWith("/")) emptyList() else current.split('/').filter(String::isNotBlank)
        val source = target.removePrefix("~/").removePrefix("/")
        val result = ArrayDeque<String>()
        (base + source.split('/')).forEach { part ->
            when {
                part.isBlank() || part == "." || part == "~" -> Unit
                part == ".." -> if (result.isNotEmpty()) result.removeLast()
                else -> {
                    require(part != ".git") { "The .git directory is protected." }
                    require(part.length <= 255) { "Directory name is too long." }
                    result.addLast(part)
                }
            }
        }
        return result.joinToString("/")
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        approvalJob?.cancel()
        runJob?.cancel()
        super.onCleared()
    }
}
