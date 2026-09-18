package com.mrredhood.devforge.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentFilePatchCodecTest {
    @Test
    fun roundTripsStructuredPatch() {
        val patch = AgentFilePatch(
            path = "src/Main.kt",
            content = "fun main() = Unit",
            summary = "Update entry point",
            expectedContentHash = "a".repeat(64),
        )

        val decoded = AgentFilePatchCodec.decode(AgentFilePatchCodec.encode(patch))

        assertEquals(patch, decoded)
    }

    @Test
    fun optionalExpectedHashMayBeOmitted() {
        val patch = AgentFilePatchCodec.decode("{"path":"src/Main.kt","content":"new"}")
        assertNull(patch.expectedContentHash)
    }

    @Test
    fun rejectsInvalidHashAndOversizedContent() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentFilePatchCodec.decode("{"path":"src/Main.kt","content":"new","expectedContentHash":"bad"}")
        }

        val oversized = "x".repeat(AgentFilePatchCodec.MAX_PATCH_CONTENT_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) {
            AgentFilePatchCodec.decode(
                org.json.JSONObject()
                    .put("path", "src/Main.kt")
                    .put("content", oversized)
                    .toString(),
            )
        }
    }
}
