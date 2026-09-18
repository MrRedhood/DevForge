package com.mrredhood.devforge.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AIProviderRegistryTest {
    @Test
    fun registersExpandedProviders() {
        assertEquals(8, AIProvider.entries.size)
        assertEquals(AIWireProtocol.ANTHROPIC_MESSAGES, AIProviderRegistry.spec(AIProvider.ANTHROPIC).wireProtocol)
        assertEquals("https://api.x.ai/v1", AIProviderRegistry.spec(AIProvider.XAI_GROK).defaultBaseUrl)
        assertEquals("https://api.deepinfra.com/v1/openai", AIProviderRegistry.spec(AIProvider.DEEPINFRA).defaultBaseUrl)
        assertEquals("https://api.groq.com/openai/v1", AIProviderRegistry.spec(AIProvider.GROQ).defaultBaseUrl)
    }

    @Test
    fun buildsProviderEndpoints() {
        assertEquals(
            "https://api.x.ai/v1/chat/completions",
            AIProviderRegistry.chatEndpoint(AIProvider.XAI_GROK, null, "grok-4.6"),
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AIProviderRegistry.chatEndpoint(AIProvider.ANTHROPIC, null),
        )
        assertEquals(
            "https://api.deepinfra.com/v1/openai/chat/completions",
            AIProviderRegistry.chatEndpoint(AIProvider.DEEPINFRA, null),
        )
    }

    @Test
    fun validatesCustomHttpsEndpoint() {
        assertEquals(
            "https://example.com/v1",
            AIProviderRegistry.validateCustomBaseUrl("https://example.com/v1/"),
        )
        assertEquals(
            "http://localhost:8080/v1",
            AIProviderRegistry.validateCustomBaseUrl("http://localhost:8080/v1/"),
        )
    }

    @Test
    fun rejectsUnsafeCustomEndpoint() {
        assertRejected { AIProviderRegistry.validateCustomBaseUrl("http://example.com/v1") }
        assertRejected { AIProviderRegistry.validateCustomBaseUrl("https://user:pass@example.com/v1") }
        assertRejected { AIProviderRegistry.validateCustomBaseUrl("https://example.com/v1?key=secret") }
        assertRejected { AIProviderRegistry.validateCustomBaseUrl("https://example.com/v1/../admin") }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        fail("Expected IllegalArgumentException.")
    }
}
