package com.mrredhood.devforge.core.diagnostics

import org.json.JSONArray
import org.json.JSONObject

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
    HINT,
}

enum class DiagnosticSource {
    COMPILER,
    LINT,
    TEST,
    BUILD,
    GIT,
    UNKNOWN,
}

data class DiagnosticLocation(
    val path: String? = null,
    val line: Int? = null,
    val column: Int? = null,
) {
    fun isValid(): Boolean =
        (line == null || line > 0) && (column == null || column > 0)
}

data class Diagnostic(
    val severity: DiagnosticSeverity,
    val source: DiagnosticSource,
    val message: String,
    val code: String? = null,
    val location: DiagnosticLocation? = null,
    val origin: String? = null,
) {
    fun bounded(): Diagnostic? {
        val boundedMessage = message.trim().take(MAX_MESSAGE_CHARS)
        if (boundedMessage.isBlank()) return null
        val boundedCode = code?.trim()?.take(MAX_CODE_CHARS)?.ifBlank { null }
        val boundedOrigin = origin?.trim()?.take(MAX_ORIGIN_CHARS)?.ifBlank { null }
        val boundedLocation = location?.let {
            DiagnosticLocation(
                path = it.path?.trim()?.take(MAX_PATH_CHARS)?.ifBlank { null },
                line = it.line?.takeIf { line -> line > 0 },
                column = it.column?.takeIf { column -> column > 0 },
            )
        }
        return copy(
            message = boundedMessage,
            code = boundedCode,
            location = boundedLocation,
            origin = boundedOrigin,
        )
    }

    companion object {
        const val MAX_MESSAGE_CHARS = 4_000
        const val MAX_CODE_CHARS = 200
        const val MAX_PATH_CHARS = 500
        const val MAX_ORIGIN_CHARS = 300
    }
}

data class DiagnosticReport(
    val diagnostics: List<Diagnostic>,
    val truncated: Boolean = false,
) {
    val errorCount: Int get() = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount: Int get() = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    fun bounded(maxDiagnostics: Int = MAX_DIAGNOSTICS): DiagnosticReport {
        val safeLimit = maxDiagnostics.coerceIn(1, MAX_DIAGNOSTICS)
        val bounded = diagnostics.asSequence()
            .mapNotNull(Diagnostic::bounded)
            .take(safeLimit)
            .toList()
        val sourceCount = diagnostics.count()
        return DiagnosticReport(
            diagnostics = bounded,
            truncated = truncated || sourceCount > bounded.size,
        )
    }

    companion object {
        const val MAX_DIAGNOSTICS = 200
    }
}

object DiagnosticTextParser {
    private val locationPatterns = listOf(
        Regex("""^(?:e:|w:)?\\s*(.+?):(\\d+)(?::(\\d+))?:\\s*(?:error|warning|info|hint)?:?\\s*(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^(.+?):(\\d+)(?::(\\d+))?:\\s*(.+)$"""),
    )

