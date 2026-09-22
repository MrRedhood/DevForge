package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapabilityGrantDao {
    @Query("SELECT * FROM capability_grants WHERE workspaceId = :workspaceId AND enabled = 1 ORDER BY capability ASC LIMIT :limit")
    fun observeForWorkspace(workspaceId: String, limit: Int): Flow<List<CapabilityGrantEntity>>

    @Query("SELECT * FROM capability_grants WHERE workspaceId = :workspaceId AND capability = :capability AND enabled = 1 AND (expiresAtEpochMs IS NULL OR expiresAtEpochMs > :now) LIMIT 1")
    suspend fun getActive(workspaceId: String, capability: String, now: Long): CapabilityGrantEntity?

    @Query("SELECT * FROM capability_grants WHERE enabled = 1 AND (expiresAtEpochMs IS NULL OR expiresAtEpochMs > :now) ORDER BY workspaceId ASC, capability ASC LIMIT :limit")
    suspend fun listActive(now: Long, limit: Int): List<CapabilityGrantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grant: CapabilityGrantEntity)

    @Query("UPDATE capability_grants SET enabled = 0 WHERE workspaceId = :workspaceId AND capability = :capability")
    suspend fun revoke(workspaceId: String, capability: String): Int

    @Query("DELETE FROM capability_grants WHERE expiresAtEpochMs IS NOT NULL AND expiresAtEpochMs <= :now")
    suspend fun pruneExpired(now: Long): Int
}
