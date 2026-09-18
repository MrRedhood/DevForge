package com.mrredhood.devforge.core.github

sealed interface GitHubConnectionState {
    data object Disconnected : GitHubConnectionState
    data object CredentialStored : GitHubConnectionState
    data class Connected(val accountName: String? = null) : GitHubConnectionState
}

enum class GitHubCredentialMode {
    ManualToken,
    OAuth,
}

sealed interface GitHubCredentialValidation {
    data class Valid(val accountName: String) : GitHubCredentialValidation
    data class Invalid(val message: String) : GitHubCredentialValidation
}

data class GitHubConnectionSnapshot(
    val state: GitHubConnectionState = GitHubConnectionState.Disconnected,
    val credentialMode: GitHubCredentialMode = GitHubCredentialMode.ManualToken,
    val message: String? = null,
)
