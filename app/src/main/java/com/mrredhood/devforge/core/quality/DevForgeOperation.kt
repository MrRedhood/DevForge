package com.mrredhood.devforge.core.quality

import com.mrredhood.devforge.core.security.SecretRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DevForgeOperationType {
    AI,
    AGENT,
    BUILD,
    GIT,
    TERMINAL,
    WORKSPACE,
    ARTIFACT,
    SYNC,
    DIAGNOSTIC,
    BACKUP,
    OTHER,
}

enum class DevForgeOperationState {
    QUEUED,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class DevForgeOperation(
    val id: String,
    val type: DevForgeOperationType,
    val title: String,
    val workspaceId: String?,
    val state: DevForgeOperationState,
    val progressPercent: Int?,
    val statusMessage: String?,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val finishedAtEpochMs: Long? = null,
    val errorMessage: String? = null,
)

object DevForgeOperationCenter {
    private const val MAX_ACTIVE = 64
    private const val MAX_HISTORY = 120
    private const val MAX_TEXT = 300

    private val activeMap = ConcurrentHashMap<String, DevForgeOperation>()
    private val history = ArrayDeque<DevForgeOperation>()
    private val lock = Any()

    private val _operations = MutableStateFlow<List<DevForgeOperation>>(emptyList())
    val operations: StateFlow<List<DevForgeOperation>> = _operations.asStateFlow()

    fun start(
        type: DevForgeOperationType,
        title: String,
        workspaceId: String? = null,
        message: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val operation = DevForgeOperation(
            id = id,
            type = type,
            title = clean(title),
            workspaceId = workspaceId,
            state = DevForgeOperationState.QUEUED,
            progressPercent = 0,
            statusMessage = cleanOrNull(message),
            startedAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        synchronized(lock) {
            trimActive()
            activeMap[id] = operation
            publishLocked()
        }
        return id
    }

    fun running(id: String, message: String? = null, progressPercent: Int? = null) {
        update(id, DevForgeOperationState.RUNNING, message, progressPercent)
    }

    fun waiting(id: String, message: String? = null) {
        update(id, DevForgeOperationState.WAITING, message, null)
    }

    fun succeed(id: String, message: String? = null) {
        finish(id, DevForgeOperationState.SUCCEEDED, message, null)
    }

    fun fail(id: String, message: String) {
        finish(id, DevForgeOperationState.FAILED, null, message)
    }

    fun cancel(id: String, message: String? = null) {
        finish(id, DevForgeOperationState.CANCELLED, message, null)
    }

    fun update(
        id: String,
        state: DevForgeOperationState,
        message: String? = null,
        progressPercent: Int? = null,
    ) {
        synchronized(lock) {
            val current = activeMap[id] ?: return
            activeMap[id] = current.copy(
                state = state,
                progressPercent = progressPercent?.coerceIn(0, 100),
                statusMessage = cleanOrNull(message) ?: current.statusMessage,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            publishLocked()
        }
    }

    fun snapshot(limit: Int = 40): List<DevForgeOperation> =
        synchronized(lock) { (activeMap.values + history.asReversed()).take(limit.coerceIn(1, 120)) }

    private fun finish(
        id: String,
        state: DevForgeOperationState,
        message: String?,
        error: String?,
    ) {
        synchronized(lock) {
            val current = activeMap.remove(id) ?: return
            val now = System.currentTimeMillis()
            val finished = current.copy(
                state = state,
                progressPercent = if (state == DevForgeOperationState.SUCCEEDED) 100 else current.progressPercent,
                statusMessage = cleanOrNull(message),
                updatedAtEpochMs = now,
                finishedAtEpochMs = now,
                errorMessage = cleanOrNull(error),
            )
            history.addLast(finished)
            while (history.size > MAX_HISTORY) history.removeFirst()
            publishLocked()
        }
    }

    private fun trimActive() {
        if (activeMap.size < MAX_ACTIVE) return
        val oldest = activeMap.values.minByOrNull { it.updatedAtEpochMs } ?: return
        activeMap.remove(oldest.id)
        history.addLast(oldest.copy(state = DevForgeOperationState.CANCELLED, finishedAtEpochMs = System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    private fun clean(value: String): String =
        SecretRedactor.redact(value, MAX_TEXT).replace(Regex("[\r\n\t]+"), " ").trim()

    private fun cleanOrNull(value: String?): String? =
        value?.takeIf { it.isNotBlank() }?.let(::clean)

    private fun publishLocked() {
        _operations.value = (activeMap.values + history.asReversed()).sortedByDescending { it.updatedAtEpochMs }.take(120)
    }
}
