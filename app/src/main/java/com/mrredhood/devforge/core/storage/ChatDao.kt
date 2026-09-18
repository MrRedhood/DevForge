package com.mrredhood.devforge.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions WHERE scopeId = :scopeId AND providerId = :providerId AND modelId = :modelId LIMIT 1")
    suspend fun find(scopeId: String, providerId: String, modelId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun get(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsert(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET updatedAtEpochMs = :updatedAt WHERE sessionId = :sessionId")
    suspend fun touch(sessionId: String, updatedAt: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun recentDescending(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND messageId NOT IN (SELECT messageId FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMs DESC LIMIT :keep)")
    suspend fun prune(sessionId: String, keep: Int)

    @Query("DELETE FROM chat_messages WHERE createdAtEpochMs < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMs ASC LIMIT :limit")
    fun observe(sessionId: String, limit: Int): Flow<List<ChatMessageEntity>>
}
