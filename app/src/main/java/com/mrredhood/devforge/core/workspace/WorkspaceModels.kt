package com.mrredhood.devforge.core.workspace

import android.net.Uri
import java.util.UUID

data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val treeUri: Uri,
    val lastOpenedAt: Long = System.currentTimeMillis(),
)

data class WorkspaceEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val childCount: Int? = null,
)
