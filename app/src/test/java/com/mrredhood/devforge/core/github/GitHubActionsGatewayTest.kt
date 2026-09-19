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
        var requested: FakeConnection? = null
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory { url ->
                FakeConnection(URL(url), 200, "{\"login\":\"devforge-user\"}").also { requested = it }
            },
        )

        val result = gateway.validateCredential()

        assertEquals(GitHubCredentialValidation.Valid("devforge-user"), result)
        assertEquals("GET", requested?.requestMethod)
        assertTrue(requested?.url.toString().endsWith("/user"))
    }

    @Test
    fun reportsInvalidCredentialWithoutExposingToken() {
        var requested: FakeConnection? = null
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("secret-token-value"),
            connection = HttpConnectionFactory { url ->
                FakeConnection(URL(url), 401, "", "{\"message\":\"Bad credentials\"}").also { requested = it }
            },
        )

        val result = gateway.validateCredential()

        assertTrue(result is GitHubCredentialValidation.Invalid)
        val message = (result as GitHubCredentialValidation.Invalid).message
        assertTrue(message.contains("401"))
        assertTrue(!message.contains("secret-token-value"))
    }

    @Test
    fun cancellationUsesNormalCancelEndpoint() {
        var requested: FakeConnection? = null
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory { url ->
                FakeConnection(URL(url), 202, "").also { requested = it }
            },
        )

        val result = gateway.cancelRun("MrRedhood", "DevForge", 12345L)

        assertEquals(GitHubCancelResult.Accepted, result)
        assertEquals("POST", requested?.requestMethod)
        assertTrue(requested?.url.toString().endsWith("/repos/MrRedhood/DevForge/actions/runs/12345/cancel"))
    }

    @Test
    fun fetchLogsFollowsGithubTemporaryRedirect() {
        val requests = mutableListOf<String>()
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory { url ->
                requests += url
                when {
                    url.endsWith("/actions/runs/99/jobs?per_page=100") -> FakeConnection(
                        URL(url),
                        200,
                        """{"jobs":[{"id":7,"name":"build","status":"completed","conclusion":"success"}]}""",
                    )
                    url.endsWith("/actions/jobs/7/logs") -> FakeConnection(
                        URL(url),
                        302,
                        "",
                        "",
                        mapOf("Location" to "https://logs.example.test/job/7"),
                    )
                    url == "https://logs.example.test/job/7" -> FakeConnection(
                        URL(url),
                        200,
                        "Gradle task completed",
                    )
                    else -> error("Unexpected GitHub test URL: $url")
                }
            },
        )

        val result = gateway.fetchLogs("MrRedhood", "DevForge", 99L)

        assertTrue(result is GitHubLogsResult.Success)
        assertEquals("Gradle task completed", (result as GitHubLogsResult.Success).jobs.single().text)
        assertEquals(3, requests.size)
    }

    @Test
    fun cancellationMapsConflictWithoutPretendingSuccess() {
        val gateway = GitHubActionsGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory { url -> FakeConnection(URL(url), 409, "", "{\"message\":\"Conflict\"}") },
        )

        val result = gateway.cancelRun("owner", "repo", 99L)

        assertEquals(GitHubCancelResult.Conflict, result)
    }

    private class FakeSecretStore(
        private var value: String?,
    ) : SecretStore {
        override fun put(key: String, value: String) {
            this.value = value
        }

        override fun get(key: String): String? = value

        override fun remove(key: String) {
            value = null
        }
    }

    private class FakeConnection(
        url: URL,
        status: Int,
        body: String,
        errorBody: String = "",
        private val headers: Map<String, String> = emptyMap(),
    ) : HttpURLConnection(url) {
        private val responseStatus = status
        private val responseBody = body.toByteArray(Charsets.UTF_8)
        private val responseError = errorBody.toByteArray(Charsets.UTF_8)

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = responseStatus
        override fun getInputStream() = ByteArrayInputStream(responseBody)
        override fun getErrorStream() = ByteArrayInputStream(responseError)
        override fun getHeaderField(name: String): String? = headers[name]
    }
}
