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

    fun canonicalPrefixes(): List<String> = allowedPrefixes.map { normalize(it, allowEmpty = true) }.distinct().sorted()

    companion object {
        const val MAX_PREFIXES = 8
        const val MAX_PATH_LENGTH = 500
        const val MAX_PATH_DEPTH = 32

        fun normalize(path: String, allowEmpty: Boolean = false): String {
            val value = path.replace('\\', '/').trim('/')
            if (value.isEmpty() && allowEmpty) return ""
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
