package com.mrredhood.devforge

import android.app.Application
import com.mrredhood.devforge.core.automation.AutomationScheduler
import com.mrredhood.devforge.core.settings.DevForgeSettingsRepository
import com.mrredhood.devforge.core.storage.DevForgeDataRetentionService
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.notification.ApprovalNotificationManager
import com.mrredhood.devforge.core.notification.DevForgeActivityNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DevForgeApplication : Application() {
    val agentRuntime: com.mrredhood.devforge.core.agent.AgentRuntimeManager by lazy {
        com.mrredhood.devforge.core.agent.AgentRuntimeManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        AutomationScheduler.initialize(this)
        ApprovalNotificationManager.initialize(this)
        DevForgeActivityNotificationManager.initialize(this)
        ApprovalNotificationManager.repostPending()
        agentRuntime
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                DevForgeDataRetentionService(
                    DevForgeDatabase.get(this@DevForgeApplication),
                    DevForgeSettingsRepository(this@DevForgeApplication),
                ).prune()
            }
            runCatching {
                val database = DevForgeDatabase.get(this@DevForgeApplication)
                val grants = com.mrredhood.devforge.core.storage.CapabilityGrantRepository(database.capabilityGrantDao())
                grants.pruneExpired()
                grants.restoreActiveGrants()
            }
        }
    }
}
