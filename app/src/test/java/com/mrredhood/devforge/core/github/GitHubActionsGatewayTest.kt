package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.security.SecretStore
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubActionsGatewayTest {
    @Test
    fun validatesAuthenticatedCredentialThroughUserEndpoint() {
        val connection = FakeConnection(200, "{\"login\":\"devforge-user\"}")
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory { connection },
        )

        val result = gateway.validateCredential()

        assertEquals(GitHubCredentialValidation.Valid("devforge-user"), result)
        assertEquals("GET", connection.requestMethod)
        assertTrue(connection.url.toString().endsWith("/user"))
    }

    @Test
    fun reportsInvalidCredentialWithoutExposingToken() {
        val connection = FakeConnection(401, "", "{\"message\":\"Bad credentials\"}")
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("secret-token-value"),
            connection = HttpConnectionFactory { connection },
        )

        val result = gateway.validateCredential()

        assertTrue(result is GitHubCredentialValidation.Invalid)
        val message = (result as GitHubCredentialValidation.Invalid).message
        assertTrue(message.contains("401"))
        assertTrue(!message.contains("secret-token-value"))
    }

    @Test
    fun cancellationUsesNormalCancelEndpoint() {
        val connection = FakeConnection(202, "")
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory { connection },
        )

        val result = gateway.cancelRun("MrRedhood", "DevForge", 12345L)

        assertEquals(GitHubCancelResult.Accepted, result)
        assertEquals("POST", connection.requestMethod)
        assertTrue(connection.url.toString().endsWith("/repos/MrRedhood/DevForge/actions/runs/12345/cancel"))
    }

    @Test
    fun cancellationMapsConflictWithoutPretendingSuccess() {
        val connection = FakeConnection(409, "", "{\"message\":\"Conflict\"}")
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory { connection },
        )

        val result = gateway.cancelRun("owner", "repo", 99L)

        assertEquals(GitHubCancelResult.Conflict, result)
    }

    private class FakeSecretStore(
        private val token: String?,
    ) : SecretStore {
        private var value: String? = token

        override fun put(key: String, value: String) {
            this.value = value
        }

        override fun get(key: String): String? = value

        override fun remove(key: String) {
            value = null
        }
    }

    private class FakeConnection(
        status: Int,
        body: String,
        errorBody: String = "",
    ) : HttpURLConnection(URL("https://api.github.com/placeholder")) {
        private val responseStatus = status
        private val responseBody = body.toByteArray(Charsets.UTF_8)
        private val responseError = errorBody.toByteArray(Charsets.UTF_8)

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = responseStatus
        override fun getInputStream() = ByteArrayInputStream(responseBody)
        override fun getErrorStream() = ByteArrayInputStream(responseError)
    }
}
