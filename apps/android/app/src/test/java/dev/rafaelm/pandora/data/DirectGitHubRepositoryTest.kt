package dev.rafaelm.pandora.data

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class DirectGitHubRepositoryTest {
    private val configuration = GitHubConfiguration("Iv1.test", "pandora", "owner")
    private val profile = """{"login":"owner","name":"Owner","avatar_url":"https://avatars.githubusercontent.com/1","html_url":"https://github.com/owner"}"""
    private val device = """{"device_code":"private-device-code","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
    private val tokens = """{"access_token":"ghu_access","refresh_token":"ghr_refresh","expires_in":28800,"refresh_token_expires_in":15897600}"""
    private class Store : GitHubCredentialStore {
        var value: GitHubCredentials? = null
        val selections = mutableMapOf<String, Set<String>>()
        override fun credentials() = value
        override fun save(credentials: GitHubCredentials) { value = credentials }
        override fun clear() { value = null }
        override fun selected(login: String) = selections[login].orEmpty()
        override fun select(login: String, names: Set<String>) { selections[login] = names }
    }
    private data class Request(val url: String, val method: String, val body: String?, val token: String?, val time: Long)
    private class Fixture(config: GitHubConfiguration = GitHubConfiguration("Iv1.test", "pandora", "owner")) {
        var now = 0L
        val store = Store()
        val requests = mutableListOf<Request>()
        var handler: suspend (Request) -> GitHubHttpResponse = { error("Unexpected request: ${it.url}") }
        val repository = DirectGitHubRepository(config, store, GitHubTransport { url, method, body, token ->
            val request = Request(url, method, body, token, now)
            requests += request
            handler(request)
        }, clock = { now }, wait = { now += it })
        fun connected(expiresAt: Long = Long.MAX_VALUE) {
            store.value = GitHubCredentials("ghu_old", "ghr_old", expiresAt, Long.MAX_VALUE, "Iv1.test", "owner")
        }
    }
    private fun response(body: String, status: Int = 200, next: Boolean = false) = GitHubHttpResponse(status, body, next)
    private fun repo(id: Int, owner: String = "owner", archived: Boolean = false, issues: Boolean = true) = """{"id":$id,"name":"repo$id","full_name":"$owner/repo$id","private":true,"html_url":"https://github.com/$owner/repo$id","owner":{"login":"$owner"},"has_issues":$issues,"archived":$archived}"""

    @Test fun `pending and slowdown respect the GitHub interval and save tokens only after approval`() = runTest {
        val f = Fixture(); var polls = 0
        f.handler = { request -> when (request.url) {
            "https://github.com/login/device/code" -> response(device)
            "https://github.com/login/oauth/access_token" -> { polls++; response(when (polls) { 1 -> """{"error":"authorization_pending"}"""; 2 -> """{"error":"slow_down"}"""; else -> tokens }) }
            "https://api.github.com/user" -> { assertEquals("ghu_access", request.token); response(profile) }
            else -> error("Unexpected request")
        } }
        assertEquals("ABCD-EFGH", f.repository.connect().userCode)
        assertNull(f.store.value)
        assertFalse(f.repository.connection()); assertFalse(f.repository.connection()); assertTrue(f.repository.connection())
        assertEquals(listOf(5_000L, 10_000L, 20_000L), f.requests.filter { it.url.endsWith("/access_token") }.map { it.time })
        assertEquals("ghu_access", f.store.value!!.accessToken); assertEquals("ghr_refresh", f.store.value!!.refreshToken)
        assertFalse(f.repository.isPending())
        for (request in f.requests) assertFalse(request.body.orEmpty().contains("client_secret"))
    }
    @Test fun `wrong GitHub account is rejected before persisting tokens`() = runTest {
        val f = Fixture()
        f.handler = { when {
            it.url.endsWith("/device/code") -> response(device)
            it.url.endsWith("/access_token") -> response(tokens)
            else -> response(profile.replace("\"login\":\"owner\"", "\"login\":\"someone-else\""))
        } }
        f.repository.connect()
        try { f.repository.connection(); fail("Expected account rejection") } catch (error: DevelopmentApiException) { assertEquals(401, error.status) }
        assertNull(f.store.value)
    }
    @Test fun `expiry and declined authorization return explicit reconnect errors`() = runTest {
        for (error in listOf("access_denied", "expired_token")) {
            val f = Fixture(); f.handler = { if (it.url.endsWith("/device/code")) response(device) else response("""{"error":"$error"}""") }
            f.repository.connect()
            try { f.repository.connection(); fail("Expected rejection") } catch (failure: DevelopmentApiException) { assertEquals(401, failure.status) }
            assertNull(f.store.value)
        }
        val f = Fixture(); f.handler = { response(device) }; f.repository.connect(); f.now = 900_000
        try { f.repository.connection(); fail("Expected expiration") } catch (error: DevelopmentApiException) { assertEquals(401, error.status) }
        assertEquals(1, f.requests.size)
    }
    @Test fun `expired access token refreshes directly without a client secret`() = runTest {
        val f = Fixture(); f.connected(expiresAt = 0)
        f.handler = { request -> if (request.url.endsWith("/access_token")) {
            val body = Json.parseToJsonElement(request.body!!).jsonObject
            assertEquals("refresh_token", body["grant_type"]!!.jsonPrimitive.content)
            assertEquals("ghr_old", body["refresh_token"]!!.jsonPrimitive.content)
            assertFalse(body.containsKey("client_secret")); response(tokens)
        } else { assertEquals("ghu_access", request.token); response(profile) } }
        assertEquals("owner", f.repository.profile().login)
        assertEquals("ghr_refresh", f.store.value!!.refreshToken)
        assertEquals(28_800_000, f.store.value!!.expiresAt)
    }
    @Test fun `repository pagination keeps private personal repositories and excludes organizations and archives`() = runTest {
        val f = Fixture(); f.connected()
        f.handler = { request -> when {
            request.url.contains("/user/installations?") -> response("""{"installations":[{"id":4}]}""")
            request.url.endsWith("repositories?per_page=100&page=1") -> response("""{"repositories":[${repo(1)},${repo(2, "org")},${repo(3, archived = true)}]}""", next = true)
            else -> response("""{"repositories":[${repo(5)},${repo(6, issues = false)}]}""")
        } }
        val result = f.repository.repositories()
        assertEquals(listOf("repo1", "repo5"), result.repositories.map { it.name })
        assertTrue(result.repositories.all { it.private })
        assertEquals(configuration.installationUrl, result.installation_url)
    }
    @Test fun `issue pagination excludes pull requests and resumes without skipping issues`() = runTest {
        val f = Fixture(); f.connected()
        val body = (1..15).joinToString(prefix = "[", postfix = "]") { i -> """{"number":$i,"title":"Issue $i","html_url":"https://github.com/owner/repo1/issues/$i"${if (i == 2) ",\"pull_request\":{}" else ""}}""" }
        f.handler = { response(body) }
        val first = f.repository.issues("repo1"); assertEquals(10, first.items.size); assertEquals(11L, first.items.last().number); assertEquals(12, first.next_page)
        val next = f.repository.issues("repo1", first.next_page!!); assertEquals(listOf(12L, 13L, 14L, 15L), next.items.map { it.number }); assertNull(next.next_page)
    }
    @Test fun `issue payload preserves picker values and an uncertain POST is never retried`() = runTest {
        val f = Fixture(); f.connected()
        f.handler = { request ->
            assertEquals("POST", request.method); assertEquals("ghu_old", request.token)
            val body = Json.parseToJsonElement(request.body!!).jsonObject
            assertEquals("Bug", body["title"]!!.jsonPrimitive.content)
            assertEquals("[\"bug\"]", body["labels"].toString()); assertEquals("[\"owner\"]", body["assignees"].toString())
            throw IOException("Disconnected after sending")
        }
        try { f.repository.create("repo1", NewGitHubIssue("  Bug  ", "Details", listOf("bug"), listOf("owner"))); fail("Expected uncertain result") }
        catch (error: DevelopmentApiException) { assertEquals(504, error.status); assertTrue(error.message.contains("before retrying")) }
        assertEquals(1, f.requests.size)
    }
    @Test fun `missing configuration makes no requests and disconnect works offline`() = runTest {
        val f = Fixture(GitHubConfiguration("", "", ""))
        assertFalse(f.repository.isConfigured())
        try { f.repository.connect(); fail("Expected setup requirement") } catch (error: DevelopmentApiException) { assertEquals(503, error.status) }
        assertTrue(f.requests.isEmpty())
        f.connected(); f.repository.disconnect(); assertNull(f.store.value); assertTrue(f.requests.isEmpty())
    }
    @Test fun `cancelled authorization cannot store credentials from a late response`() = runTest {
        val f = Fixture(); val ready = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.handler = { request -> when {
            request.url.endsWith("/device/code") -> response(device)
            request.url.endsWith("/access_token") -> response(tokens)
            else -> { ready.complete(Unit); release.await(); response(profile) }
        } }
        f.repository.connect()
        val poll = launch { f.repository.connection() }
        ready.await(); f.repository.clearSession(); release.complete(Unit); poll.join()
        assertNull(f.store.value); assertFalse(f.repository.isPending())
    }
}
