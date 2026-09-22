package com.mrredhood.devforge.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mrredhood.devforge.DevForgeApplication
import com.mrredhood.devforge.MainActivity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApprovalNotificationManager {
    const val ACTION_APPROVE = "com.mrredhood.devforge.APPROVE_APPROVAL"
    const val ACTION_DISMISS = "com.mrredhood.devforge.DISMISS_APPROVAL"
    const val EXTRA_APPROVAL_ID = "approval_id"
    const val CHANNEL_ID = "devforge_approvals"

    private const val NOTIFICATION_ID_BASE = 48000
    private const val WINDOW_MS = 15_000L

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        // Approval requests are intentionally in-app only. Keep this compatibility
        // surface for older notification receiver/work-manager registrations.
        appContext = context.applicationContext
    }

    fun notifyPending(action: com.mrredhood.devforge.core.storage.ApprovalEntity) {
        // Deliberately no-op: approvals must appear in the in-app bottom-right card.
    }

    fun repostPending() {
        // Deliberately no-op: approvals must never be reposted to the Android notification shade.
    }

    internal suspend fun updateProgress(approvalId: String) {
        // Deliberately no-op: the in-app card owns the 15-second countdown.
    }

    fun cancel(approvalId: String) {
        val context = appContext ?: return
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(approvalId))
        WorkManager.getInstance(context).cancelUniqueWork(workName(approvalId))
    }

    fun scheduleProgress(approvalId: String) {
        // Deliberately no-op: WorkManager is not used for approval UI.
    }

    private fun notificationId(approvalId: String): Int =
        NOTIFICATION_ID_BASE + (approvalId.hashCode() and 0x1FFF)

    private fun workName(approvalId: String): String =
        "approval-notification:$approvalId"
}

class ApprovalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Approval execution is in-app only. The legacy notification receiver is inert.
    }
}

class ApprovalNotificationProgressWorker(
    context: Context,
    params: WorkerParameters,
) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val approvalId = inputData.getString(ApprovalNotificationManager.EXTRA_APPROVAL_ID) ?: return Result.failure()
        return runCatching {
            ApprovalNotificationManager.updateProgress(approvalId)
            Result.success()
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Result.retry()
        }
    }
}


object DevForgeActivityNotificationManager {
    const val CHANNEL_ID = "devforge_activity"
    private const val BASE_ID = 56000

    @Volatile private var context: Context? = null

    fun initialize(context: Context) {
        val application = context.applicationContext
        this.context = application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "DevForge activity",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Build, agent, automation, Git and recovery activity."
                    setShowBadge(true)
                },
            )
        }
    }

    fun notify(title: String, text: String) {
        val app = context ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            app,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        app.getSystemService(NotificationManager::class.java).notify(
            BASE_ID + (title.hashCode() and 0x0FFF),
            Notification.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title.take(180))
                .setContentText(text.take(500))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }
}
