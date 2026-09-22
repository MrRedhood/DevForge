package com.mrredhood.devforge.core.quality

import android.content.Context
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import com.mrredhood.devforge.core.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class WorkspaceCheckState { PASS, WARN, FAIL }

data class WorkspaceIntegrityCheck(
    val id: String,
    val label: String,
    val state: WorkspaceCheckState,
    val detail: String,
)

data class WorkspaceIntegrityReport(
    val workspaceId: String?,
    val workspaceName: String?,
    val checkedAtEpochMs: Long,
    val checks: List<WorkspaceIntegrityCheck>,
) {
    val failures: Int get() = checks.count { it.state == WorkspaceCheckState.FAIL }
    val warnings: Int get() = checks.count { it.state == WorkspaceCheckState.WARN }
    val passed: Int get() = checks.count { it.state == WorkspaceCheckState.PASS }
    val healthy: Boolean get() = failures == 0
}

class WorkspaceIntegrityService(context: Context) {
    private val appContext = context.applicationContext
    private val workspaces = WorkspaceDatabaseRepository(appContext)
    private val git = GitRepositoryService(appContext.contentResolver)

    suspend fun inspect(workspace: Workspace?): WorkspaceIntegrityReport = withContext(Dispatchers.IO) {
        if (workspace == null) {
            return@withContext WorkspaceIntegrityReport(
                workspaceId = null,
                workspaceName = null,
                checkedAtEpochMs = System.currentTimeMillis(),
                checks = listOf(
                    WorkspaceIntegrityCheck("workspace", "Workspace selection", WorkspaceCheckState.WARN, "No workspace is currently selected."),
                ),
            )
        }

        val checks = mutableListOf<WorkspaceIntegrityCheck>()
        val resolver = appContext.contentResolver
        val persistedPermission = resolver.persistedUriPermissions.any {
            it.uri == workspace.treeUri && it.isReadPermission
        }
        checks += WorkspaceIntegrityCheck(
            "permission",
            "Persisted workspace access",
            if (persistedPermission) WorkspaceCheckState.PASS else WorkspaceCheckState.FAIL,
            if (persistedPermission) "Persisted SAF read permission is available." else "The workspace URI is no longer backed by a persisted read permission.",
        )

        val treeReadable = runCatching {
            resolver.query(
                workspace.treeUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null,
            )?.use { cursor -> cursor.moveToFirst() } == true
        }.getOrDefault(false)
        checks += WorkspaceIntegrityCheck(
            "root",
            "Workspace root",
            if (treeReadable) WorkspaceCheckState.PASS else WorkspaceCheckState.FAIL,
            if (treeReadable) "The selected workspace root can be queried." else "The selected workspace root could not be queried.",
        )

        val knownWorkspace = runCatching {
            workspaces.workspaces.first().any { it.id == workspace.id }
        }.getOrDefault(false)
        checks += WorkspaceIntegrityCheck(
            "database",
            "Workspace metadata",
            if (knownWorkspace) WorkspaceCheckState.PASS else WorkspaceCheckState.FAIL,
            if (knownWorkspace) "The workspace is present in persisted workspace metadata." else "The workspace is missing from persisted workspace metadata.",
        )

        val gitState = runCatching { git.detect(workspace.treeUri) }.getOrElse {
            GitDetectionState.Unsupported(it.message ?: "Git detection failed.")
        }
        when (gitState) {
            GitDetectionState.Detecting -> checks += WorkspaceIntegrityCheck(
                "git",
                "Git repository",
                WorkspaceCheckState.WARN,
                "Git repository detection is still in progress.",
            )
            GitDetectionState.NotDetected -> checks += WorkspaceIntegrityCheck(
                "git",
                "Git repository",
                WorkspaceCheckState.WARN,
                "No Git repository was detected at the workspace root.",
            )
            is GitDetectionState.Detected -> checks += WorkspaceIntegrityCheck(
                "git",
                "Git repository",
                WorkspaceCheckState.PASS,
                "Git repository detected on " + (gitState.repository.branchName ?: "detached HEAD") + ".",
            )
            is GitDetectionState.Unsupported -> checks += WorkspaceIntegrityCheck(
                "git",
                "Git repository",
                WorkspaceCheckState.WARN,
                gitState.reason.take(220),
            )
        }

        WorkspaceIntegrityReport(
            workspaceId = workspace.id,
            workspaceName = workspace.name.take(160),
            checkedAtEpochMs = System.currentTimeMillis(),
            checks = checks,
        )
    }
}
