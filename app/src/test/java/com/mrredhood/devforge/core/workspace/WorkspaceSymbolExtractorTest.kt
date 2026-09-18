package com.mrredhood.devforge.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceSymbolExtractorTest {
    @Test
    fun extractsCommonDeclarations() {
        val symbols = WorkspaceSymbolExtractor.extract("Sample.kt", "class Demo
interface Service
fun runTask() {}
object Registry")
        assertEquals(listOf("Demo", "Service", "runTask", "Registry"), symbols.map { it.name })
    }
}
