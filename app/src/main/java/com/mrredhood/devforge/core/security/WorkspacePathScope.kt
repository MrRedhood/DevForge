package com.mrredhood.devforge.core.security

/** Explicit path boundary for agent and automation workspace operations. */
data class WorkspacePathScope(
    val allowedPrefixes: List<String> = listOf("")
) {
    init {
        require(allowedPrefixes.size <= MAX_PREFIXES) { "A path scope may contain at most $MAX_PREFIXES prefixes." }
        allowedPrefixes.forEach { normalize(it, allowEmpty = true) }
    }

    fun allows(path: String): Boolean = runCatching {
        val normalized = normalize(path, allowEmpty = true)
        if (allowedPrefixes.isEmpty()) return@runCatching true
        allowedPrefixes.any { prefix ->
            val scope = normalize(prefix, allowEmpty = true)
            scope.isEmpty() || normalized == scope || normalized.startsWith("$scope/")
        }
    }.getOrDefault(false)

    fun requireAllowed(path: String): String {
        val normalized = normalize(path, allowEmpty = true)
        require(allows(normalized)) { "Path is outside the authorized workspace scope." }
        return normalized
    }

    fun canonicalPrefixes(): List<String> {
        if (allowedPrefixes.isEmpty()) return listOf("")
        return allowedPrefixes
            .map { normalize(it, allowEmpty = true) }
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .ifEmpty { listOf("") }
    }

    /** Returns true when this granted scope completely contains the required action scope. */
    fun covers(required: WorkspacePathScope): Boolean {
        val granted = canonicalPrefixes()
        val grantedIsRoot = allowedPrefixes.isEmpty() || allowedPrefixes.any { normalize(it, allowEmpty = true).isEmpty() }
        val requiredIsRoot = required.allowedPrefixes.isEmpty() || required.allowedPrefixes.any { normalize(it, allowEmpty = true).isEmpty() }
        if (grantedIsRoot) return true
        if (requiredIsRoot) return false
        return required.canonicalPrefixes().all { requiredPrefix ->
            granted.any { grantedPrefix ->
                requiredPrefix == grantedPrefix || requiredPrefix.startsWith("$grantedPrefix/")
            }
        }
    }

    companion object {
        const val MAX_PREFIXES = 8
        const val MAX_PATH_LENGTH = 500
        const val MAX_PATH_DEPTH = 32

        fun normalize(path: String, allowEmpty: Boolean = false): String {
            val value = path.replace('\\', '/').trim('/')
            if (value.isEmpty() && allowEmpty) return ""
            if (value == "." && allowEmpty) return ""
            require(value.isNotBlank()) { "Workspace path cannot be empty." }
            require(value.length <= MAX_PATH_LENGTH) { "Workspace path is too long." }
            val parts = value.split('/')
            require(parts.size <= MAX_PATH_DEPTH) { "Workspace path is too deep." }
            require(parts.none { it.isBlank() || it == "." || it == ".." }) { "Workspace path is invalid." }
            require(parts.none { it.equals(".git", true) }) { "Git metadata is outside the agent workspace scope." }
            return parts.joinToString("/")
        }
    }
}
