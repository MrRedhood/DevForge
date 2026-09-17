package com.mrredhood.devforge.core.automation

import com.mrredhood.devforge.core.storage.AutomationEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

sealed interface AutomationEvent {
    data class RepositoryChanged(
        val workspaceId: String,
        val branch: String?,
        val changedPaths: List<String>,
        val fingerprint: String,
    ) : AutomationEvent

    data class BuildCompleted(
        val owner: String,
        val repository: String,
        val branch: String,
        val runId: Long,
        val conclusion: String?,
        val target: String,
    ) : AutomationEvent
}

sealed interface AutomationTriggerConfig {
    data object None : AutomationTriggerConfig

    data class RepositoryChange(
        val branch: String? = null,
        val pathPrefixes: List<String> = emptyList(),
    ) : AutomationTriggerConfig

    data class BuildCompletion(
        val owner: String,
        val repository: String,
        val branch: String? = null,
        val conclusion: String? = null,
        val target: String? = null,
    ) : AutomationTriggerConfig

    data class Condition(
        val event: String,
        val conditions: Map<String, String>,
    ) : AutomationTriggerConfig
}

object AutomationTriggerCodec {
    fun encode(config: AutomationTriggerConfig): String = when (config) {
        AutomationTriggerConfig.None -> ""
        is AutomationTriggerConfig.RepositoryChange -> JSONObject()
            .put("branch", config.branch)
            .put("paths", JSONArray(config.pathPrefixes.take(MAX_PATHS)))
            .toString()
        is AutomationTriggerConfig.BuildCompletion -> JSONObject()
            .put("owner", config.owner.take(MAX_FIELD))
            .put("repository", config.repository.take(MAX_FIELD))
            .put("branch", config.branch)
            .put("conclusion", config.conclusion)
            .put("target", config.target)
            .toString()
        is AutomationTriggerConfig.Condition -> JSONObject()
            .put("event", config.event.take(MAX_FIELD))
            .put("conditions", JSONObject().apply { config.conditions.entries.take(MAX_CONDITIONS).forEach { put(it.key.take(MAX_FIELD), it.value.take(MAX_FIELD)) } })
            .toString()
    }

    fun decode(type: AutomationTriggerType, value: String?): AutomationTriggerConfig {
        if (type == AutomationTriggerType.SCHEDULE || type == AutomationTriggerType.MANUAL) return AutomationTriggerConfig.None
        val raw = value?.trim().orEmpty()
        require(raw.isNotBlank()) { "Automation trigger configuration is missing." }
        val json = JSONObject(raw)
        return when (type) {
            AutomationTriggerType.REPOSITORY_CHANGE -> AutomationTriggerConfig.RepositoryChange(
                branch = json.optString("branch").takeIf(String::isNotBlank),
                pathPrefixes = json.optJSONArray("paths")?.let { array -> buildList(minOf(array.length(), MAX_PATHS)) { for (i in 0 until minOf(array.length(), MAX_PATHS)) add(array.optString(i, "")) } }?.filter(String::isNotBlank).orEmpty(),
            )
            AutomationTriggerType.BUILD_COMPLETION -> AutomationTriggerConfig.BuildCompletion(
                owner = json.optString("owner").trim(),
                repository = json.optString("repository").trim(),
                branch = json.optString("branch").takeIf(String::isNotBlank),
                conclusion = json.optString("conclusion").takeIf(String::isNotBlank),
                target = json.optString("target").takeIf(String::isNotBlank),
            )
            AutomationTriggerType.CONDITION -> {
                val values = linkedMapOf<String, String>()
                val conditions = json.optJSONObject("conditions") ?: JSONObject()
                val keys = conditions.keys()
                while (keys.hasNext() && values.size < MAX_CONDITIONS) {
                    val key = keys.next()
                    values[key] = conditions.optString(key, "")
                }
                AutomationTriggerConfig.Condition(json.optString("event").trim(), values)
            }
            else -> AutomationTriggerConfig.None
        }
    }

    fun matches(automation: AutomationEntity, event: AutomationEvent): Boolean {
        val type = runCatching { AutomationTriggerType.valueOf(automation.triggerType) }.getOrNull() ?: return false
        val config = runCatching { decode(type, automation.schedule) }.getOrElse { return false }
        return when (event) {
            is AutomationEvent.RepositoryChanged -> when (config) {
                is AutomationTriggerConfig.RepositoryChange -> repositoryMatches(config, event)
                is AutomationTriggerConfig.Condition -> config.event.equals("repository_change", true) && conditionMatches(config.conditions, mapOf(
                    "workspaceId" to event.workspaceId,
                    "branch" to event.branch.orEmpty(),
                    "paths" to event.changedPaths.joinToString(","),
                ))
                else -> false
            }
            is AutomationEvent.BuildCompleted -> when (config) {
                is AutomationTriggerConfig.BuildCompletion -> buildMatches(config, event)
                is AutomationTriggerConfig.Condition -> config.event.equals("build_completion", true) && conditionMatches(config.conditions, mapOf(
                    "owner" to event.owner,
                    "repository" to event.repository,
                    "branch" to event.branch,
                    "conclusion" to event.conclusion.orEmpty(),
                    "target" to event.target,
                ))
                else -> false
            }
        }
    }

    private fun repositoryMatches(config: AutomationTriggerConfig.RepositoryChange, event: AutomationEvent.RepositoryChanged): Boolean {
        if (config.branch != null && config.branch != event.branch) return false
        if (config.pathPrefixes.isEmpty()) return true
        return event.changedPaths.any { path -> config.pathPrefixes.any { prefix -> path == prefix || path.startsWith("$prefix/") } }
    }

    private fun buildMatches(config: AutomationTriggerConfig.BuildCompletion, event: AutomationEvent.BuildCompleted): Boolean =
        config.owner.equals(event.owner, true) &&
            config.repository.equals(event.repository, true) &&
            (config.branch == null || config.branch == event.branch) &&
            (config.conclusion == null || config.conclusion.equals(event.conclusion, true)) &&
            (config.target == null || config.target.equals(event.target, true))

    private fun conditionMatches(conditions: Map<String, String>, values: Map<String, String>): Boolean =
        conditions.all { (key, expected) ->
            val actual = values[key] ?: return false
            expected == "*" || actual.equals(expected, true) || (key == "paths" && actual.split(',').any { it == expected })
        }

    fun eventFingerprint(workspaceId: String, branch: String?, paths: List<String>): String {
        val input = buildString {
            append(workspaceId).append('\u0000').append(branch.orEmpty()).append('\u0000')
            paths.sorted().take(500).forEach { append(it).append('\u0000') }
        }
        return sha256(input)
    }

    fun eventKey(event: AutomationEvent): String = when (event) {
        is AutomationEvent.RepositoryChanged -> "repo-${event.fingerprint}"
        is AutomationEvent.BuildCompleted -> "build-${event.owner.lowercase()}/${event.repository.lowercase()}-${event.runId}"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private const val MAX_FIELD = 120
    private const val MAX_PATHS = 40
    private const val MAX_CONDITIONS = 16
}
