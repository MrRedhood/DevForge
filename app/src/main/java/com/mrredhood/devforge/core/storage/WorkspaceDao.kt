package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<WorkspaceEntity?>

    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("UPDATE workspaces SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE workspaces SET isActive = 1, lastOpenedAt = :openedAt WHERE id = :workspaceId")
    suspend fun activate(workspaceId: String, openedAt: Long)

    @Query("DELETE FROM workspaces WHERE id = :workspaceId")
    suspend fun delete(workspaceId: String)

    @Query("SELECT * FROM workspaces WHERE id = :workspaceId LIMIT 1")
    suspend fun findById(workspaceId: String): WorkspaceEntity?
}
