package com.mrredhood.devforge.core.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.mrredhood.devforge.core.automation.AutomationScheduler

class DevForgeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DevForgeSettingsRepository(application)

    var settings by mutableStateOf(repository.snapshot())
        private set

    fun setTheme(value: ThemeMode) { repository.setThemeMode(value); settings = repository.snapshot() }
    fun setDensity(value: DensityMode) { repository.setDensityMode(value); settings = repository.snapshot() }
    fun setEditorFont(value: EditorFontSize) { repository.setEditorFontSize(value); settings = repository.snapshot() }
    fun setWordWrap(value: Boolean) { repository.setWordWrap(value); settings = repository.snapshot() }
    fun setInvisibles(value: Boolean) { repository.setShowInvisibles(value); settings = repository.snapshot() }
    fun setAutoSaveEnabled(value: Boolean) { repository.setAutoSaveEnabled(value); settings = repository.snapshot() }
    fun setAutoSaveIntervalMs(value: String) { value.toLongOrNull()?.let { repository.setAutoSaveIntervalMs(it) }; settings = repository.snapshot() }
    fun setAiRouting(value: AiRoutingMode) { repository.setAiRoutingMode(value); settings = repository.snapshot() }

    fun saveGithub(owner: String, repositoryName: String, branch: String, workflow: String) {
        repository.setGithub(owner, repositoryName, branch, workflow)
        settings = repository.snapshot()
    }

    fun setBuildPoll(value: String) {
        value.toLongOrNull()?.let { repository.setBuildPollSeconds(it) }
        settings = repository.snapshot()
    }

    fun setTerminalTimeout(value: String) {
        value.toLongOrNull()?.let { repository.setTerminalTimeoutMs(it) }
        settings = repository.snapshot()
    }

    fun setAutomationInterval(value: String) {
        value.toLongOrNull()?.let { repository.setAutomationEventIntervalMinutes(it) }
        settings = repository.snapshot()
        AutomationScheduler.initialize(getApplication())
    }

    fun setAuditRetention(value: String) {
        value.toLongOrNull()?.let { repository.setAuditRetentionDays(it) }
        settings = repository.snapshot()
    }

    fun setChatRetention(value: String) {
        value.toLongOrNull()?.let { repository.setChatRetentionDays(it) }
        settings = repository.snapshot()
    }
}