    fun parseCompilerOutput(
        output: String,
        maxDiagnostics: Int = DiagnosticReport.MAX_DIAGNOSTICS,
    ): DiagnosticReport {
        val diagnostics = ArrayList<Diagnostic>()
        var truncated = false
        for (line in output.lineSequence()) {
            val text = line.trim()
            if (text.isBlank()) continue
            val match = locationPatterns.asSequence().mapNotNull { it.matchEntire(text) }.firstOrNull() ?: continue
            val groups = match.groupValues
            val path = groups[1].trim().ifBlank { null }
            val lineNumber = groups[2].toIntOrNull()
            val column = groups[3].toIntOrNull().takeIf { groups.size > 3 && it > 0 }
            val rawMessage = groups.last().trim()
            val severity = when {
                Regex("\\berror\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> DiagnosticSeverity.ERROR
                Regex("\\bwarning\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> DiagnosticSeverity.WARNING
                else -> DiagnosticSeverity.INFO
            }
            val diagnostic = Diagnostic(
                severity = severity,
                source = DiagnosticSource.COMPILER,
                message = rawMessage,
                location = DiagnosticLocation(path, lineNumber, column),
                origin = "compiler-text",
            ).bounded() ?: continue
            if (diagnostics.size >= maxDiagnostics.coerceIn(1, DiagnosticReport.MAX_DIAGNOSTICS)) {
                truncated = true
                break
            }
            diagnostics += diagnostic
        }
        return DiagnosticReport(diagnostics, truncated)
    }
}

object SarifDiagnosticParser {
    fun parse(
        sarifJson: String,
        maxDiagnostics: Int = DiagnosticReport.MAX_DIAGNOSTICS,
    ): DiagnosticReport {
        val limit = maxDiagnostics.coerceIn(1, DiagnosticReport.MAX_DIAGNOSTICS)
        val diagnostics = ArrayList<Diagnostic>()
        var truncated = false
        val root = runCatching { JSONObject(sarifJson) }.getOrNull()
            ?: return DiagnosticReport(emptyList(), truncated = true)

        val runs = root.optJSONArray("runs") ?: return DiagnosticReport(emptyList())
        for (runIndex in 0 until runs.length()) {
            val run = runs.optJSONObject(runIndex) ?: continue
            val toolName = run.optJSONObject("tool")?.optJSONObject("driver")?.optString("name").orEmpty()
            val rules = run.optJSONObject("tool")?.optJSONObject("driver")?.optJSONArray("rules")
            val ruleTitles = buildMap {
                if (rules != null) {
                    for (index in 0 until rules.length()) {
                        val rule = rules.optJSONObject(index) ?: continue
                        val id = rule.optString("id").trim()
                        if (id.isBlank()) continue
                        val shortDescription = rule.optJSONObject("shortDescription")?.optString("text").orEmpty()
                        if (shortDescription.isNotBlank()) put(id, shortDescription)
                    }
                }
            }
            val results = run.optJSONArray("results") ?: continue
            for (resultIndex in 0 until results.length()) {
                if (diagnostics.size >= limit) {
                    truncated = true
                    break
                }
                val result = results.optJSONObject(resultIndex) ?: continue
                val level = result.optString("level").lowercase()
                val severity = when (level) {
                    "error" -> DiagnosticSeverity.ERROR
                    "warning" -> DiagnosticSeverity.WARNING
                    "note", "none" -> DiagnosticSeverity.INFO
                    else -> DiagnosticSeverity.INFO
                }
                val code = result.optString("ruleId").ifBlank { null }
                val message = result.optJSONObject("message")?.optString("text")
                    ?.ifBlank { null }
                    ?: ruleTitles[code]
                    ?: continue
                val location = result.optJSONArray("locations")
                    ?.optJSONObject(0)
                    ?.optJSONObject("physicalLocation")
                    ?.let { physical ->
                        val artifact = physical.optJSONObject("artifactLocation")
                        val region = physical.optJSONObject("region")
                        val path = artifact?.optString("uri")?.ifBlank { null }
                        DiagnosticLocation(
                            path = path,
                            line = region?.optInt("startLine", 0)?.takeIf { it > 0 },
                            column = region?.optInt("startColumn", 0)?.takeIf { it > 0 },
                        )
                    }
                diagnostics += Diagnostic(
                    severity = severity,
                    source = DiagnosticSource.LINT,
                    message = message,
                    code = code,
                    location = location,
                    origin = toolName.ifBlank { "sarif" },
                ).bounded() ?: continue
            }
            if (truncated) break
        }
        return DiagnosticReport(diagnostics, truncated).bounded(limit)
    }
}

object DiagnosticReportCodec {
    fun encode(report: DiagnosticReport): String {
        val bounded = report.bounded()
        val diagnostics = JSONArray()
        bounded.diagnostics.forEach { diagnostic ->
            val item = JSONObject()
                .put("severity", diagnostic.severity.name)
                .put("source", diagnostic.source.name)
                .put("message", diagnostic.message)
            diagnostic.code?.let { item.put("code", it) }
            diagnostic.origin?.let { item.put("origin", it) }
            diagnostic.location?.let { location ->
                val locationJson = JSONObject()
                location.path?.let { locationJson.put("path", it) }
                location.line?.let { locationJson.put("line", it) }
                location.column?.let { locationJson.put("column", it) }
                item.put("location", locationJson)
            }
            diagnostics.put(item)
        }
        return JSONObject()
            .put("version", 1)
            .put("truncated", bounded.truncated)
            .put("diagnostics", diagnostics)
            .toString()
    }

    fun decode(json: String): DiagnosticReport {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return DiagnosticReport(emptyList(), truncated = true)
        if (root.optInt("version", -1) != 1) return DiagnosticReport(emptyList(), truncated = true)
        val items = root.optJSONArray("diagnostics") ?: return DiagnosticReport(emptyList(), root.optBoolean("truncated"))
        val diagnostics = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val severity = runCatching { DiagnosticSeverity.valueOf(item.optString("severity")) }.getOrNull() ?: continue
                val source = runCatching { DiagnosticSource.valueOf(item.optString("source")) }.getOrNull() ?: DiagnosticSource.UNKNOWN
                val location = item.optJSONObject("location")?.let {
                    DiagnosticLocation(
                        path = it.optString("path").ifBlank { null },
                        line = it.optInt("line", 0).takeIf { line -> line > 0 },
                        column = it.optInt("column", 0).takeIf { column -> column > 0 },
                    )
                }
                add(
                    Diagnostic(
                        severity = severity,
                        source = source,
                        message = item.optString("message"),
                        code = item.optString("code").ifBlank { null },
                        location = location,
                        origin = item.optString("origin").ifBlank { null },
                    )
                )
            }
        }
        return DiagnosticReport(diagnostics, root.optBoolean("truncated")).bounded()
    }
}
