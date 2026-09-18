package com.mrredhood.devforge.core.github

import com.mrredhood.devforge.core.security.SecretStore
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepositoryGatewayTest {
    @Test
    fun validatesCandidateTokenWithoutOverwritingStoredToken() {
        val store = FakeSecretStore("old-token")
        var openedConnection: CapturingConnection? = null
        val gateway = GitHubRepositoryGateway(
            secretStore = store,
            connection = HttpConnectionFactory {
                CapturingConnection(
                    url = URL(it),
                    body = """{"login":"candidate-user"}""",
                ).also { openedConnection = it }
            },
        )

        val result = gateway.validateCredential("new-token")

        assertTrue(result is GitHubCredentialValidation.Valid)
        assertEquals("new-token", openedConnection?.requestProperty("Authorization")?.removePrefix("Bearer "))
        assertEquals("old-token", store.value())
    }

    @Test
    fun rejectsUnsafeRepositoryIdentifiersBeforeNetworkAccess() {
        var opened = false
        val gateway = GitHubRepositoryGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory {
                opened = true
                FakeConnection(URL(it), 200, """{"login":"ok"}""")
            },
        )

        val result = gateway.getRepository("owner/../../", "repo")

        assertTrue(result is GitHubRepositoryResult.Failure)
        assertTrue(!opened)
    }

    @Test
    fun rejectsUnsafeWorkflowRepositoryIdentifiersBeforeNetworkAccess() {
        var opened = false
        val gateway = GitHubRepositoryGateway(
            secretStore = FakeSecretStore("token"),
            connection = HttpConnectionFactory {
                opened = true
                FakeConnection(URL(it), 200, """{"workflows":[]}""")
            },
        )

        val result = gateway.listWorkflows("owner", "repo/actions")

        assertTrue(result is GitHubWorkflowListResult.Failure)
        assertTrue(!opened)
    }

    private class FakeSecretStore(private var value: String?) : SecretStore {
        override fun put(key: String, value: String) { this.value = value }
        override fun get(key: String): String? = value
        override fun remove(key: String) { value = null }
        fun value(): String? = value
    }

    private class CapturingConnection(
        url: URL,
        body: String,
    ) : HttpURLConnection(url) {
        private val responseBody = body.toByteArray(Charsets.UTF_8)

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(responseBody)
        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))
        fun requestProperty(name: String): String? = getRequestProperty(name)
    }

    private class FakeConnection(
        url: URL,
        status: Int,
        body: String,
    ) : HttpURLConnection(url) {
        private val responseStatus = status
        private val responseBody = body.toByteArray(Charsets.UTF_8)

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = responseStatus
        override fun getInputStream() = ByteArrayInputStream(responseBody)
        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))
    }
}
