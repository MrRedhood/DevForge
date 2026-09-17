package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BuildReceiptDao {
    @Query("SELECT * FROM build_receipts ORDER BY recordedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<BuildReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(receipt: BuildReceiptEntity)

    @Query("DELETE FROM build_receipts WHERE runId NOT IN (SELECT runId FROM build_receipts ORDER BY recordedAtEpochMs DESC LIMIT :keep)")
    suspend fun prune(keep: Int)
}
