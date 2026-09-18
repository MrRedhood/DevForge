package com.mrredhood.devforge.core.ai

import com.mrredhood.devforge.core.security.SecretRedactor
import com.mrredhood.devforge.core.storage.ChatMessageDao
import com.mrredhood.devforge.core.storage.ChatMessageEntity
import com.mrredhood.devforge.core.storage.ChatSessionDao
import com.mrredhood.devforge.core.storage.ChatSessionEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val sessions: ChatSessionDao,
    private val messages: ChatMessageDao,
) {
    suspend fun getOrCreateSession(
        scopeId: String,
        model: AIModelInfo,
    ): ChatSessionEntity {
        sessions.find(scopeId, model.provider.id, model.id)?.let { return it }
        val now = System.currentTimeMillis()
        val entity = ChatSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            scopeId = scopeId,
            providerId = model.provider.id,
            modelId = model.id,
            modelName = model.displayName,
            contextLimit = model.contextLimit,
            title = model.displayName,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        sessions.upsert(entity)
        return sessions.find(scopeId, model.provider.id, model.id) ?: entity
    }

    fun observeMessages(sessionId: String, limit: Int = MAX_MESSAGES): Flow<List<ChatMessageEntity>> =
        messages.observe(sessionId, limit)

    suspend fun addMessage(
        sessionId: String,
        role: String,
        content: String,
        commandName: String? = null,
    ) {
        require(content.length <= MAX_MESSAGE_CHARS) { "Chat message is too large." }
        val durableContent = SecretRedactor.redact(content, MAX_MESSAGE_CHARS)
        messages.insert(
            ChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = role,
                content = durableContent,
                commandName = commandName,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        messages.prune(sessionId, MAX_MESSAGES)
        sessions.touch(sessionId, System.currentTimeMillis())
    }

    companion object {
        const val MAX_MESSAGES = 200
        const val MAX_MESSAGE_CHARS = 256 * 1024
        const val GLOBAL_SCOPE = "global"
    }
}
