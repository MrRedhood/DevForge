package com.mrredhood.devforge.core.editor

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ContentHasher {
    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
