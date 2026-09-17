package com.mrredhood.devforge.core.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.mrredhood.devforge.core.security.AndroidSecretStore
import com.mrredhood.devforge.core.security.SecretStore

class GitHubConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val secretStore: SecretStore = AndroidSecretStore(application)

    var snapshot: GitHubConnectionSnapshot by mutableStateOf(initialSnapshot())
        private set

    fun connectWithToken(token: String) {
        val normalized = token.trim()
        if (normalized.isEmpty()) {
            snapshot = snapshot.copy(message = "Enter a GitHub token before connecting.")
            return
        }

        secretStore.put(TOKEN_KEY, normalized)
        snapshot = GitHubConnectionSnapshot(
            state = GitHubConnectionState.Connected(),
            credentialMode = GitHubCredentialMode.ManualToken,
            message = "Token stored securely on this device. Account verification is ready from the repository picker.",
        )
    }

    fun disconnect() {
        secretStore.remove(TOKEN_KEY)
        snapshot = GitHubConnectionSnapshot(message = "GitHub credentials removed from this device.")
    }

    fun hasStoredCredential(): Boolean = secretStore.get(TOKEN_KEY) != null

    private fun initialSnapshot(): GitHubConnectionSnapshot = if (hasStoredCredential()) {
        GitHubConnectionSnapshot(state = GitHubConnectionState.Connected())
    } else {
        GitHubConnectionSnapshot()
    }

    companion object {
        const val TOKEN_KEY = "github_access_token"
    }
}
