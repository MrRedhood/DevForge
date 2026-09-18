package com.mrredhood.devforge.core.editor

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorIntelligenceTest {
    @Test
    fun foldingFindsMultilineBraceBlocks() {
        val ranges = EditorFolding.ranges("class Demo {\n  fun work() {\n  }\n}")
        assertTrue(ranges.isNotEmpty())
        assertTrue(ranges.any { it.startLine == 1 && it.endLine == 4 })
    }

    @Test
    fun foldingCapsRangesWithoutRepeatedWholePrefixScans() {
        val content = buildString {
            repeat(120) { index ->
                append("block$index {\n")
                append("  value = $index\n")
                append("}\n")
            }
        }
        val ranges = EditorFolding.ranges(content)
        assertEquals(80, ranges.size)
    }

    @Test
    fun diagnosticsFindsUnclosedBracket() {
        val report = EditorDiagnostics.analyze("Demo.kt", "fun demo() {")
        assertTrue(report.diagnostics.any { it.code == "EDITOR_BRACKET" })
    }

    @Test
    fun visibleWhitespaceKeepsMappingBounded() {
        val transformed = VisibleWhitespaceVisualTransformation().filter(AnnotatedString("a b\t\n"))
        assertEquals("a·b→   ↵\n", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(5, transformed.offsetMapping.transformedToOriginal(transformed.text.length))
    }
}
