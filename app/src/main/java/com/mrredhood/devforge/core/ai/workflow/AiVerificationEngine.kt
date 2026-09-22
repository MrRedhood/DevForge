package com.mrredhood.devforge.core.ai.workflow

import android.content.Context
import android.net.Uri
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiVerificationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val tree = WorkspaceFileTree(appContext.contentResolver)
    private val database = DevForgeDatabase.get(appContext)

    suspend fun verify(
        workspaceId: String?,
        workspaceRoot: Uri?,
        changedPaths: List<String>,
        missionStartedAtEpochMs: Long = 0L,
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
            val entryNames = entries.map { it.name }.toSet()
            val looksLikeAndroid = entryNames.any {
                it == "AndroidManifest.xml" ||
                    it == "settings.gradle" ||
                    it == "settings.gradle.kts" ||
                    it == "build.gradle" ||
                    it == "build.gradle.kts" ||
                    it == "gradlew"
            }
            checks += AiVerificationCheck(
                id = "project",
                title = "Project type understood",
                status = if (looksLikeAndroid) AiVerificationCheck.Status.PASSED else AiVerificationCheck.Status.SKIPPED,
                detail = if (looksLikeAndroid) "Android/Gradle project markers detected at the workspace root." else "No Android/Gradle markers detected at the workspace root.",
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
                    changedPaths.distinct().take(12).size.toString() + " changed paths observed."
                },
            )
        }

        val latestBuild = runCatching { database.buildReceiptDao().latest() }.getOrNull()
        val buildReceiptIsFresh = latestBuild != null &&
            latestBuild.recordedAtEpochMs >= missionStartedAtEpochMs &&
            latestBuild.conclusion.equals("success", ignoreCase = true)
        val buildReceiptIsFailed = latestBuild != null &&
            latestBuild.recordedAtEpochMs >= missionStartedAtEpochMs &&
            !latestBuild.conclusion.isNullOrBlank() &&
            !latestBuild.conclusion.equals("success", ignoreCase = true)
        checks += AiVerificationCheck(
            id = "build",
            title = "Build verification",
            status = when {
                !requestedBuild -> AiVerificationCheck.Status.SKIPPED
                buildReceiptIsFresh -> AiVerificationCheck.Status.PASSED
                buildReceiptIsFailed -> AiVerificationCheck.Status.FAILED
                else -> AiVerificationCheck.Status.SKIPPED
            },
            detail = when {
                !requestedBuild -> "No build was requested by this mission."
                buildReceiptIsFresh -> "Build Center recorded a successful build after this mission started."
                buildReceiptIsFailed -> "Build Center recorded a non-successful build after this mission started."
                else -> "A build was requested; no fresh authoritative Build Center receipt is available yet."
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
