package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkspaceContextLedgerDao {
    @Query("SELECT * FROM workspace_context_ledger WHERE workspaceId = :workspaceId LIMIT 1")
    suspend fun get(workspaceId: String): WorkspaceContextLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: WorkspaceContextLedgerEntity)
}
