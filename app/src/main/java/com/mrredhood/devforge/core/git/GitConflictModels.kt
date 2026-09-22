package com.mrredhood.devforge.core.git

/** Bounded snapshot of one active isolated Git conflict-resolution session. */
data class GitConflictSessionSnapshot(
    val sessionId: String,
    val operation: String,
    val paths: List<String>,
    val selectedPath: String?,
    val files: List<GitConflictFile>,
    val approvalId: String? = null,
    val createdAtEpochMs: Long,
)

data class GitConflictFile(
    val path: String,
    val ours: String?,
    val base: String?,
    val theirs: String?,
    val merged: String?,
    val binary: Boolean,
    val oversized: Boolean,
)
