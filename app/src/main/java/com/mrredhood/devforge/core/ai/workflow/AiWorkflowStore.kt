package com.mrredhood.devforge.core.ai.workflow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AiWorkflowStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "devforge_ai_workflows",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun save(snapshot: AiWorkflowSnapshot) {
        val json = encode(snapshot)
        prefs.edit()
            .putString(KEY_ACTIVE, json)
            .putString(KEY_PREFIX + snapshot.workflowId, json)
            .apply()
    }

    fun active(): AiWorkflowSnapshot? =
        prefs.getString(KEY_ACTIVE, null)?.let(::decode)

    fun get(workflowId: String): AiWorkflowSnapshot? =
        prefs.getString(KEY_PREFIX + workflowId, null)?.let(::decode)

    @Synchronized
    fun clearActive() {
        prefs.edit().remove(KEY_ACTIVE).apply()
    }

    @Synchronized
    fun prune(maxWorkflows: Int = 20) {
        val keys = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.sorted()
        if (keys.size <= maxWorkflows) return
        prefs.edit().apply {
            keys.take(keys.size - maxWorkflows).forEach { remove(it) }
        }.apply()
    }

    private fun encode(snapshot: AiWorkflowSnapshot): String = JSONObject()
        .put("workflowId", snapshot.workflowId)
        .put("workspaceId", snapshot.workspaceId)
        .put("workspaceName", snapshot.workspaceName)
        .put("request", snapshot.request.take(12_000))
        .put("phase", snapshot.phase.name)
        .put("status", snapshot.status.name)
        .put("currentStep", snapshot.currentStep)
        .put(
            "generatedPlan",
            JSONArray().apply {
                snapshot.generatedPlan.take(MAX_PLAN_STEPS).forEach { step ->
                    put(
                        JSONObject()
                            .put("id", step.id)
                            .put("title", step.title.take(180))
                            .put("detail", step.detail.take(500))
                            .put("status", step.status.name),
                    )
                }
            },
        )
        .put(
            "activities",
            JSONArray().apply {
                snapshot.activities.takeLast(MAX_ACTIVITIES).forEach { activity ->
                    put(
                        JSONObject()
                            .put("id", activity.id)
                            .put("kind", activity.kind.name)
                            .put("status", activity.status.name)
                            .put("title", activity.title.take(300))
                            .put("detail", activity.detail.take(2_000))
                            .put("paths", JSONArray(activity.paths.take(MAX_PATHS)))
                            .put("createdAtEpochMs", activity.createdAtEpochMs),
                    )
                }
            },
        )
        .put(
            "verification",
            snapshot.verification?.let { receipt ->
                JSONObject()
                    .put(
                        "checks",
                        JSONArray().apply {
                            receipt.checks.take(MAX_CHECKS).forEach { check ->
                                put(
                                    JSONObject()
                                        .put("id", check.id)
                                        .put("title", check.title)
                                        .put("status", check.status.name)
                                        .put("detail", check.detail.take(1_000)),
                                )
                            }
                        },
                    )
                    .put("verifiedAtEpochMs", receipt.verifiedAtEpochMs)
            },
        )
        .put("summary", snapshot.summary?.take(6_000))
        .put("startedAtEpochMs", snapshot.startedAtEpochMs)
        .put("updatedAtEpochMs", snapshot.updatedAtEpochMs)
        .toString()

    private fun decode(raw: String): AiWorkflowSnapshot? = runCatching {
        val json = JSONObject(raw)
        val planJson = json.optJSONArray("generatedPlan") ?: JSONArray()
        val generatedPlan = buildList {
            for (i in 0 until minOf(planJson.length(), MAX_PLAN_STEPS)) {
                val item = planJson.optJSONObject(i) ?: continue
                add(
                    AiPlanStep(
                        id = item.optString("id"),
                        title = item.optString("title").take(180),
                        detail = item.optString("detail").take(500),
                        status = runCatching { AiPlanStepStatus.valueOf(item.optString("status")) }
                            .getOrDefault(AiPlanStepStatus.PENDING),
                    ),
                )
            }
        }
        val activitiesJson = json.optJSONArray("activities") ?: JSONArray()
        val activities = buildList {
            for (i in 0 until minOf(activitiesJson.length(), MAX_ACTIVITIES)) {
                val item = activitiesJson.optJSONObject(i) ?: continue
                add(
                    AiActivity(
                        id = item.optString("id"),
                        kind = runCatching { AiActivityKind.valueOf(item.optString("kind")) }
                            .getOrDefault(AiActivityKind.INFO),
                        status = runCatching { AiActivityStatus.valueOf(item.optString("status")) }
                            .getOrDefault(AiActivityStatus.COMPLETED),
                        title = item.optString("title"),
                        detail = item.optString("detail"),
                        paths = run {
                            val array = item.optJSONArray("paths") ?: JSONArray()
                            buildList {
                                for (index in 0 until minOf(array.length(), MAX_PATHS)) {
                                    add(array.optString(index))
                                }
                            }
                        },
                        createdAtEpochMs = item.optLong("createdAtEpochMs"),
                    ),
                )
            }
        }
        val verificationJson = json.optJSONObject("verification")
        val verification = verificationJson?.let { receipt ->
            val checksJson = receipt.optJSONArray("checks") ?: JSONArray()
            AiVerificationReceipt(
                checks = buildList {
                    for (i in 0 until minOf(checksJson.length(), MAX_CHECKS)) {
                        val item = checksJson.optJSONObject(i) ?: continue
                        add(
                            AiVerificationCheck(
                                id = item.optString("id"),
                                title = item.optString("title"),
                                status = runCatching {
                                    AiVerificationCheck.Status.valueOf(item.optString("status"))
                                }.getOrDefault(AiVerificationCheck.Status.SKIPPED),
                                detail = item.optString("detail"),
                            ),
                        )
                    }
                },
                verifiedAtEpochMs = receipt.optLong("verifiedAtEpochMs"),
            )
        }
        AiWorkflowSnapshot(
            workflowId = json.optString("workflowId"),
            workspaceId = json.optString("workspaceId").takeIf(String::isNotBlank),
            workspaceName = json.optString("workspaceName").takeIf(String::isNotBlank),
            request = json.optString("request"),
            phase = runCatching { AiWorkflowPhase.valueOf(json.optString("phase")) }
                .getOrDefault(AiWorkflowPhase.UNDERSTAND),
            status = runCatching { AiWorkflowSnapshot.Status.valueOf(json.optString("status")) }
                .getOrDefault(AiWorkflowSnapshot.Status.RUNNING),
            currentStep = json.optString("currentStep").takeIf(String::isNotBlank),
            activities = activities,
            generatedPlan = generatedPlan,
            verification = verification,
            summary = json.optString("summary").takeIf(String::isNotBlank),
            startedAtEpochMs = json.optLong("startedAtEpochMs"),
            updatedAtEpochMs = json.optLong("updatedAtEpochMs"),
        )
    }.getOrNull()

    companion object {
        private const val KEY_ACTIVE = "active"
        private const val KEY_PREFIX = "workflow::"
        private const val MAX_ACTIVITIES = 180
        private const val MAX_PLAN_STEPS = 8
        private const val MAX_PATHS = 20
        private const val MAX_CHECKS = 20
    }
}
