package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EditorTabDao {
    @Query("SELECT * FROM editor_tabs ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    suspend fun list(limit: Int): List<EditorTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: EditorTabEntity)

    @Query("DELETE FROM editor_tabs WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM editor_tabs")
    suspend fun clear()

    @Query("UPDATE editor_tabs SET isActive = 0")
    suspend fun clearActive()
}

@Dao
interface EditorSnapshotDao {
    @Query("SELECT * FROM editor_snapshots WHERE uri = :uri ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun list(uri: String, limit: Int): List<EditorSnapshotEntity>

    @Query("SELECT * FROM editor_snapshots WHERE uri = :uri ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun latest(uri: String): EditorSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: EditorSnapshotEntity)

    @Query("DELETE FROM editor_snapshots WHERE uri = :uri")
    suspend fun clear(uri: String)

    @Query("DELETE FROM editor_snapshots WHERE uri = :uri AND snapshotId NOT IN (SELECT snapshotId FROM editor_snapshots WHERE uri = :uri ORDER BY createdAtEpochMs DESC LIMIT :keep)")
    suspend fun prune(uri: String, keep: Int)
}

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks WHERE workspaceId = :workspaceId ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observe(workspaceId: String, limit: Int): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun get(taskId: String): AgentTaskEntity?

    @Query("SELECT * FROM agent_tasks WHERE workspaceId = :workspaceId ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    suspend fun list(workspaceId: String, limit: Int): List<AgentTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: AgentTaskEntity)

    @Query("DELETE FROM agent_tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: String)

    @Query("UPDATE agent_tasks SET status = 'PAUSED', updatedAtEpochMs = :updatedAt WHERE taskId = :taskId AND status IN ('QUEUED','PLANNING','RUNNING')")
    suspend fun pause(taskId: String, updatedAt: Long): Int

    @Query("UPDATE agent_tasks SET status = 'QUEUED', updatedAtEpochMs = :updatedAt, errorMessage = NULL, completedAtEpochMs = NULL WHERE taskId = :taskId AND status = 'PAUSED'")
    suspend fun resume(taskId: String, updatedAt: Long): Int

    @Query("UPDATE agent_tasks SET status = 'PAUSED', updatedAtEpochMs = :updatedAt, errorMessage = :message WHERE workspaceId = :workspaceId AND status IN ('PLANNING','RUNNING')")
    suspend fun recoverRunning(workspaceId: String, updatedAt: Long, message: String): Int
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_definitions ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automation_definitions ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    suspend fun list(limit: Int): List<AutomationEntity>

    @Query("SELECT * FROM automation_definitions WHERE automationId = :automationId LIMIT 1")
    suspend fun get(automationId: String): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(automation: AutomationEntity)

    @Query("DELETE FROM automation_definitions WHERE automationId = :automationId")
    suspend fun delete(automationId: String)

    @Query("SELECT * FROM automation_runs WHERE automationId = :automationId ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun recentRuns(automationId: String, limit: Int): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRun(runId: String): AutomationRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: AutomationRunEntity)
}

@Dao
interface AutomationTriggerStateDao {
    @Query("SELECT * FROM automation_trigger_state WHERE automationId = :automationId LIMIT 1")
    suspend fun get(automationId: String): AutomationTriggerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AutomationTriggerStateEntity)

    @Query("DELETE FROM automation_trigger_state WHERE automationId = :automationId")
    suspend fun delete(automationId: String)
}

@Dao
interface AuditEventDao {
    @Query("SELECT * FROM audit_events ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE workspaceId = :workspaceId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeForWorkspace(workspaceId: String, limit: Int): Flow<List<AuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: AuditEventEntity)

    @Query("DELETE FROM audit_events WHERE createdAtEpochMs < :cutoff")
    suspend fun prune(cutoff: Long): Int
}
