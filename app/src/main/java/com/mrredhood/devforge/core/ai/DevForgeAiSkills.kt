package com.mrredhood.devforge.core.ai

data class DevForgeAiSkill(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val guidance: String,
)

object DevForgeAiSkills {
    val builtIn: List<DevForgeAiSkill> = listOf(
        DevForgeAiSkill(
            id = "android",
            title = "Android engineering",
            keywords = listOf("android", "activity", "fragment", "compose", "manifest", "gradle", "apk", "aab"),
            guidance = "Treat Android lifecycle, Compose state, Gradle configuration, manifest changes and device compatibility as first-class constraints.",
        ),
        DevForgeAiSkill(
            id = "ui",
            title = "UI engineering",
            keywords = listOf("ui", "screen", "layout", "compose", "button", "dialog", "navigation", "design"),
            guidance = "Prefer compact mobile-first interaction, bounded recomposition, accessible touch targets and simple state ownership.",
        ),
        DevForgeAiSkill(
            id = "testing",
            title = "Testing",
            keywords = listOf("test", "tests", "unit", "instrumentation", "coverage", "regression", "verify"),
            guidance = "Add focused regression coverage for the behavior being changed and report verification separately from implementation.",
        ),
        DevForgeAiSkill(
            id = "security",
            title = "Security",
            keywords = listOf("security", "secret", "token", "credential", "auth", "permission", "approval", "privacy"),
            guidance = "Treat model output as untrusted, never persist secrets in source or receipts, and keep mutating or sensitive actions behind approval policy.",
        ),
        DevForgeAiSkill(
            id = "performance",
            title = "Performance",
            keywords = listOf("performance", "slow", "lag", "memory", "cache", "startup", "optimize", "optimization"),
            guidance = "Prefer bounded work, background IO, incremental updates and measurable hot-path improvements over broad rewrites.",
        ),
        DevForgeAiSkill(
            id = "git",
            title = "Git and release",
            keywords = listOf("git", "github", "commit", "branch", "release", "actions", "ci", "workflow", "publish"),
            guidance = "Keep Git mutations explicit, never force-push from an automated workflow, and use Build Center receipts as the authoritative build/test/lint evidence.",
        ),
    )

    fun matched(request: String, workspaceContext: String = ""): List<DevForgeAiSkill> {
        val haystack = (request + "\n" + workspaceContext).lowercase()
        return builtIn.filter { skill -> skill.keywords.any(haystack::contains) }.take(4)
    }

    fun prompt(request: String, workspaceContext: String = ""): String {
        val skills = matched(request, workspaceContext)
        val skillText = if (skills.isEmpty()) "No specialized skill profile matched; use the default DevForge engineering policy." else
            skills.joinToString("\n") { "- " + it.title + ": " + it.guidance }
        return listOf(
            "DevForge engineering policy:",
            "- Before changing files, inspect relevant workspace state and any `.devforge/rules/` or `.devforge/skills/` files that exist in the workspace.",
            "- Do not override explicit project rules with model preferences; if a rule conflicts with the requested work, surface the conflict instead of silently ignoring it.",
            "- Separate plan, execution and verification. Do not claim a build, test or lint passed without an authoritative receipt.",
            "- Keep changes narrowly scoped to the mission and preserve unrelated user work.",
            "- Prefer safe, reversible operations and ask for approval whenever the existing capability policy requires it.",
            "Matched skill profile:",
            skillText,
        ).joinToString("\n")
    }
}