package com.mrredhood.devforge.core.ai.workflow

import android.content.Context
import android.net.Uri
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiVerificationEngine(context: Context) {
    private val tree = WorkspaceFileTree(context.applicationContext.contentResolver)

    suspend fun verify(
        workspaceId: String?,
        workspaceRoot: Uri?,
        changedPaths: List<String>,
        requestedBuild: Boolean = false,
        requestedTests: Boolean = false,
        requestedLint: Boolean = false,
    ): AiVerificationReceipt = withContext(Dispatchers.IO) {
        val checks = mutableListOf<AiVerificationCheck>()

        if (workspaceRoot == null) {
            checks += AiVerificationCheck(
                id = "workspace",
                title = "Workspace available",
                status = AiVerificationCheck.Status.SKIPPED,
                detail = "No active workspace was attached to this request.",
            )
        } else {
            val entries = runCatching { tree.list(workspaceRoot, 200) }.getOrElse { emptyList() }
            checks += AiVerificationCheck(
                id = "workspace",
                title = "Workspace accessible",
                status = if (entries.isNotEmpty() || workspaceId != null) {
                    AiVerificationCheck.Status.PASSED
                } else {
                    AiVerificationCheck.Status.FAILED
                },
                detail = entries.size.toString() + " visible items inspected near the workspace root.",
            )
            checks += AiVerificationCheck(
                id = "changes",
                title = "Expected change activity recorded",
                status = if (changedPaths.isNotEmpty()) {
                    AiVerificationCheck.Status.PASSED
                } else {
                    AiVerificationCheck.Status.SKIPPED
                },
                detail = if (changedPaths.isEmpty()) {
                    "No file mutations were reported by the AI workflow."
                } else {
                    changedPaths.distinct().take(12).size.toString() + " changed paths observed.",
                },
            )
        }

        checks += AiVerificationCheck(
            id = "build",
            title = "Build verification",
            status = AiVerificationCheck.Status.SKIPPED,
            detail = if (requestedBuild) {
                "A build was requested; Build Center remains the authoritative build receipt."
            } else {
                "No build was requested by this mission."
            },
        )
        checks += AiVerificationCheck(
            id = "tests",
            title = "Test verification",
            status = AiVerificationCheck.Status.SKIPPED,
            detail = if (requestedTests) {
                "Tests were requested; the test runner receipt is required for a pass."
            } else {
                "No tests were requested by this mission."
            },
        )
        checks += AiVerificationCheck(
            id = "lint",
            title = "Lint verification",
            status = AiVerificationCheck.Status.SKIPPED,
            detail = if (requestedLint) {
                "Lint was requested; the lint receipt is required for a pass."
            } else {
                "No lint was requested by this mission."
            },
        )

        AiVerificationReceipt(checks, System.currentTimeMillis())
    }
}
