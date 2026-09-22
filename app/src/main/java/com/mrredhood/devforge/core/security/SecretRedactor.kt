package com.mrredhood.devforge.core.security

/**
 * Best-effort redaction for data that is about to cross into durable logs, receipts or review
 * metadata. Authorization hashes and live tool arguments must use the original bytes and never
 * pass through this class.
 */
object SecretRedactor {
    private val sensitiveKeyPattern = Regex(
        """(?i)("?(?:api[_-]?key|access[_-]?token|auth[_-]?token|authorization|bearer|password|passwd|secret|private[_-]?key|client[_-]?secret|refresh[_-]?token)"?\s*[:=]\s*["']?)([^"'\s,}]+)"""
    )
    private val bearerPattern = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+\-/]+=*""")
    private val basicPattern = Regex("""(?i)\bBasic\s+[A-Za-z0-9+/]+=*""")
    private val commonTokenPatterns = listOf(
        Regex("""\bsk-[A-Za-z0-9._-]{16,}\b"""),
        Regex("""\bsk-proj-[A-Za-z0-9._-]{12,}\b"""),
        Regex("""\bgithub_pat_[A-Za-z0-9_]{20,}\b"""),
        Regex("""\bghp_[A-Za-z0-9]{20,}\b"""),
        Regex("""\bAIza[A-Za-z0-9_-]{20,}\b"""),
        Regex("""\bAKIA[A-Z0-9]{16}\b"""),
        Regex("""\bya29\.[A-Za-z0-9._-]{20,}\b"""),
        Regex("""\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"""),
    )

    fun redact(input: String, maxChars: Int = MAX_OUTPUT_CHARS): String {
        var value = input
        value = bearerPattern.replace(value, "Bearer $REDACTED")
        value = basicPattern.replace(value, "Basic $REDACTED")
        commonTokenPatterns.forEach { pattern ->
            value = pattern.replace(value, REDACTED)
        }
        value = sensitiveKeyPattern.replace(value) { match -> match.groupValues[1] + REDACTED }
        return value.take(maxChars.coerceIn(1, MAX_OUTPUT_CHARS))
    }

    fun containsLikelySecret(input: String): Boolean =
        sensitiveKeyPattern.containsMatchIn(input) ||
            bearerPattern.containsMatchIn(input) ||
            basicPattern.containsMatchIn(input) ||
            commonTokenPatterns.any { it.containsMatchIn(input) }

    const val REDACTED = "[REDACTED]"
    const val MAX_OUTPUT_CHARS = 64 * 1024
}
