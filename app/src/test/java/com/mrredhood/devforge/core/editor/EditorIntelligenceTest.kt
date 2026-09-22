package com.mrredhood.devforge.core.editor

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorIntelligenceTest {
    @Test
    fun detectsExpandedLanguageSet() {
        assertEquals(EditorLanguage.CSHARP, EditorLanguage.detect("Main.cs"))
        assertEquals(EditorLanguage.SWIFT, EditorLanguage.detect("App.swift"))
        assertEquals(EditorLanguage.PHP, EditorLanguage.detect("index.php"))
        assertEquals(EditorLanguage.RUBY, EditorLanguage.detect("app.rb"))
        assertEquals(EditorLanguage.LUA, EditorLanguage.detect("init.lua"))
        assertEquals(EditorLanguage.SCALA, EditorLanguage.detect("Main.scala"))
        assertEquals(EditorLanguage.ELIXIR, EditorLanguage.detect("app.ex"))
        assertEquals(EditorLanguage.MATLAB, EditorLanguage.detect("script.m"))
        assertEquals(EditorLanguage.OBJECTIVE_C, EditorLanguage.detect("App.mm"))
        assertEquals(EditorLanguage.FSHARP, EditorLanguage.detect("Program.fs"))
        assertEquals(EditorLanguage.JULIA, EditorLanguage.detect("main.jl"))
        assertEquals(EditorLanguage.ZIG, EditorLanguage.detect("main.zig"))
        assertEquals(EditorLanguage.NIM, EditorLanguage.detect("main.nim"))
        assertEquals(EditorLanguage.CLOJURE, EditorLanguage.detect("core.clj"))
        assertEquals(EditorLanguage.GDSCRIPT, EditorLanguage.detect("player.gd"))
        assertEquals(EditorLanguage.SOLIDITY, EditorLanguage.detect("Token.sol"))
        assertEquals(EditorLanguage.PASCAL, EditorLanguage.detect("main.pas"))
        assertEquals(EditorLanguage.FORTRAN, EditorLanguage.detect("main.f90"))
        assertEquals(EditorLanguage.OCAML, EditorLanguage.detect("Main.ml"))
        assertEquals(EditorLanguage.VHDL, EditorLanguage.detect("cpu.vhd"))
        assertEquals(EditorLanguage.VERILOG, EditorLanguage.detect("cpu.sv"))
        assertEquals(EditorLanguage.SCSS, EditorLanguage.detect("theme.scss"))
        assertEquals(EditorLanguage.GRAPHQL, EditorLanguage.detect("schema.graphql"))
        assertEquals(EditorLanguage.HCL, EditorLanguage.detect("main.tf"))
        assertEquals(EditorLanguage.POWERSHELL, EditorLanguage.detect("build.ps1"))
        assertEquals(EditorLanguage.DOCKERFILE, EditorLanguage.detect("Dockerfile"))
        assertEquals(EditorLanguage.MAKEFILE, EditorLanguage.detect("Makefile"))
    }

    @Test
    fun syntaxHighlightingStylesCaseInsensitiveSqlAndPreservesStrings() {
        val transformed = CodeSyntaxVisualTransformation(
            language = EditorLanguage.SQL,
            keywordColor = androidx.compose.ui.graphics.Color.Red,
            stringColor = androidx.compose.ui.graphics.Color.Blue,
            commentColor = androidx.compose.ui.graphics.Color.Green,
            numberColor = androidx.compose.ui.graphics.Color.Yellow,
        ).filter(AnnotatedString("SELECT '123' FROM users -- 456"))
        assertTrue(transformed.text.spanStyles.any { it.item.color == androidx.compose.ui.graphics.Color.Red })
        assertTrue(transformed.text.spanStyles.any { it.item.color == androidx.compose.ui.graphics.Color.Blue })
    }

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
