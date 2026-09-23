package com.mrredhood.devforge.core.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPackageAnalyzerTest {

    @Test
    fun recognizesDevForgeNativePackageManifest() {
        val root = createTempDir()
        File(root, "manifest.json").writeText(
            """
            {
              "manifestVersion": 1,
              "id": "com.example.hello",
              "name": "Hello DevForge",
              "version": "1.0.0",
              "publisher": {"id": "example", "name": "Example"},
              "description": "Test package",
              "type": "extension",
              "devforge": {"api": "1.0", "minimumVersion": "0.1.0"},
              "entry": {"runtime": "javascript", "main": "main.js"},
              "permissions": ["workspace.read", "commands.register"]
            }
            """.trimIndent(),
        )
        File(root, "main.js").writeText("export function activate() {}")
        val result = ExtensionPackageAnalyzer.analyze(root).getOrThrow()
        assertEquals(ExtensionSource.DEVFORGE, result.manifest.source)
        assertEquals(ExtensionPackageKind.RUNTIME, result.manifest.kind)
        assertEquals(ExtensionCompatibility.DEVFORGE_RUNTIME_SUPPORTED, result.manifest.compatibility)
        assertEquals("com.example.hello", result.manifest.id)
        root.deleteRecursively()
    }

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
    fun recognizesVsixStyleNestedExtensionDirectory() {
        val root = createTempDir()
        val extension = File(root, "extension").apply { mkdirs() }
        File(extension, "package.json").writeText(
            """{"publisher":"test","name":"icon-theme","displayName":"Icon Theme","version":"1.0.0","contributes":{"iconThemes":[{"id":"icons","label":"Icons","path":"theme.json"}]}}""",
        )
        File(extension, "theme.json").writeText("""{"iconDefinitions":{}}""")
        val result = ExtensionPackageAnalyzer.analyze(root).getOrThrow()
        assertEquals(ExtensionSource.VSCODE, result.manifest.source)
        assertEquals(ExtensionPackageKind.ICON_THEME, result.manifest.kind)
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

    @Test
    fun extractsVsCodeConfigurationSettings() {
        val root = createTempDir()
        File(root, "package.json").writeText(
            """{"publisher":"test","name":"settings","displayName":"Settings","version":"1.0.0","contributes":{"configuration":{"properties":{"test.enabled":{"type":"boolean","title":"Enabled","default":true,"description":"Enable it"}}}}}""",
        )
        val settings = ExtensionPackageAnalyzer.analyze(root).getOrThrow().manifest.settings
        assertEquals(1, settings.size)
        assertEquals("test.enabled", settings.single().key)
        assertEquals(ExtensionSettingType.BOOLEAN, settings.single().type)
        assertEquals("true", settings.single().defaultValue)
        root.deleteRecursively()
    }

    @Test
    fun extractsAcodeSettings() {
        val root = createTempDir()
        File(root, "plugin.json").writeText(
            """{"id":"test.settings","name":"Settings","version":"1.0.0","main":"main.js","settings":{"enabled":{"type":"boolean","label":"Enabled","default":false}}}""",
        )
        File(root, "main.js").writeText("acode.setPluginInit(\"test.settings\", () => {});")
        val settings = ExtensionPackageAnalyzer.analyze(root).getOrThrow().manifest.settings
        assertEquals(1, settings.size)
        assertEquals(ExtensionSettingType.BOOLEAN, settings.single().type)
        assertEquals("false", settings.single().defaultValue)
        root.deleteRecursively()
    }

    private fun createTempDir(): File =
        kotlin.io.path.createTempDirectory("devforge-extension-test").toFile()
}
