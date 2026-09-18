package com.mrredhood.devforge

import android.app.Application
import com.mrredhood.devforge.core.automation.AutomationScheduler
import com.mrredhood.devforge.core.settings.DevForgeSettingsRepository
import com.mrredhood.devforge.core.storage.DevForgeDataRetentionService
import com.mrredhood.devforge.core.storage.DevForgeDatabase
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
        agentRuntime
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                DevForgeDataRetentionService(
                    DevForgeDatabase.get(this@DevForgeApplication),
                    DevForgeSettingsRepository(this@DevForgeApplication),
                ).prune()
            }
        }
    }
}
