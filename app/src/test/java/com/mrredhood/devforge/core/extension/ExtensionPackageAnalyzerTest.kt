package com.mrredhood.devforge.core.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPackageAnalyzerTest {
    @Test
    fun recognizesNativeKotlinLanguageExtension() {
        val root = createTempDir()
        File(root, "package.json").writeText(
            """
            {
              "publisher": "test",
              "name": "kotlin-extra",
              "displayName": "Kotlin Extras",
              "version": "1.0.0",
              "contributes": {
                "languages": [{
                  "id": "kotlin",
                  "extensions": [".kt", ".kts"]
                }]
              }
            }
            """.trimIndent(),
        )
        val result = ExtensionPackageAnalyzer.analyze(root).getOrThrow()
        assertEquals(ExtensionCompatibility.NATIVE_LANGUAGE, result.manifest.compatibility)
        assertTrue(result.manifest.nativeLanguages.any { it.contains("KOTLIN") })
        root.deleteRecursively()
    }

    @Test
    fun rejectsUnknownProgrammingLanguageInsteadOfPretendingSupport() {
        val root = createTempDir()
        File(root, "package.json").writeText(
            """
            {
              "publisher": "test",
              "name": "unknown-language",
              "displayName": "Unknown Language",
              "version": "1.0.0",
              "contributes": {
                "languages": [{
                  "id": "newlang",
                  "extensions": [".newlang"]
                }]
              }
            }
            """.trimIndent(),
        )
        val result = ExtensionPackageAnalyzer.analyze(root).getOrThrow()
        assertEquals(ExtensionCompatibility.UNSUPPORTED, result.manifest.compatibility)
        assertTrue(result.manifest.unsupportedReason?.contains("does not currently support natively") == true)
        root.deleteRecursively()
    }

    @Test
    fun rejectsAcodePluginUsingUnsupportedEditorModule() {
        val root = createTempDir()
        File(root, "plugin.json").writeText(
            """{"id":"test.editor","name":"Editor Plugin","version":"1.0.0","main":"main.js"}""",
        )
        File(root, "main.js").writeText(
            """acode.setPluginInit("test.editor", () => acode.require("editor"));""",
        )
        val result = ExtensionPackageAnalyzer.analyze(root).getOrThrow()
        assertEquals(ExtensionCompatibility.UNSUPPORTED, result.manifest.compatibility)
        root.deleteRecursively()
    }

    @Test
    fun recognizesBundledAcodeCommandPlugin() {
        val root = createTempDir()
        File(root, "plugin.json").writeText(
            """
            {
              "id": "test.acode.plugin",
              "name": "Test Plugin",
              "version": "1.0.0",
              "main": "main.js"
            }
            """.trimIndent(),
        )
        File(root, "main.js").writeText(
            """
            acode.setPluginInit("test.acode.plugin", () => {
              const commands = acode.require("commands");
              commands.addCommand({ name: "test.command", exec: () => acode.toast("ok") });
            });
            """.trimIndent(),
        )
        val result = ExtensionPackageAnalyzer.analyze(root).getOrThrow()
        assertEquals(ExtensionCompatibility.ACODE_RUNTIME_SUPPORTED, result.manifest.compatibility)
        assertEquals(ExtensionSource.ACODE, result.manifest.source)
        root.deleteRecursively()
    }

    private fun createTempDir(): File =
        kotlin.io.path.createTempDirectory("devforge-extension-test").toFile()
}
