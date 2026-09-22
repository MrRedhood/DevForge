package com.mrredhood.devforge.core.extension

import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxedExtensionRegistryTest {
    @Test
    fun rejectsUnsafeExtensionIdentifiers() {
        val registry = SandboxedExtensionRegistry()
        val result = registry.register(
            ExtensionManifest(
                id = "../escape",
                name = "Bad",
                version = "1.0",
                capabilities = emptySet(),
                entryPoint = "main",
            ),
        )
        assertTrue(result is ExtensionValidation.Invalid)
    }

    @Test
    fun parsesBoundedManifest() {
        val registry = SandboxedExtensionRegistry()
        val result = registry.parseManifest(
            "{\"id\":\"demo\",\"name\":\"Demo\",\"version\":\"1.0\",\"capabilities\":[\"READ_WORKSPACE\"],\"entryPoint\":\"main\"}",
        )
        assertTrue(result is ExtensionValidation.Valid)
    }
}
