package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.mrredhood.devforge.core.security.CredentialSecurityStore

class GitHubConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val secretStore = CredentialSecurityStore(application)

    var snapshot: GitHubConnectionSnapshot by mutableStateOf(initialSnapshot())
        private set

    fun connectWithToken(token: String) {
        val normalized = token.trim()
        if (normalized.isEmpty()) {
            snapshot = snapshot.copy(message = "Enter a GitHub token before connecting.")
            return
        }

        runCatching { secretStore.put(TOKEN_KEY, normalized) }.onFailure { error ->
            snapshot = snapshot.copy(message = error.message ?: "Unable to store the GitHub credential.")
            return
        }
        snapshot = GitHubConnectionSnapshot(
            state = GitHubConnectionState.Connected(),
            credentialMode = GitHubCredentialMode.ManualToken,
            message = "Token stored securely on this device. Account verification is ready from the repository picker.",
        )
    }

    fun disconnect() {
        runCatching { secretStore.remove(TOKEN_KEY) }
            .onSuccess { snapshot = GitHubConnectionSnapshot(message = "GitHub credentials removed from this device.") }
            .onFailure { error -> snapshot = snapshot.copy(message = error.message ?: "Unlock protected credentials before disconnecting.") }
    }

    fun hasStoredCredential(): Boolean = secretStore.contains(TOKEN_KEY)

    fun isCredentialLocked(): Boolean = secretStore.isProtectionEnabled && !secretStore.isUnlocked && secretStore.contains(TOKEN_KEY)

    private fun initialSnapshot(): GitHubConnectionSnapshot = if (hasStoredCredential()) {
        GitHubConnectionSnapshot(state = GitHubConnectionState.Connected())
    } else {
        GitHubConnectionSnapshot()
    }

    companion object {
        const val TOKEN_KEY = "github_access_token"
    }
}
