package com.mrredhood.devforge.core.quality

import com.mrredhood.devforge.core.security.SecretRedactor

data class SecurityFinding(
    val path: String,
    val severity: String,
    val summary: String,
)

object DevForgeSecurityScanner {
    fun scan(files: List<Pair<String, String>>, maxFindings: Int = 20): List<SecurityFinding> =
        files.asSequence()
            .filter { (_, content) -> SecretRedactor.containsLikelySecret(content) }
            .map { (path, content) ->
                SecurityFinding(
                    path = path.take(240),
                    severity = "review",
                    summary = "Secret-like material detected in the currently open content; inspect before sharing or committing.",
                )
            }
            .take(maxFindings.coerceIn(1, 50))
            .toList()
}

data class PerformanceBudgetSnapshot(
    val openTabs: Int,
    val dirtyTabs: Int,
    val totalOpenContentChars: Int,
    val maxOpenTabs: Int = 24,
    val maxTotalOpenContentChars: Int = 4_000_000,
) {
    val withinBudget: Boolean =
        openTabs <= maxOpenTabs && totalOpenContentChars <= maxTotalOpenContentChars
}

data class ReleaseGateResult(
    val checks: List<Pair<String, Boolean>>,
) {
    val passed: Int get() = checks.count { it.second }
    val failed: Int get() = checks.count { !it.second }
    val ready: Boolean get() = failed == 0 && checks.isNotEmpty()
}

object DevForgeReleaseQualityGate {
    fun evaluate(
        hasSuccessfulBuild: Boolean,
        artifactsPresent: Boolean,
        logsAvailable: Boolean,
        noUnsavedEditorChanges: Boolean,
        diagnosticsClear: Boolean,
        configurationComplete: Boolean,
    ): ReleaseGateResult = ReleaseGateResult(
        checks = listOf(
            "Build completed successfully" to hasSuccessfulBuild,
            "Expected artifacts available" to artifactsPresent,
            "Build logs/report data available" to logsAvailable,
            "No unsaved editor changes" to noUnsavedEditorChanges,
            "Diagnostics are clear" to diagnosticsClear,
            "Build configuration is complete" to configurationComplete,
        ),
    )
}
