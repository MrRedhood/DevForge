package com.mrredhood.devforge.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class Editor2ModelTest {
    @Test
    fun lineIndexAndViewportAreBounded() {
        val document = Editor2Document("one\ntwo\nthree")
        assertEquals(3, document.lineCount())
        assertEquals(4, document.lineStart(2))
        assertEquals("two", document.visibleLines(2, 2).single())

        val renderer = EditorViewportRenderer(2)
        assertEquals(1..2, renderer.window(1, 10))
        assertEquals(9..10, renderer.window(9, 10))
    }

    @Test
    fun replacementKeepsStableLineModel() {
        val document = Editor2Document("a\nb\nc")
        document.replace(2, 3, "beta")
        assertEquals("a\nbeta\nc", document.snapshot())
        assertEquals(3, document.lineCount())
        assertEquals("beta", document.visibleLines(2, 2).single())
    }
}
