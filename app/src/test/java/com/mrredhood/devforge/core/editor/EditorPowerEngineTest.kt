package com.mrredhood.devforge.core.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorPowerEngineTest {
    @Test
    fun duplicateLinePreservesCursorColumn() {
        val edit = EditorPowerEngine.duplicateLine("one\ntwo", 1)
        assertEquals("one\none\ntwo", edit.content)
        assertEquals(5, edit.cursor)
    }

    @Test
    fun deleteMiddleLineRemovesOnlyThatLine() {
        val edit = EditorPowerEngine.deleteLine("one\ntwo\nthree", 5)
        assertEquals("one\nthree", edit.content)
    }

    @Test
    fun moveLineUpKeepsColumn() {
        val edit = EditorPowerEngine.moveLine("one\ntwo\nthree", 6, -1)
        assertEquals("two\none\nthree", edit.content)
        assertEquals(2, edit.cursor)
    }
}