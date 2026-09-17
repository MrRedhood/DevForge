package com.mrredhood.devforge.core.automation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.agent.AgentTaskPlanCodec
import com.mrredhood.devforge.core.agent.AgentTaskStatus
import com.mrredhood.devforge.core.storage.AutomationEntity
import com.mrredhood.devforge.core.storage.AutomationRunEntity
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.WorkspaceDatabaseRepository
import com.mrredhood.devforge.core.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AutomationViewModel(application: Application) : AndroidViewModel(application) {
    private val durable = DurableStateRepository(DevForgeDatabase.get(application))
    private val workspaces = WorkspaceDatabaseRepository(application)

    var automations by mutableStateOf<List<AutomationEntity>>(emptyList())
        private set
    var activeWorkspace by mutableStateOf<Workspace?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var selectedRuns by mutableStateOf<List<AutomationRunEntity>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            durable.observeAutomations(DurableStateRepository.MAX_AUTOMATIONS).collectLatest { automations = it }
        }
        viewModelScope.launch {
            workspaces.ensureLegacyWorkspaceMigrated()
            workspaces.activeWorkspace.collectLatest { activeWorkspace = it }
        }
    }

    fun save(draft: AutomationDraft) {
        if (isSaving) return
        viewModelScope.launch(Dispatchers.IO) {
            isSaving = true
            runCatching {
                validate(draft)
                val id = draft.automationId ?: UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val existing = durable.getAutomation(id)
                val entity = AutomationEntity(
                    automationId = id,
                    workspaceId = draft.workspaceId?.trim()?.takeIf(String::isNotBlank),
                    name = draft.name.trim().take(200),
                    status = if (draft.enabled) AutomationStatus.ENABLED.name else AutomationStatus.DISABLED.name,
                    triggerType = draft.triggerType.name,
                    schedule = draft.triggerConfig.trim().takeIf(String::isNotBlank),
                    actionGraph = draft.actionGraph,
                    createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                )
                durable.saveAutomation(entity)
                if (existing != null && (existing.triggerType != entity.triggerType || existing.schedule != entity.schedule || existing.workspaceId != entity.workspaceId)) {
                    durable.resetAutomationTriggerState(id)
                }
                if (draft.enabled) AutomationScheduler.scheduleNext(getApplication(), entity)
                else AutomationScheduler.cancel(getApplication(), id)
                withContext(Dispatchers.Main.immediate) { message = "Automation '${entity.name}' saved." }
            }.onFailure { error ->
                withContext(Dispatchers.Main.immediate) { message = error.message ?: "Unable to save automation." }
            }
            isSaving = false
        }
    }

    fun setEnabled(automation: AutomationEntity, enabled: Boolean) {
        save(
            AutomationDraft(
                automationId = automation.automationId,
                name = automation.name,
                workspaceId = automation.workspaceId,
                triggerType = runCatching { AutomationTriggerType.valueOf(automation.triggerType) }.getOrDefault(AutomationTriggerType.MANUAL),
                triggerConfig = automation.schedule.orEmpty(),
                actionGraph = automation.actionGraph,
                enabled = enabled,
            ),
        )
    }

    fun runNow(automation: AutomationEntity) {
        if (automation.status != AutomationStatus.ENABLED.name) {
            message = "Enable the automation before running it."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val started = AutomationScheduler.triggerNow(getApplication(), automation.automationId)
            withContext(Dispatchers.Main.immediate) {
                message = if (started) "Automation '${automation.name}' queued." else "Unable to queue the automation."
            }
        }
    }

    fun delete(automation: AutomationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            AutomationScheduler.cancel(getApplication(), automation.automationId)
            durable.deleteAutomation(automation.automationId)
            withContext(Dispatchers.Main.immediate) { message = "Automation '${automation.name}' deleted." }
        }
    }

    fun loadRuns(automationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val runs = durable.recentAutomationRuns(automationId, 12)
            withContext(Dispatchers.Main.immediate) { selectedRuns = runs }
        }
    }

    fun clearMessage() { message = null }

    private fun validate(draft: AutomationDraft) {
        require(draft.name.trim().isNotBlank()) { "Automation name cannot be empty." }
        require(draft.name.trim().length <= 200) { "Automation name is too long." }
        require(draft.triggerConfig.toByteArray(Charsets.UTF_8).size <= DurableStateRepository.MAX_SCHEDULE_LENGTH) { "Trigger configuration is too large." }
        require(draft.actionGraph.toByteArray(Charsets.UTF_8).size <= DurableStateRepository.MAX_AUTOMATION_GRAPH_BYTES) { "Action graph is too large." }
        val plan = AgentTaskPlanPlanValidator.decode(draft.actionGraph)
        require(plan.steps.isNotEmpty()) { "Add at least one automation action." }

        when (draft.triggerType) {
            AutomationTriggerType.SCHEDULE -> require(AutomationScheduleParser.parse(draft.triggerConfig) != null) { "Enter a valid schedule." }
            AutomationTriggerType.MANUAL -> Unit
            AutomationTriggerType.REPOSITORY_CHANGE -> {
                require(!draft.workspaceId.isNullOrBlank()) { "Repository-change automation needs a workspace." }
                AutomationTriggerCodec.decode(draft.triggerType, draft.triggerConfig)
            }
            AutomationTriggerType.BUILD_COMPLETION -> {
                val config = AutomationTriggerCodec.decode(draft.triggerType, draft.triggerConfig) as AutomationTriggerConfig.BuildCompletion
                require(config.owner.isNotBlank() && config.repository.isNotBlank()) { "Build trigger needs GitHub owner and repository." }
            }
            AutomationTriggerType.CONDITION -> AutomationTriggerCodec.decode(draft.triggerType, draft.triggerConfig)
        }
    }

    private object AgentTaskPlanPlanValidator {
        fun decode(value: String) = AgentTaskPlanCodec.decode(value)
    }
}

data class AutomationDraft(
    val automationId: String?,
    val name: String,
    val workspaceId: String?,
    val triggerType: AutomationTriggerType,
    val triggerConfig: String,
    val actionGraph: String,
    val enabled: Boolean,
)
