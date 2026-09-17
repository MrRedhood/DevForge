package com.mrredhood.devforge.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "editor_tabs")
data class EditorTabEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val content: String,
    val savedContent: String,
    val isActive: Boolean,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "editor_snapshots")
data class EditorSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val uri: String,
    val name: String,
    val content: String,
    val contentHash: String,
    val reason: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey val taskId: String,
    val workspaceId: String,
    val title: String,
    val instruction: String,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val payload: String?,
    val errorMessage: String?,
)

@Entity(tableName = "automation_definitions")
data class AutomationEntity(
    @PrimaryKey val automationId: String,
    val workspaceId: String?,
    val name: String,
    val status: String,
    val triggerType: String,
    val schedule: String?,
    val actionGraph: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "automation_runs")
data class AutomationRunEntity(
    @PrimaryKey val runId: String,
    val automationId: String,
    val status: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val errorMessage: String?,
    val receiptJson: String?,
)

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey val eventId: String,
    val workspaceId: String?,
    val actionId: String?,
    val capability: String?,
    val risk: String?,
    val eventType: String,
    val summary: String,
    val metadataJson: String?,
    val createdAtEpochMs: Long,
)
