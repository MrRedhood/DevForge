package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.build.BuildConfiguration

/** Read-only GitHub Actions execution data exposed to the Build Center. */
data class GitHubRunSnapshot(
    val id: Long,
    val runNumber: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String?,
    val branch: String,
    val event: String,
    val createdAt: String?,
    val updatedAt: String?,
)

data class GitHubArtifact(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val expired: Boolean,
    val archiveDownloadUrl: String?,
    val createdAt: String?,
    val expiresAt: String?,
)

data class GitHubJobLog(
    val jobId: Long,
    val jobName: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String?,
    val text: String,
)

sealed interface GitHubRunResult {
    data class Success(val run: GitHubRunSnapshot) : GitHubRunResult
    data class Failure(val message: String) : GitHubRunResult
}

sealed interface GitHubArtifactsResult {
    data class Success(val artifacts: List<GitHubArtifact>) : GitHubArtifactsResult
    data class Failure(val message: String) : GitHubArtifactsResult
}

sealed interface GitHubLogsResult {
    data class Success(val jobs: List<GitHubJobLog>, val truncated: Boolean) : GitHubLogsResult
    data class Failure(val message: String) : GitHubLogsResult
}

data class BuildHistoryEntry(
    val runId: Long,
    val runNumber: Long,
    val configuration: BuildConfiguration,
    val state: String,
    val conclusion: String?,
    val htmlUrl: String?,
    val updatedAt: String?,
    val recordedAtEpochMs: Long,
)
