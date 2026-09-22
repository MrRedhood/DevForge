package com.mrredhood.devforge.core.power

import android.content.Context
import com.mrredhood.devforge.core.ai.workflow.AiWorkflowSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PowerFeature(val title: String, val description: String) {
    TASKS("Project Tasks", "Repeatable engineering tasks that can run through the DevForge terminal."),
    TEST_EXPLORER("Test Explorer", "One place for test, lint, build and failure-fix actions."),
    DEBUGGER("Debugger Surface", "Structured debug state, frames, watches and explain-state inputs."),
    UNDO_TREE("Branching Undo", "Checkpoint editor states and return to an alternate edit path."),
    REGISTERS("Registers", "Multiple clipboard/register buffers for mobile editing workflows."),
    NAVIGATION("Semantic Navigation", "Back/forward, definition, references, previous edits and AI change navigation."),
    STRUCTURAL_EDITING("Structural Editing", "Expression/function/class selection and safe structural transforms."),
    AI_REFACTOR("AI Refactoring", "Structured refactor missions instead of blind text replacement."),
    CODE_REVIEW("Pre-commit Review", "Review changes against project rules before committing."),
    CRITIC("Reviewer Agents", "Independent review roles for correctness, tests, security and performance."),
    PROOF("Proof Package", "Changes, tests, lint, build receipts and logs grouped into evidence."),
    UI_JOURNEYS("UI Journeys", "Declarative Android interaction journeys for repeatable UI validation."),
    PREVIEW_LAB("Compose Preview Lab", "Preview requests for device classes, references and accessibility checks."),
    DEVICE_MATRIX("Device Matrix", "Phone, tablet, foldable and multi-window validation targets."),
    REMOTE_DEV("Remote Development", "Remote workspace definitions for controller/worker workflows."),
    PROFILES("Development Profiles", "Switchable settings, task and integration bundles."),
    EXTENSIONS("DevForge Extensions", "Native extension contracts for future plugins without adopting another IDE API."),
    MCP("MCP Gateway", "Approval-aware external tool server registry."),
    MEMORY("AI Memory", "Evidence-backed global, workspace and mission memory."),
    AUTOMATIONS("Engineering Automations", "Reusable CI, issue, dependency and failure-monitoring blueprints."),
    LEARNED_RULES("Learned Rules", "Repository rule proposals that require explicit acceptance."),
    ROUTING("Resource-aware Routing", "Choose provider/model class from task complexity and device constraints."),
    MISSION_GRAPH("Mission Graph", "A single graph from plan through inspect, execute, verify, review and release.")
}

data class EngineeringTask(
    val id: String,
    val name: String,
    val command: String,
    val description: String,
    val enabled: Boolean = true,
)

class EngineeringTaskStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_engineering_tasks", Context.MODE_PRIVATE)

    fun list(): List<EngineeringTask> {
        val raw = prefs.getString(KEY, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultTasks()
            save(defaults)
            return defaults
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        EngineeringTask(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name"),
                            command = item.optString("command"),
                            description = item.optString("description"),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.getOrElse { defaultTasks() }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        save(list().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    private fun save(tasks: List<EngineeringTask>) {
        val array = JSONArray()
        tasks.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("command", it.command)
                    .put("description", it.description)
                    .put("enabled", it.enabled),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun defaultTasks(): List<EngineeringTask> = listOf(
        EngineeringTask("verify", "Verify project", "./gradlew test lint"),
        EngineeringTask("debug-build", "Debug APK", "./gradlew assembleDebug"),
        EngineeringTask("release-build", "Release APK", "./gradlew assembleRelease"),
        EngineeringTask("unit", "Unit tests", "./gradlew test"),
        EngineeringTask("lint", "Lint", "./gradlew lint"),
        EngineeringTask("clean", "Clean build", "./gradlew clean"),
        EngineeringTask("status", "Git status", "git status --short"),
        EngineeringTask("changed", "Changed files", "git diff --stat"),
    )

    private companion object { const val KEY = "tasks" }
}

data class DevelopmentProfile(
    val id: String,
    val name: String,
    val description: String,
)

class DevelopmentProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_profiles", Context.MODE_PRIVATE)

    fun list(): List<DevelopmentProfile> = listOf(
        DevelopmentProfile("mobile-safe", "Mobile Safe", "Smaller context, bounded tools and conservative terminal limits."),
        DevelopmentProfile("android", "Android Engineering", "Build, test, lint, Compose and Android-focused workflows."),
        DevelopmentProfile("release", "Release", "Release APK/AAB, signing, artifacts and verification."),
        DevelopmentProfile("review", "Review", "Read-first changes, tests, lint, security and proof package."),
    )

    fun current(): DevelopmentProfile {
        val id = prefs.getString("active", "mobile-safe")
        return list().firstOrNull { it.id == id } ?: list().first()
    }

    fun select(id: String) {
        if (list().any { it.id == id }) prefs.edit().putString("active", id).apply()
    }
}

data class AiMemoryEntry(
    val scope: String,
    val text: String,
    val evidence: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

class AiMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_ai_memory", Context.MODE_PRIVATE)

    fun entries(scope: String): List<AiMemoryEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("scope") != scope) continue
                add(
                    AiMemoryEntry(
                        scope = scope,
                        text = item.optString("text"),
                        evidence = item.optString("evidence"),
                        updatedAtEpochMs = item.optLong("updatedAt", 0L),
                    ),
                )
            }
        }.takeLast(40)
    }.getOrDefault(emptyList())

    fun remember(entry: AiMemoryEntry) {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        array.put(
            JSONObject()
                .put("scope", entry.scope)
                .put("text", entry.text.take(600))
                .put("evidence", entry.evidence.take(600))
                .put("updatedAt", entry.updatedAtEpochMs),
        )
        while (array.length() > 120) {
            val trimmed = JSONArray()
            for (index in 1 until array.length()) trimmed.put(array.get(index))
            array.clearAndCopyFrom(trimmed)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object { const val KEY = "entries" }
}

data class LearnedRuleProposal(
    val rule: String,
    val evidence: String,
    val accepted: Boolean = false,
)

data class McpServerDefinition(
    val id: String,
    val name: String,
    val endpoint: String,
    val enabled: Boolean = false,
    val approvalRequired: Boolean = true,
)

data class UiJourneyStep(
    val action: String,
    val target: String,
    val value: String = "",
)

data class UiJourney(
    val name: String,
    val steps: List<UiJourneyStep>,
    val assertions: List<String> = emptyList(),
)

data class ComposePreviewRequest(
    val device: String,
    val referenceDescription: String = "",
    val accessibilityPass: Boolean = true,
)

data class DeviceTarget(
    val id: String,
    val label: String,
    val notes: String,
)

data class RemoteDevEndpoint(
    val id: String,
    val name: String,
    val endpoint: String,
    val transport: String,
    val enabled: Boolean = false,
)

data class DevForgeExtension(
    val id: String,
    val name: String,
    val version: String,
    val capabilities: Set<String>,
)

data class EngineeringMissionNode(
    val id: String,
    val label: String,
    val state: State,
    val detail: String = "",
) {
    enum class State { WAITING, ACTIVE, PASSED, FAILED, SKIPPED }
}

object EngineeringMissionGraph {
    fun from(workflow: AiWorkflowSnapshot?): List<EngineeringMissionNode> {
        val phase = workflow?.phase
        val status = workflow?.status
        fun state(target: String): EngineeringMissionNode.State = when {
            workflow == null -> EngineeringMissionNode.State.WAITING
            phase?.name == target && status == AiWorkflowSnapshot.Status.RUNNING -> EngineeringMissionNode.State.ACTIVE
            target == "VERIFY" && ((workflow.verification?.failedCount ?: 0) > 0) -> EngineeringMissionNode.State.FAILED
            phase?.ordinal ?: -1 > when (target) {
                "UNDERSTAND" -> 0
                "PLAN" -> 1
                "INSPECT" -> 2
                "EXECUTE" -> 3
                "VERIFY" -> 4
                else -> 5
            } -> EngineeringMissionNode.State.PASSED
            else -> EngineeringMissionNode.State.WAITING
        }
        return listOf(
            "UNDERSTAND" to "Understand",
            "PLAN" to "Plan",
            "INSPECT" to "Inspect",
            "EXECUTE" to "Execute",
            "VERIFY" to "Verify",
            "REVIEW" to "Review",
        ).map { (id, label) -> EngineeringMissionNode(id, label, state(id)) }
    }
}

data class AiProofPackage(
    val workflowId: String?,
    val summary: String,
    val changedPaths: List<String>,
    val passedChecks: List<String>,
    val failedChecks: List<String>,
    val recordedAtEpochMs: Long,
) {
    companion object {
        fun from(workflow: AiWorkflowSnapshot?): AiProofPackage? {
            workflow ?: return null
            return AiProofPackage(
                workflowId = workflow.workflowId,
                summary = workflow.summary ?: workflow.changeSummary,
                changedPaths = workflow.changedPaths,
                passedChecks = workflow.verification?.checks
                    ?.filter { it.status == com.mrredhood.devforge.core.ai.workflow.AiVerificationCheck.Status.PASSED }
                    ?.map { it.title }
                    .orEmpty(),
                failedChecks = workflow.verification?.checks
                    ?.filter { it.status == com.mrredhood.devforge.core.ai.workflow.AiVerificationCheck.Status.FAILED }
                    ?.map { it.title }
                    .orEmpty(),
                recordedAtEpochMs = workflow.updatedAtEpochMs,
            )
        }
    }
}

private fun JSONArray.clearAndCopyFrom(source: JSONArray) {
    while (length() > 0) {
        remove(length() - 1)
    }
    for (index in 0 until source.length()) put(source.get(index))
}
