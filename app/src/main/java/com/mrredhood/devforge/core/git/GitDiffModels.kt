package com.mrredhood.devforge.core.git

import com.mrredhood.devforge.core.editor.DiffLine

/** Read-only structured diff data derived from HEAD, index and worktree state. */
data class GitDiffDocument(
    val path: String,
    val status: GitFileStatus,
    val sections: List<GitDiffSection>,
    val unavailableReason: String? = null,
)

data class GitDiffSection(
    val title: String,
    val beforeLabel: String,
    val afterLabel: String,
    val lines: List<DiffLine>,
)
