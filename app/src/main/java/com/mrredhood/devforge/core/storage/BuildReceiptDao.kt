package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildReceiptDao {
    @Query("SELECT * FROM build_receipts ORDER BY recordedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BuildReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(receipt: BuildReceiptEntity)

    @Query("DELETE FROM build_receipts WHERE runId NOT IN (SELECT runId FROM build_receipts ORDER BY recordedAtEpochMs DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Transaction
    suspend fun record(receipt: BuildReceiptEntity, keep: Int) {
        insertIfAbsent(receipt)
        prune(keep)
    }
}
