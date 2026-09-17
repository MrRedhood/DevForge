package com.mrredhood.devforge.core.storage

import android.content.Context
import androidx.room.withTransaction
import com.mrredhood.devforge.core.workspace.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class WorkspaceDatabaseRepository(context: Context) {
    private val database = DevForgeDatabase.get(context)
    private val dao = database.workspaceDao()
    private val legacyPreferences = context.getSharedPreferences("devforge_workspace", Context.MODE_PRIVATE)

    val workspaces: Flow<List<Workspace>> = dao.observeAll().map { values -> values.map(WorkspaceEntity::toDomain) }
    val activeWorkspace: Flow<Workspace?> = dao.observeActive().map { it?.toDomain() }

    suspend fun ensureLegacyWorkspaceMigrated() {
        if (dao.observeAllOnceIsEmpty().not()) return
        val raw = legacyPreferences.getString("current_workspace", null) ?: return
        runCatching {
            val json = JSONObject(raw)
            val workspace = Workspace(
                id = json.getString("id"),
                name = json.getString("name"),
                treeUri = android.net.Uri.parse(json.getString("treeUri")),
                lastOpenedAt = json.optLong("lastOpenedAt", System.currentTimeMillis()),
            )
            database.withTransaction {
                dao.clearActive()
                dao.upsert(workspace.toEntity(isActive = true))
            }
        }
    }

    suspend fun saveAndActivate(workspace: Workspace) {
        database.withTransaction {
            dao.clearActive()
            dao.upsert(workspace.toEntity(isActive = true))
        }
        legacyPreferences.edit().remove("current_workspace").apply()
    }

    suspend fun activate(workspaceId: String) {
        database.withTransaction {
            dao.clearActive()
            dao.activate(workspaceId, System.currentTimeMillis())
        }
    }

    suspend fun delete(workspaceId: String) {
        dao.delete(workspaceId)
    }

    private suspend fun WorkspaceDao.observeAllOnceIsEmpty(): Boolean =
        findById("__probe__") == null && runCatching { kotlinx.coroutines.flow.first(observeAll()) }.getOrDefault(emptyList()).isEmpty()
}
