package com.mrredhood.devforge.core.editor

import android.net.Uri

/** A single open document with an in-memory editing buffer. */
data class EditorTab(
    val uri: Uri,
    val name: String,
    val content: String,
    val savedContent: String,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isDirty: Boolean get() = content != savedContent
}

data class RecoveryDraft(
    val uri: Uri,
    val name: String,
    val content: String,
    val savedAt: Long,
)
