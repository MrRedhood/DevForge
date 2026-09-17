package com.mrredhood.devforge.core.git

import android.net.Uri

sealed interface GitDetectionState {
    data object NotDetected : GitDetectionState
    data object Detecting : GitDetectionState
    data class Detected(val repository: GitRepositoryState) : GitDetectionState
    data class Unsupported(val reason: String) : GitDetectionState
}

data class GitRepositoryState(
    val rootUri: Uri,
    val gitDirectoryUri: Uri,
    val branchName: String?,
    val headRevision: String?,
    val remoteUrl: String?,
    val detachedHead: Boolean,
    val branches: List<GitBranch> = emptyList(),
    val supportsMetadataRead: Boolean = true,
    val statusAvailability: GitStatusAvailability = GitStatusAvailability.MetadataOnly,
)

data class GitBranch(
    val name: String,
    val revision: String? = null,
    val isCurrent: Boolean = false,
    val isLocal: Boolean = true,
)

enum class GitStatusAvailability {
    NotImplemented,
    MetadataOnly,
    IndexAwareWorktree,
    IndexAndHeadAware,
}

enum class GitFileStatus {
    Clean,
    Modified,
    Deleted,
    Untracked,
    Staged,
    StagedAndModified,
    Conflict,
    Unchecked,
}
