package com.mrredhood.devforge.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageServerRegistryTest {

    @Test
    fun registryDeduplicatesAndFiltersInvalidDescriptors() {
        val valid = LanguageServerDescriptor(
            id = "builtin",
            displayName = "Built-in",
            languages = setOf(EditorLanguage.KOTLIN, EditorLanguage.JAVA),
            capabilities = setOf(LspCapability.DIAGNOSTICS),
        )
        val duplicate = valid.copy(displayName = "Duplicate")
        val invalid = valid.copy(id = "", displayName = "Invalid")

        val registry = LanguageServerRegistry(listOf(valid, duplicate, invalid))

        assertEquals(1, registry.all().size)
        assertEquals("Built-in", registry.find("builtin")?.displayName)
        assertEquals(1, registry.forLanguage(EditorLanguage.KOTLIN).size)
        assertNotNull(registry.forLanguage(EditorLanguage.JAVA).firstOrNull())
        assertNull(registry.find("missing"))
    }
}
