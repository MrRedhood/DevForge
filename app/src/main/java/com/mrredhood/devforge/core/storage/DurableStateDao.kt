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


@Dao
interface AgentSharedMemoryDao {
    @Query("SELECT * FROM agent_shared_memory WHERE workspaceId = :workspaceId ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observe(workspaceId: String, limit: Int): Flow<List<AgentSharedMemoryEntity>>

    @Query("SELECT * FROM agent_shared_memory WHERE workspaceId = :workspaceId ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    suspend fun list(workspaceId: String, limit: Int): List<AgentSharedMemoryEntity>

    @Query("SELECT * FROM agent_shared_memory WHERE workspaceId = :workspaceId AND key = :key LIMIT 1")
    suspend fun get(workspaceId: String, key: String): AgentSharedMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: AgentSharedMemoryEntity)

    @Query("DELETE FROM agent_shared_memory WHERE workspaceId = :workspaceId AND key = :key")
    suspend fun delete(workspaceId: String, key: String)

    @Query("DELETE FROM agent_shared_memory WHERE workspaceId = :workspaceId AND memoryId NOT IN (SELECT memoryId FROM agent_shared_memory WHERE workspaceId = :workspaceId ORDER BY updatedAtEpochMs DESC LIMIT :keep)")
    suspend fun prune(workspaceId: String, keep: Int)
}

@Dao
interface AgentHandoffDao {
    @Query("SELECT * FROM agent_handoffs WHERE workspaceId = :workspaceId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observe(workspaceId: String, limit: Int): Flow<List<AgentHandoffEntity>>

    @Query("SELECT * FROM agent_handoffs WHERE workspaceId = :workspaceId AND (toTaskId IS NULL OR toTaskId = :taskId) AND status IN ('PENDING','CLAIMED') ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun available(workspaceId: String, taskId: String, limit: Int): List<AgentHandoffEntity>

    @Query("SELECT * FROM agent_handoffs WHERE handoffId = :handoffId LIMIT 1")
    suspend fun get(handoffId: String): AgentHandoffEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(handoff: AgentHandoffEntity)

    @Query("UPDATE agent_handoffs SET status = 'CLAIMED', claimedAtEpochMs = :now WHERE handoffId = :handoffId AND status = 'PENDING' AND (toTaskId IS NULL OR toTaskId = :taskId)")
    suspend fun claim(handoffId: String, taskId: String, now: Long): Int

    @Query("UPDATE agent_handoffs SET status = 'COMPLETED', completedAtEpochMs = :now WHERE handoffId = :handoffId AND status = 'CLAIMED'")
    suspend fun complete(handoffId: String, now: Long): Int

    @Query("DELETE FROM agent_handoffs WHERE workspaceId = :workspaceId AND status = 'COMPLETED' AND completedAtEpochMs < :cutoff")
    suspend fun pruneCompleted(workspaceId: String, cutoff: Long): Int
}

@Dao
interface AgentFileLeaseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun acquire(lease: AgentFileLeaseEntity): Long

    @Query("DELETE FROM agent_file_leases WHERE expiresAtEpochMs <= :now")
    suspend fun pruneExpired(now: Long): Int

    @Query("DELETE FROM agent_file_leases WHERE workspaceId = :workspaceId AND taskId = :taskId AND path = :path")
    suspend fun release(workspaceId: String, taskId: String, path: String): Int

    @Query("DELETE FROM agent_file_leases WHERE workspaceId = :workspaceId AND taskId = :taskId")
    suspend fun releaseAll(workspaceId: String, taskId: String): Int

    @Query("SELECT * FROM agent_file_leases WHERE workspaceId = :workspaceId ORDER BY acquiredAtEpochMs DESC LIMIT :limit")
    suspend fun list(workspaceId: String, limit: Int): List<AgentFileLeaseEntity>
}
