package com.mrredhood.devforge.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    @Test
    fun redactsCredentialNamedJsonValues() {
        val input = """{"apiKey":"top-secret-value","password":"hunter2","safe":"visible"}"""
        val output = SecretRedactor.redact(input)

        assertFalse(output.contains("top-secret-value"))
        assertFalse(output.contains("hunter2"))
        assertTrue(output.contains(SecretRedactor.REDACTED))
        assertTrue(output.contains("visible"))
    }

    @Test
    fun redactsCommonProviderAndAuthorizationTokens() {
        val input = listOf(
            "Authorization: Bearer abcdefghijklmnopqrstuvwxyz",
            "key=sk-proj-1234567890abcdef123456",
            "github_pat_123456789012345678901234",
            "ghp_123456789012345678901234",
            "AIza123456789012345678901234",
        ).joinToString("\n")

        val output = SecretRedactor.redact(input)

        assertFalse(output.contains("abcdefghijklmnopqrstuvwxyz"))
        assertFalse(output.contains("sk-proj-1234567890abcdef123456"))
        assertFalse(output.contains("github_pat_123456789012345678901234"))
        assertFalse(output.contains("ghp_123456789012345678901234"))
        assertFalse(output.contains("AIza123456789012345678901234"))
        assertTrue(output.contains(SecretRedactor.REDACTED))
    }

    @Test
    fun truncatesRedactedOutputToConfiguredBound() {
        val input = "x".repeat(SecretRedactor.MAX_OUTPUT_CHARS + 500)
        val output = SecretRedactor.redact(input)

        assertTrue(output.length <= SecretRedactor.MAX_OUTPUT_CHARS)
    }

    @Test
    fun detectsLikelySecretsWithoutMutatingOriginalInput() {
        val input = """token=sk-1234567890abcdef123456"""
        assertTrue(SecretRedactor.containsLikelySecret(input))
        assertTrue(input.contains("sk-1234567890abcdef123456"))
    }
}
