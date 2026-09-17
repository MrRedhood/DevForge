package com.mrredhood.devforge.core.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["scopeId", "providerId", "modelId"], unique = true)],
)
data class ChatSessionEntity(
    @PrimaryKey val sessionId: String,
    val scopeId: String,
    val providerId: String,
    val modelId: String,
    val modelName: String,
    val contextLimit: Long?,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId", "createdAtEpochMs"])],
)
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val commandName: String?,
    val createdAtEpochMs: Long,
)
