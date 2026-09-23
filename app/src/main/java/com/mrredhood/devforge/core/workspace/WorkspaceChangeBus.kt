package com.mrredhood.devforge.core.workspace

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class WorkspaceChangeEvent(
    val workspaceId: String,
    val paths: List<String> = emptyList(),
)

object WorkspaceChangeBus {
    private val _events = MutableSharedFlow<WorkspaceChangeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<WorkspaceChangeEvent> = _events

    fun emit(workspaceId: String, paths: List<String> = emptyList()) {
        if (workspaceId.isBlank()) return
        _events.tryEmit(
            WorkspaceChangeEvent(
                workspaceId = workspaceId,
                paths = paths.map { it.trim('/') }.filter(String::isNotBlank).distinct().take(100),
            ),
        )
    }
}
