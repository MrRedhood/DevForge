package com.mrredhood.devforge.core.storage

import com.mrredhood.devforge.core.settings.DevForgeSettingsRepository

class DevForgeDataRetentionService(
    private val database: DevForgeDatabase,
    private val settings: DevForgeSettingsRepository,
) {
    suspend fun prune(): RetentionResult {
        val current = System.currentTimeMillis()
        val config = settings.snapshot()
        val auditRemoved = DurableStateRepository(database).pruneAudit(config.auditRetentionDays)
        val chatCutoff = current - config.chatRetentionDays * DAY_MS
        val chatRemoved = database.chatMessageDao().deleteOlderThan(chatCutoff)
        return RetentionResult(auditRemoved, chatRemoved)
    }

    data class RetentionResult(val auditRemoved: Int, val chatRemoved: Int)

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
