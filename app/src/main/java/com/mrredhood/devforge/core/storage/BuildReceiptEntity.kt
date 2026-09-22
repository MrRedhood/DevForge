package com.mrredhood.devforge.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mrredhood.devforge.core.build.BuildConfiguration
import com.mrredhood.devforge.core.build.BuildTarget
import com.mrredhood.devforge.core.github.BuildHistoryEntry

@Entity(tableName = "build_receipts")
data class BuildReceiptEntity(
    @PrimaryKey val runId: Long,
    val runNumber: Long,
    val githubOwner: String,
    val githubRepository: String,
    val workflowFile: String,
    val branch: String,
    val buildTask: String,
    val artifactName: String,
    val target: String,
    val state: String,
    val conclusion: String?,
    val htmlUrl: String?,
    val updatedAt: String?,
    val recordedAtEpochMs: Long,
)

fun BuildReceiptEntity.toDomain(): BuildHistoryEntry = BuildHistoryEntry(
    runId = runId,
    runNumber = runNumber,
    configuration = BuildConfiguration(
        githubOwner = githubOwner,
        githubRepository = githubRepository,
        workflowFile = workflowFile,
        branch = branch,
        buildTask = buildTask,
        artifactName = artifactName,
        target = BuildTarget.entries.firstOrNull { it.name == target } ?: BuildTarget.DebugApk,
    ),
    state = state,
    conclusion = conclusion,
    htmlUrl = htmlUrl,
    updatedAt = updatedAt,
    recordedAtEpochMs = recordedAtEpochMs,
)

fun BuildHistoryEntry.toEntity(): BuildReceiptEntity = BuildReceiptEntity(
    runId = runId,
    runNumber = runNumber,
    githubOwner = configuration.githubOwner,
    githubRepository = configuration.githubRepository,
    workflowFile = configuration.workflowFile,
    branch = configuration.branch,
    buildTask = configuration.buildTask,
    artifactName = configuration.artifactName,
    target = configuration.target.name,
    state = state,
    conclusion = conclusion,
    htmlUrl = htmlUrl,
    updatedAt = updatedAt,
    recordedAtEpochMs = recordedAtEpochMs,
)
