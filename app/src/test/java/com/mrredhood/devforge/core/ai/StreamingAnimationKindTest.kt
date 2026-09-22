package com.mrredhood.devforge.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAnimationKindTest {
    @Test
    fun providesAtLeastTenDistinctAnimations() {
        assertTrue(StreamingAnimationKind.entries.size >= 10)
        assertEquals(
            StreamingAnimationKind.entries.size,
            StreamingAnimationKind.entries.map { it.title }.distinct().size,
        )
        assertEquals(
            StreamingAnimationKind.entries.size,
            StreamingAnimationKind.entries.map { it.glyph }.distinct().size,
        )
    }

    @Test
    fun fromIndexWrapsSafely() {
        val values = StreamingAnimationKind.entries
        assertEquals(values.first(), StreamingAnimationKind.fromIndex(0))
        assertEquals(values.last(), StreamingAnimationKind.fromIndex(-1))
        assertEquals(values.first(), StreamingAnimationKind.fromIndex(values.size))
        assertNotNull(StreamingAnimationKind.fromIndex(Int.MAX_VALUE))
    }
}
