package com.mrredhood.devforge.core.agent

import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.AgentFileLeaseEntity
import com.mrredhood.devforge.core.storage.AgentHandoffEntity
import com.mrredhood.devforge.core.storage.AgentSharedMemoryEntity
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction

enum class AgentHandoffStatus { PENDING, CLAIMED, COMPLETED }

data class AgentHandoffDraft(
    val workspaceId: String,
    val fromTaskId: String,
    val toTaskId: String? = null,
    val title: String,
    val summary: String,
    val contextJson: String = "{}",
)

/** Durable, bounded coordination plane shared by all agents in a workspace. */
class AgentCoordinationService(
    private val db: DevForgeDatabase,
) {
    private val memory = db.agentSharedMemoryDao()
    private val handoffs = db.agentHandoffDao()
    private val leases = db.agentFileLeaseDao()

    fun observeMemory(workspaceId: String, limit: Int = MAX_MEMORY_ENTRIES): Flow<List<AgentSharedMemoryEntity>> =
        memory.observe(workspaceId, limit.coerceIn(1, MAX_MEMORY_ENTRIES))

    suspend fun listMemory(workspaceId: String, limit: Int = MAX_MEMORY_ENTRIES): List<AgentSharedMemoryEntity> =
        memory.list(workspaceId, limit.coerceIn(1, MAX_MEMORY_ENTRIES))

    suspend fun getMemory(workspaceId: String, key: String): AgentSharedMemoryEntity? =
        memory.get(workspaceId, normalizeKey(key))

    suspend fun putMemory(workspaceId: String, key: String, content: String, sourceTaskId: String?): AgentSharedMemoryEntity {
        val normalizedKey = normalizeKey(key)
        val normalizedContent = normalizeContent(content)
        val existing = memory.get(workspaceId, normalizedKey)
        val entity = AgentSharedMemoryEntity(
            memoryId = existing?.memoryId ?: UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            key = normalizedKey,
            content = normalizedContent,
            sourceTaskId = sourceTaskId?.take(MAX_TASK_ID_LENGTH),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        memory.upsert(entity)
        memory.prune(workspaceId, MAX_MEMORY_ENTRIES)
        return entity
    }

    suspend fun deleteMemory(workspaceId: String, key: String): Boolean =
        runCatching { memory.delete(workspaceId, normalizeKey(key)); true }.getOrDefault(false)

    fun observeHandoffs(workspaceId: String, limit: Int = MAX_HANDOFFS): Flow<List<AgentHandoffEntity>> =
        handoffs.observe(workspaceId, limit.coerceIn(1, MAX_HANDOFFS))

    suspend fun availableHandoffs(workspaceId: String, taskId: String, limit: Int = MAX_HANDOFFS): List<AgentHandoffEntity> =
        handoffs.available(workspaceId, taskId.take(MAX_TASK_ID_LENGTH), limit.coerceIn(1, MAX_HANDOFFS))

    suspend fun listHandoffs(workspaceId: String, limit: Int = MAX_HANDOFFS): List<AgentHandoffEntity> =
        handoffs.observe(workspaceId, limit.coerceIn(1, MAX_HANDOFFS)).first()

    suspend fun createHandoff(draft: AgentHandoffDraft): AgentHandoffEntity {
        require(draft.workspaceId.isNotBlank()) { "Handoff workspace is required." }
        require(draft.fromTaskId.isNotBlank()) { "Handoff source task is required." }
        require(draft.toTaskId == null || draft.toTaskId.isNotBlank()) { "Handoff target task is invalid." }
        val title = draft.title.trim().take(MAX_HANDOFF_TITLE)
        val summary = draft.summary.trim().take(MAX_HANDOFF_SUMMARY)
        require(title.isNotBlank()) { "Handoff title is required." }
        require(summary.isNotBlank()) { "Handoff summary is required." }
        val context = draft.contextJson.take(MAX_HANDOFF_CONTEXT_BYTES)
        require(!looksLikeSecret(summary) && !looksLikeSecret(context)) { "Potential secret material cannot be stored in agent handoffs." }
        val now = System.currentTimeMillis()
        val handoff = AgentHandoffEntity(
            handoffId = UUID.randomUUID().toString(),
            workspaceId = draft.workspaceId.take(MAX_WORKSPACE_ID_LENGTH),
            fromTaskId = draft.fromTaskId.take(MAX_TASK_ID_LENGTH),
            toTaskId = draft.toTaskId?.take(MAX_TASK_ID_LENGTH),
            title = title,
            summary = summary,
            contextJson = context,
            status = AgentHandoffStatus.PENDING.name,
            createdAtEpochMs = now,
            claimedAtEpochMs = null,
            claimedByTaskId = null,
            completedAtEpochMs = null,
        )
        handoffs.insert(handoff)
        return handoff
    }

    suspend fun claimHandoff(handoffId: String, taskId: String): Boolean =
        handoffs.claim(handoffId.take(MAX_HANDOFF_ID_LENGTH), taskId.take(MAX_TASK_ID_LENGTH), System.currentTimeMillis()) > 0

    suspend fun completeHandoff(handoffId: String, taskId: String): Boolean =
        handoffs.complete(handoffId.take(MAX_HANDOFF_ID_LENGTH), taskId.take(MAX_TASK_ID_LENGTH), System.currentTimeMillis()) > 0

    suspend fun pruneCompletedHandoffs(workspaceId: String, retentionMs: Long = HANDOFF_RETENTION_MS): Int =
        handoffs.pruneCompleted(workspaceId, System.currentTimeMillis() - retentionMs)

    suspend fun acquireFileLease(workspaceId: String, taskId: String, path: String, ttlMs: Long = FILE_LEASE_TTL_MS): Boolean {
        val normalized = WorkspacePathScope.normalize(path)
        val now = System.currentTimeMillis()
        val lease = AgentFileLeaseEntity(
            leaseKey = leaseKey(workspaceId, normalized),
            workspaceId = workspaceId,
            path = normalized,
            taskId = taskId.take(MAX_TASK_ID_LENGTH),
            acquiredAtEpochMs = now,
            expiresAtEpochMs = now + ttlMs.coerceIn(1_000L, FILE_LEASE_TTL_MS),
        )
        return db.withTransaction {
            leases.pruneExpired(now)
            leases.acquire(lease) != -1L
        }
    }

    suspend fun releaseFileLease(workspaceId: String, taskId: String, path: String): Boolean {
        val normalized = WorkspacePathScope.normalize(path)
        return leases.release(workspaceId, taskId, normalized) > 0
    }

    suspend fun releaseTaskFileLeases(workspaceId: String, taskId: String): Int =
        leases.releaseAll(workspaceId, taskId)

    suspend fun listFileLeases(workspaceId: String, limit: Int = MAX_LEASES): List<AgentFileLeaseEntity> =
        leases.list(workspaceId, limit.coerceIn(1, MAX_LEASES))

    private fun normalizeKey(value: String): String {
        val key = value.trim()
        require(key.isNotBlank() && key.length <= MAX_MEMORY_KEY) { "Memory key is missing or too long." }
        require(!looksLikeSecret(key)) { "Potential secret material cannot be used as a memory key." }
        return key
    }

    private fun normalizeContent(value: String): String {
        val content = value.trim().take(MAX_MEMORY_CONTENT)
        require(content.isNotBlank()) { "Shared memory content is required." }
        require(!looksLikeSecret(content)) { "Potential secret material cannot be stored in shared memory." }
        return content
    }

    private fun looksLikeSecret(value: String): Boolean {
        val lower = value.lowercase()
        return value.contains("-----begin ", true) ||
            lower.contains("sk-") ||
            lower.contains("AIza".lowercase()) ||
            lower.contains("github_pat_") ||
            lower.contains("ghp_") ||
            lower.contains("api_key=") ||
            lower.contains("access_token=")
    }

    private fun leaseKey(workspaceId: String, path: String): String = workspaceId + "|" + path

    companion object {
        const val MAX_MEMORY_ENTRIES = 100
        const val MAX_MEMORY_KEY = 160
        const val MAX_MEMORY_CONTENT = 8 * 1024
        const val MAX_HANDOFFS = 50
        const val MAX_HANDOFF_TITLE = 160
        const val MAX_HANDOFF_SUMMARY = 2_000
        const val MAX_HANDOFF_CONTEXT_BYTES = 16 * 1024
        const val MAX_HANDOFF_ID_LENGTH = 100
        const val MAX_TASK_ID_LENGTH = 100
        const val MAX_WORKSPACE_ID_LENGTH = 160
        const val MAX_LEASES = 100
        const val FILE_LEASE_TTL_MS = 2L * 60L * 1000L
        const val HANDOFF_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
