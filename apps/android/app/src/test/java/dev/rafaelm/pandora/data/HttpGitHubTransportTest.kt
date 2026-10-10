package dev.rafaelm.pandora.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HttpGitHubTransportTest {
    private class Connection(url: URL) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream("{}".toByteArray())
        override fun getResponseCode() = 200
        override fun getHeaderField(name: String?) = if (name == "Link") "<https://api.github.com/next>; rel=\"next\"" else null
    }
    @Test fun `OAuth parameters are form encoded and no credential enters the URL`() = runTest {
        val connection = Connection(URL("https://github.com/login/oauth/access_token"))
        val transport = HttpGitHubTransport { connection }
        transport.request(connection.url.toString(), "POST", """{"client_id":"Iv1.test","refresh_token":"token+with/slash","grant_type":"refresh_token"}""", null)
        assertEquals("application/x-www-form-urlencoded", connection.getRequestProperty("Content-Type"))
        assertTrue(connection.sent.toString("UTF-8").contains("refresh_token=token%2Bwith%2Fslash"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertNull(connection.url.query)
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.closed)
    }
    @Test fun `GitHub API sends JSON with bearer authentication and explicit version`() = runTest {
        val connection = Connection(URL("https://api.github.com/repos/owner/repo/issues"))
        val body = """{"title":"Bug"}"""
        val response = HttpGitHubTransport { connection }.request(connection.url.toString(), "POST", body, "ghu_test")
        assertEquals(body, connection.sent.toString("UTF-8"))
        assertEquals("Bearer ghu_test", connection.getRequestProperty("Authorization"))
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertEquals("2026-03-10", connection.getRequestProperty("X-GitHub-Api-Version"))
        assertTrue(response.hasNext)
    }
    @Test fun `cleartext foreign hosts and URL credentials are rejected before opening a connection`() = runTest {
        var opened = false
        val transport = HttpGitHubTransport { opened = true; Connection(it) }
        for (url in listOf("http://github.com/login/device/code", "https://github.com.evil.example/user", "https://token@api.github.com/user")) {
            try { transport.request(url, "GET", null, "secret"); fail("Expected invalid endpoint") }
            catch (_: IllegalArgumentException) { }
        }
        assertFalse(opened)
    }
}
