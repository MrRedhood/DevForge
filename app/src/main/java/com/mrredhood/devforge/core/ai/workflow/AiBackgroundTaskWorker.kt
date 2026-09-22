package com.mrredhood.devforge.core.ai.workflow

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrredhood.devforge.DevForgeApplication
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import kotlinx.coroutines.flow.first

class AiBackgroundTaskWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DevForgeApplication ?: return Result.failure()
        WorkspaceDatabaseRepository(applicationContext).workspaces.first().forEach { workspace ->
            runCatching { app.agentRuntime.recoverWorkspace(workspace.id) }
        }
        return Result.success()
    }
}
