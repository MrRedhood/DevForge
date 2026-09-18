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

    var snapshot: GitHubConnectionSnapshot by mutableStateOf(initialSnapshot())
        private set

    fun connectWithToken(token: String) {
        val normalized = token.trim()
        if (normalized.isEmpty()) {
            snapshot = snapshot.copy(message = "Enter a GitHub token before connecting.")
            return
        }
        validationJob?.cancel()
        runCatching { secretStore.put(TOKEN_KEY, normalized) }.onFailure { error ->
            snapshot = snapshot.copy(message = error.message ?: "Unable to store the GitHub credential.")
            return
        }
        snapshot = GitHubConnectionSnapshot(message = "Verifying the GitHub credential with the live API…")
        validationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = repositoryGateway.validateCredential()
            withContext(Dispatchers.Main.immediate) {
                when (result) {
                    is GitHubCredentialValidation.Valid -> {
                        snapshot = GitHubConnectionSnapshot(
                            state = GitHubConnectionState.Connected(result.accountName),
                            credentialMode = GitHubCredentialMode.ManualToken,
                            message = "GitHub credential verified for @" + result.accountName + ".",
                        )
                    }
                    is GitHubCredentialValidation.Invalid -> {
                        runCatching { secretStore.remove(TOKEN_KEY) }
                        snapshot = GitHubConnectionSnapshot(
                            message = "GitHub credential validation failed: " + result.message,
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
        validationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = repositoryGateway.validateCredential()
            withContext(Dispatchers.Main.immediate) {
                snapshot = when (result) {
                    is GitHubCredentialValidation.Valid -> snapshot.copy(
                        state = GitHubConnectionState.Connected(result.accountName),
                        message = "GitHub credential is valid for @" + result.accountName + ".",
                    )
                    is GitHubCredentialValidation.Invalid -> snapshot.copy(
                        state = GitHubConnectionState.Disconnected,
                        message = "GitHub credential validation failed: " + result.message,
                    )
                }
            }
        }
    }

    fun disconnect() {
        validationJob?.cancel()
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

    override fun onCleared() {
        validationJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val TOKEN_KEY = "github_access_token"
    }
}
