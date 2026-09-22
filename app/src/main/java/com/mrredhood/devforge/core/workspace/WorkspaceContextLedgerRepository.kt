package com.mrredhood.devforge.core.workspace

import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.WorkspaceContextLedgerEntity
import org.json.JSONArray

class WorkspaceContextLedgerRepository(
    database: DevForgeDatabase,
) {
    private val dao = database.workspaceContextLedgerDao()

    suspend fun get(workspaceId: String): WorkspaceContextLedgerEntity? = dao.get(workspaceId)

    suspend fun recordAccess(
        workspaceId: String,
        contextVersion: Long,
        paths: List<String>,
        snapshot: String? = null,
    ) {
        if (workspaceId.isBlank()) return
        val existing = dao.get(workspaceId)
        val merged = buildList {
            (paths + parsePaths(existing?.recentPathsJson)).forEach { raw ->
                val value = raw.trim().trim('/')
                if (value.isNotBlank() && !contains(value)) add(value)
            }
        }.take(MAX_RECENT_PATHS)
        dao.upsert(
            WorkspaceContextLedgerEntity(
                workspaceId = workspaceId,
                contextVersion = contextVersion,
                recentPathsJson = JSONArray(merged).toString(),
                lastSnapshot = snapshot?.take(MAX_SNAPSHOT_CHARS) ?: existing?.lastSnapshot,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun parsePaths(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    companion object {
        private const val MAX_RECENT_PATHS = 12
        private const val MAX_SNAPSHOT_CHARS = 6_000
    }
}
