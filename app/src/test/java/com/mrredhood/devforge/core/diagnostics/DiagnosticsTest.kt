package com.mrredhood.devforge.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun compilerOutputProducesBoundedStructuredDiagnostics() {
        val report = DiagnosticTextParser.parseCompilerOutput(
            """
            e: src/main.kt:12:4: error: unresolved reference: widget
            w: src/util.kt:8: warning: deprecated API
            info.txt:3:2: note
            """.trimIndent(),
        )

        assertEquals(2, report.diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, report.diagnostics[0].severity)
        assertEquals("src/main.kt", report.diagnostics[0].location?.path)
        assertEquals(12, report.diagnostics[0].location?.line)
        assertEquals(4, report.diagnostics[0].location?.column)
        assertEquals(DiagnosticSeverity.WARNING, report.diagnostics[1].severity)
    }

    @Test
    fun sarifRoundTripPreservesDiagnosticFields() {
        val sarif = """
            {
              "runs": [{
                "tool": {"driver": {"name": "Android Lint"}},
                "results": [{
                  "ruleId": "HardcodedText",
                  "level": "warning",
                  "message": {"text": "Avoid hardcoded text."},
                  "locations": [{
                    "physicalLocation": {
                      "artifactLocation": {"uri": "app/src/main/res/layout/main.xml"},
                      "region": {"startLine": 14, "startColumn": 9}
                    }
                  }]
                }]
              }]
            }
        """.trimIndent()

        val report = SarifDiagnosticParser.parse(sarif)
        assertEquals(1, report.diagnostics.size)
        val diagnostic = report.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertEquals(DiagnosticSource.LINT, diagnostic.source)
        assertEquals("HardcodedText", diagnostic.code)
        assertEquals(14, diagnostic.location?.line)

        val decoded = DiagnosticReportCodec.decode(DiagnosticReportCodec.encode(report))
        assertEquals(report, decoded)
        assertFalse(decoded.truncated)
    }

    @Test
    fun diagnosticReportCapsInputAndMarksTruncation() {
        val output = (1..250).joinToString("\n") { "e: src/File$it.kt:1:1: error: failure $it" }
        val report = DiagnosticTextParser.parseCompilerOutput(output)

        assertEquals(DiagnosticReport.MAX_DIAGNOSTICS, report.diagnostics.size)
        assertTrue(report.truncated)
    }

    @Test
    fun malformedSarifFailsClosedWithoutThrowing() {
        val report = SarifDiagnosticParser.parse("{not-json")
        assertTrue(report.truncated)
        assertTrue(report.diagnostics.isEmpty())
    }
}
