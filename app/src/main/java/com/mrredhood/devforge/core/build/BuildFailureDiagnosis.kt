package com.mrredhood.devforge.core.build

import com.mrredhood.devforge.core.github.GitHubJobLog

data class BuildFailureFinding(
    val category: String,
    val severity: String,
    val summary: String,
    val evidence: String,
    val jobName: String,
)

object BuildFailureDiagnosis {
    private data class Rule(
        val category: String,
        val severity: String,
        val summary: String,
        val regex: Regex,
    )

    private val rules = listOf(
        Rule("Kotlin compile", "error", "Kotlin compilation reported a source-level error.", Regex("(?m)^e: file://.*$")),
        Rule("Gradle", "error", "Gradle reported a task or dependency failure.", Regex("(?i)execution failed for task|build failed|could not resolve")),
        Rule("Android lint", "warning", "Android lint reported one or more issues.", Regex("(?i)lint.*(error|warning)|:app:lint(debug|release)")),
        Rule("Tests", "error", "A test task reported a failure.", Regex("(?i)(tests? failed|test execution failed|there were failing tests)")),
        Rule("Signing", "error", "Release signing or keystore validation failed.", Regex("(?i)(keystore|apksigner|jarsigner|signing).*(fail|invalid|error)|invalid input")),
        Rule("GitHub Actions", "error", "The remote workflow or GitHub API reported an execution failure.", Regex("(?i)github.*(403|401|422)|workflow.*(failed|forbidden)|http 4(0|1|3|22)")),
        Rule("Network", "warning", "A dependency or remote service request failed.", Regex("(?i)(timeout|connection reset|429 too many requests|temporarily unavailable|network)")),
        Rule("Android SDK", "error", "An Android SDK/build-tools component was missing or unavailable.", Regex("(?i)(sdkmanager|build-tools|platforms;android|android sdk).*(not found|missing|failed)")),
    )

    fun analyze(logs: List<GitHubJobLog>): List<BuildFailureFinding> {
        val findings = mutableListOf<BuildFailureFinding>()
        logs.forEach { job ->
            val text = job.text.take(MAX_LOG_CHARS)
            rules.forEach { rule ->
                val match = rule.regex.find(text) ?: return@forEach
                val evidence = text.lines()
                    .firstOrNull { line -> rule.regex.containsMatchIn(line) }
                    ?.trim()
                    ?.take(MAX_EVIDENCE)
                    ?: match.value.trim().take(MAX_EVIDENCE)
                findings += BuildFailureFinding(
                    category = rule.category,
                    severity = rule.severity,
                    summary = rule.summary,
                    evidence = evidence,
                    jobName = job.jobName,
                )
            }
        }
        return findings
            .distinctBy { it.category + "|" + it.jobName + "|" + it.evidence }
            .take(MAX_FINDINGS)
    }

    private const val MAX_FINDINGS = 20
    private const val MAX_LOG_CHARS = 512 * 1024
    private const val MAX_EVIDENCE = 500
}
