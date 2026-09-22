package com.mrredhood.devforge.core.editor

import android.net.Uri

/** Immutable local content checkpoint used by recovery, diff and later agent tooling. */
data class ContentSnapshot(
    val id: String,
    val uri: Uri,
    val name: String,
    val content: String,
    val contentHash: String,
    val createdAt: Long,
    val reason: SnapshotReason,
)

enum class SnapshotReason {
    OPEN,
    MANUAL,
    AUTOSAVE,
    BEFORE_AGENT_CHANGE,
    BEFORE_EXTERNAL_CHANGE,
    RECOVERY,
}
