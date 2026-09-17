package com.mrredhood.devforge.core.github

sealed interface GitHubConnectionState {
    data object Disconnected : GitHubConnectionState
    data class Connected(val accountName: String? = null) : GitHubConnectionState
}

enum class GitHubCredentialMode {
    ManualToken,
    OAuth,
}

data class GitHubConnectionSnapshot(
    val state: GitHubConnectionState = GitHubConnectionState.Disconnected,
    val credentialMode: GitHubCredentialMode = GitHubCredentialMode.ManualToken,
    val message: String? = null,
)
