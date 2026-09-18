package com.mrredhood.devforge.core.storage

import com.mrredhood.devforge.core.security.SecretRedactor
import java.util.UUID

class AuditTrailRepository(private val dao: AuditEventDao) {
    suspend fun record(
        eventType: String,
        summary: String,
        workspaceId: String? = null,
        actionId: String? = null,
        capability: String? = null,
        risk: String? = null,
        metadataJson: String? = null,
    ) {
        dao.insert(
            AuditEventEntity(
                eventId = UUID.randomUUID().toString(),
                workspaceId = workspaceId,
                actionId = actionId,
                capability = capability,
                risk = risk,
                eventType = eventType,
                summary = SecretRedactor.redact(summary, 500),
                metadataJson = metadataJson?.let { SecretRedactor.redact(it, 32 * 1024) },
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        dao.prune(System.currentTimeMillis() - DEFAULT_RETENTION_DAYS * DAY_MS)
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
