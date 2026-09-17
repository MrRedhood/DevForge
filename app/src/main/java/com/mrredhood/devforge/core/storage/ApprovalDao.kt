package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ApprovalDao {
    @Query("SELECT * FROM approval_actions WHERE status = 'PENDING' ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observePending(limit: Int): Flow<List<ApprovalEntity>>

    @Query("SELECT * FROM approval_actions WHERE approvalId = :approvalId LIMIT 1")
    fun observeById(approvalId: String): Flow<ApprovalEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(action: ApprovalEntity)

    @Query("UPDATE approval_actions SET status = :status, resolvedAtEpochMs = :resolvedAt WHERE approvalId = :approvalId AND status = 'PENDING'")
    suspend fun resolve(approvalId: String, status: String, resolvedAt: Long): Int

    @Query("UPDATE approval_actions SET status = 'EXPIRED', resolvedAtEpochMs = :now WHERE status = 'PENDING' AND expiresAtEpochMs <= :now")
    suspend fun expire(now: Long): Int

    @Query("DELETE FROM approval_actions WHERE status != 'PENDING' AND resolvedAtEpochMs IS NOT NULL AND resolvedAtEpochMs < :cutoff")
    suspend fun pruneResolved(cutoff: Long): Int

    @Transaction
    suspend fun insertPending(action: ApprovalEntity) {
        insert(action)
        expire(System.currentTimeMillis())
        pruneResolved(System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L)
    }
}
