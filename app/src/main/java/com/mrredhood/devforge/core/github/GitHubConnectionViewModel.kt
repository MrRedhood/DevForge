package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GitHubConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val secretStore = CredentialSecurityStore(application)
    private val repositoryGateway = GitHubRepositoryGateway(secretStore)
    private var validationJob: Job? = null

    var isValidating by mutableStateOf(false)
        private set

    var snapshot: GitHubConnectionSnapshot by mutableStateOf(initialSnapshot())
        private set

    fun connectWithToken(token: String) {
        val normalized = normalizeToken(token)
        if (normalized.isEmpty()) {
            snapshot = snapshot.copy(message = "Enter a GitHub token before connecting.")
            return
        }

        validationJob?.cancel()
        isValidating = true
        snapshot = snapshot.copy(message = "Verifying the GitHub credential with the live API…")

        validationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = repositoryGateway.validateCredential(normalized)
            withContext(Dispatchers.Main.immediate) {
                isValidating = false
                when (result) {
                    is GitHubCredentialValidation.Valid -> {
                        val stored = runCatching { secretStore.put(TOKEN_KEY, normalized) }
                        snapshot = if (stored.isSuccess) {
                            GitHubConnectionSnapshot(
                                state = GitHubConnectionState.Connected(result.accountName),
                                credentialMode = GitHubCredentialMode.ManualToken,
                                message = "GitHub credential verified for @" + result.accountName + " and stored securely.",
                            )
                        } else {
                            GitHubConnectionSnapshot(
                                message = stored.exceptionOrNull()?.message
                                    ?: "GitHub verified the token, but DevForge could not store it securely.",
                            )
                        }
                    }
                    is GitHubCredentialValidation.Invalid -> {
                        snapshot = snapshot.copy(
                            state = if (secretStore.contains(TOKEN_KEY)) {
                                GitHubConnectionState.CredentialStored
                            } else {
                                GitHubConnectionState.Disconnected
                            },
                            message = githubCredentialMessage(result.message),
                        )
                    }
                }
            }
        }
    }

    fun validateCredential() {
        if (!secretStore.contains(TOKEN_KEY)) {
            snapshot = GitHubConnectionSnapshot(message = "No GitHub credential is stored on this device.")
            return
        }
        validationJob?.cancel()
        snapshot = snapshot.copy(message = "Verifying the stored GitHub credential with the live API…")
        isValidating = true
        validationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = repositoryGateway.validateCredential()
            withContext(Dispatchers.Main.immediate) {
                isValidating = false
                snapshot = when (result) {
                    is GitHubCredentialValidation.Valid -> snapshot.copy(
                        state = GitHubConnectionState.Connected(result.accountName),
                        message = "GitHub credential is valid for @" + result.accountName + ".",
                    )
                    is GitHubCredentialValidation.Invalid -> {
                        if (isLikelyInvalidCredential(result.message)) {
                            runCatching { secretStore.remove(TOKEN_KEY) }
                            snapshot.copy(
                                state = GitHubConnectionState.Disconnected,
                                message = "The saved GitHub token was rejected (HTTP 401 / bad credentials) and has been removed. Paste a fresh GitHub token and connect again.",
                            )
                        } else {
                            snapshot.copy(
                                state = GitHubConnectionState.CredentialStored,
                                message = githubCredentialMessage(result.message),
                            )
                        }
                    }
                }
            }
        }
    }

    fun disconnect() {
        validationJob?.cancel()
        isValidating = false
        runCatching { secretStore.remove(TOKEN_KEY) }
            .onSuccess { snapshot = GitHubConnectionSnapshot(message = "GitHub credentials removed from this device.") }
            .onFailure { error -> snapshot = snapshot.copy(message = error.message ?: "Unlock protected credentials before disconnecting.") }
    }

    fun hasStoredCredential(): Boolean = secretStore.contains(TOKEN_KEY)

    fun isCredentialLocked(): Boolean = secretStore.isProtectionEnabled && !secretStore.isUnlocked && secretStore.contains(TOKEN_KEY)

    private fun initialSnapshot(): GitHubConnectionSnapshot = if (hasStoredCredential()) {
        GitHubConnectionSnapshot(
            state = GitHubConnectionState.CredentialStored,
            message = "Credential is stored securely. Verify it before using GitHub operations.",
        )
    } else {
        GitHubConnectionSnapshot()
    }

    private fun isLikelyInvalidCredential(message: String): Boolean =
        message.contains("HTTP 401", ignoreCase = true) ||
            message.contains("bad credentials", ignoreCase = true)

    private fun normalizeToken(value: String): String =
        value.trim().replaceFirst(Regex("(?i)^Bearer\\s+"), "").trim().removeSurrounding("\"").trim()

    private fun githubCredentialMessage(message: String): String =
        if (isLikelyInvalidCredential(message)) {
            "GitHub rejected the saved token (HTTP 401 / bad credentials). The token may be expired, revoked, or incorrectly copied. Use a fresh GitHub token."
        } else {
            "GitHub credential validation failed: " + message
        }

    override fun onCleared() {
        validationJob?.cancel()
        isValidating = false
        super.onCleared()
    }

    companion object {
        const val TOKEN_KEY = "github_access_token"
    }
}
