package com.mrredhood.devforge.core.extension.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevForgeManifestValidatorTest {
    @Test
    fun acceptsValidV1Manifest() {
        val raw = """
            {
              "manifestVersion": 1,
              "id": "com.example.ai-reviewer",
              "name": "AI Code Reviewer",
              "version": "1.2.0",
              "publisher": {"id": "example", "name": "Example Labs"},
              "description": "Reviews code with DevForge AI.",
              "type": "extension",
              "devforge": {"api": "1.0", "minimumVersion": "1.0.0"},
              "entry": {"runtime": "javascript", "main": "dist/main.js"},
              "permissions": ["workspace.read", "editor.read", "ai.use", "ui.contribute", "commands.register"],
              "activation": {"events": ["workspace.opened", "command:review-file"]},
              "contributes": {
                "commands": [{"id": "review-file", "title": "Review File"}],
                "panels": [{"id": "review", "title": "Review", "location": "editor"}]
              }
            }
        """.trimIndent()
        val result = DevForgeManifestValidator.parse(raw)
        assertTrue(result is DevForgeManifestValidation.Valid)
        val manifest = (result as DevForgeManifestValidation.Valid).manifest
        assertEquals("com.example.ai-reviewer", manifest.id)
        assertEquals("1.2.0", manifest.version)
        assertEquals(2, manifest.contributions.commands.size + manifest.contributions.panels.size)
    }

    @Test
    fun rejectsUnknownPermission() {
        val result = DevForgeManifestValidator.parse(
            """{"manifestVersion":1,"id":"com.example.bad","name":"Bad","version":"1.0.0","publisher":{"id":"example","name":"Example"},"type":"extension","devforge":{"api":"1.0","minimumVersion":"1.0.0"},"entry":{"runtime":"javascript","main":"main.js"},"permissions":["workspace.magic"]}"""
        )
        assertTrue(result is DevForgeManifestValidation.Invalid)
        assertTrue((result as DevForgeManifestValidation.Invalid).errors.any { it.contains("Unknown permission") })
    }

    @Test
    fun requiresPermissionForContributions() {
        val result = DevForgeManifestValidator.parse(
            """{"manifestVersion":1,"id":"com.example.bad","name":"Bad","version":"1.0.0","publisher":{"id":"example","name":"Example"},"type":"extension","devforge":{"api":"1.0","minimumVersion":"1.0.0"},"entry":{"runtime":"javascript","main":"main.js"},"permissions":[],"contributes":{"commands":[{"id":"x","title":"X"}]}}"""
        )
        assertTrue(result is DevForgeManifestValidation.Invalid)
        assertTrue((result as DevForgeManifestValidation.Invalid).errors.any { it.contains("commands.register") })
    }

    @Test
    fun rejectsInvalidSemVer() {
        assertEquals(null, DevForgeSemVer.parse("1.0"))
        assertEquals("1.2.3", DevForgeSemVer.parse("1.2.3")?.stableString())
    }

    @Test
    fun permissionSetValidatesDependencies() {
        val permissions = DevForgePermissionSet(setOf(DevForgePermission.NETWORK_UNRESTRICTED))
        assertTrue(permissions.validate().any { it.contains("network.access") })
    }
}
