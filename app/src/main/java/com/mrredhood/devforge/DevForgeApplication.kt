package com.mrredhood.devforge

import android.app.Application
import com.mrredhood.devforge.core.automation.AutomationScheduler

class DevForgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AutomationScheduler.initialize(this)
    }
}
