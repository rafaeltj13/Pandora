package dev.rafaelm.pandora.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** These values identify the personal GitHub App; none is a client secret. */
data class GitHubConfiguration(val clientId: String, val appSlug: String, val login: String) {
    val ready: Boolean get() = clientId.matches(Regex("[A-Za-z0-9_.-]+")) &&
        appSlug.matches(Regex("[A-Za-z0-9-]+")) && login.matches(Regex("[A-Za-z0-9-]+"))
    val installationUrl: String get() = "https://github.com/apps/$appSlug/installations/new"
}

internal data class GitHubHttpResponse(val status: Int, val body: String, val hasNext: Boolean = false)
internal fun interface GitHubTransport {
    suspend fun request(url: String, method: String, body: String?, token: String?): GitHubHttpResponse
}

internal class HttpGitHubTransport(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : GitHubTransport {
    override suspend fun request(url: String, method: String, body: String?, token: String?): GitHubHttpResponse = withContext(Dispatchers.IO) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host in setOf("github.com", "api.github.com") && uri.userInfo == null) { "GitHub requests must use its HTTPS endpoints" }
        val connection = openConnection(uri.toURL())
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Pandora-Android")
            if (uri.host == "api.github.com") {
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            }
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                val payload = if (uri.host == "github.com") {
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    Json.decodeFromString<Map<String, String>>(body).entries.joinToString("&") { (key, value) ->
                        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                    }
                } else {
                    connection.setRequestProperty("Content-Type", "application/json")
                    body
                }
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            GitHubHttpResponse(status, text, connection.getHeaderField("Link")?.contains("rel=\"next\"") == true)
        } finally { connection.disconnect() }
    }
}

@Serializable private data class DeviceCodeResponse(val device_code: String, val user_code: String, val verification_uri: String, val expires_in: Long, val interval: Long = 5)
@Serializable private data class OAuthResponse(
    val access_token: String? = null, val refresh_token: String? = null,
    val expires_in: Long? = null, val refresh_token_expires_in: Long? = null,
    val error: String? = null,
)
@Serializable private data class InstallationsResponse(val installations: List<Installation>)
@Serializable private data class Installation(val id: Long)
@Serializable private data class RepositoriesResponse(val repositories: List<GitHubRepository>)
@Serializable private data class IssueResponse(val number: Long, val title: String, val html_url: String, val pull_request: JsonObject? = null) {
    fun issue() = GitHubIssue(number, title, html_url)
}

class DirectGitHubRepository internal constructor(
    private val configuration: GitHubConfiguration,
    private val storage: GitHubCredentialStore,
    private val transport: GitHubTransport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : DevelopmentRepository {
    constructor(configuration: GitHubConfiguration, storage: GitHubCredentialStore) : this(configuration, storage, HttpGitHubTransport())

    private val json = Json { ignoreUnknownKeys = true }
    private val authMutex = Mutex()
    private val sessionLock = Any()
    private val generation = AtomicLong()
    @Volatile private var pending: Pending? = null
    private data class Pending(val code: String, val authorization: GitHubAuthorization, var interval: Long, var nextPollAt: Long, val generation: Long)

    override fun isConfigured() = configuration.ready
    private fun requireConfiguration() {
        if (!isConfigured()) throw DevelopmentApiException(503, "GitHub setup is pending. Configure Pandora's GitHub App before connecting.")
    }
    private fun stored(): GitHubCredentials? = synchronized(sessionLock) {
        storage.credentials()?.takeIf { it.clientId == configuration.clientId && it.login.equals(configuration.login, ignoreCase = true) }
    }
    override fun hasSession() = isConfigured() && stored() != null
    override fun isPending() = pending != null
    override fun pendingAuthorization() = pending?.authorization
    override fun clearSession() = synchronized(sessionLock) {
        generation.incrementAndGet(); pending = null; storage.clear()
    }
    override suspend fun disconnect() { clearSession() }
    override fun selectedRepositories(login: String) = storage.selected(login)
    override fun selectRepositories(login: String, names: Set<String>) = storage.select(login, names)

    private suspend fun oauth(parameters: Map<String, String>): OAuthResponse {
        val response = transport.request("https://github.com/login/oauth/access_token", "POST", json.encodeToString(parameters + ("client_id" to configuration.clientId)), null)
        if (response.status !in 200..299 && response.status != 400) checkStatus(response)
        return json.decodeFromString(response.body)
    }
    override suspend fun connect(): GitHubAuthorization {
        requireConfiguration()
        val attempt = generation.incrementAndGet()
        val response = transport.request("https://github.com/login/device/code", "POST", json.encodeToString(mapOf("client_id" to configuration.clientId, "scope" to "offline_access")), null)
        checkStatus(response)
        val device = try { json.decodeFromString<DeviceCodeResponse>(response.body) }
        catch (_: SerializationException) { throw DevelopmentApiException(400, "GitHub could not start sign-in. Check the GitHub App client ID and enable device flow.") }
        require(device.verification_uri == "https://github.com/login/device" && device.expires_in in 1..3600 && device.interval in 1..60 && device.device_code.isNotBlank() && device.user_code.isNotBlank()) { "Unexpected GitHub authorization response" }
        val authorization = GitHubAuthorization(device.user_code, device.verification_uri, clock() + device.expires_in * 1000)
        synchronized(sessionLock) {
            if (generation.get() != attempt) throw CancellationException("Connection cancelled")
            pending = Pending(device.device_code, authorization, device.interval * 1000, clock() + device.interval * 1000, attempt)
        }
        return authorization
    }
    override suspend fun connection(): Boolean = authMutex.withLock {
        val attempt = pending ?: return@withLock hasSession()
        if (clock() >= attempt.authorization.expiresAt) throw DevelopmentApiException(401, "Connection expired. Connect GitHub again.")
        wait((attempt.nextPollAt - clock()).coerceAtLeast(0))
        if (pending !== attempt) throw CancellationException("Connection cancelled")
        if (clock() >= attempt.authorization.expiresAt) throw DevelopmentApiException(401, "Connection expired. Connect GitHub again.")
        val result = try {
            oauth(mapOf("device_code" to attempt.code, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
        } catch (error: IOException) {
            attempt.interval = (attempt.interval * 2).coerceAtMost(60_000)
            attempt.nextPollAt = clock() + attempt.interval
            throw error
        }
        when (result.error) {
            "authorization_pending" -> { attempt.nextPollAt = clock() + attempt.interval; false }
            "slow_down" -> { attempt.interval += 5_000; attempt.nextPollAt = clock() + attempt.interval; false }
            "access_denied" -> throw DevelopmentApiException(401, "GitHub authorization was declined. Connect again when ready.")
            "expired_token", "bad_verification_code" -> throw DevelopmentApiException(401, "Connection expired. Connect GitHub again.")
            null -> {
                val accessToken = result.access_token?.takeIf { it.isNotBlank() } ?: throw IOException("GitHub did not return an access token.")
                val profile = api<GitHubProfile>("/user", accessToken)
                if (!profile.login.equals(configuration.login, ignoreCase = true)) throw DevelopmentApiException(401, "Sign in with your configured GitHub account: @${configuration.login}.")
                save(attempt.generation, credentials(result, profile.login))
                synchronized(sessionLock) { if (pending === attempt) pending = null }
                true
            }
            else -> throw DevelopmentApiException(400, "GitHub could not authorize Pandora. Check the GitHub App client ID and device-flow setting.")
        }
    }
    private fun expires(seconds: Long?): Long = seconds?.let {
        require(it in 1..31_622_400) { "Invalid GitHub token expiration" }
        clock() + it * 1000
    } ?: Long.MAX_VALUE
    private fun credentials(response: OAuthResponse, login: String) = GitHubCredentials(
        response.access_token?.takeIf { it.isNotBlank() } ?: throw IOException("GitHub did not return an access token."),
        response.refresh_token, expires(response.expires_in), expires(response.refresh_token_expires_in), configuration.clientId, login,
    )
    private fun save(attempt: Long, credentials: GitHubCredentials) = synchronized(sessionLock) {
        if (generation.get() != attempt) throw CancellationException("Connection changed")
        storage.save(credentials)
    }
    private suspend fun token(): String = authMutex.withLock {
        requireConfiguration()
        val attempt = generation.get()
        val credentials = stored() ?: throw DevelopmentApiException(401, "Connect your GitHub account again.")
        if (credentials.expiresAt > clock() + 60_000) return@withLock credentials.accessToken
        val refresh = credentials.refreshToken
        if (refresh == null && credentials.expiresAt > clock()) return@withLock credentials.accessToken
        if (refresh == null || credentials.refreshExpiresAt <= clock()) throw DevelopmentApiException(401, "Your GitHub connection expired. Connect again.")
        val result = oauth(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh))
        if (result.error != null) throw DevelopmentApiException(401, "Your GitHub connection expired or was revoked. Connect again.")
        val updated = credentials(result, credentials.login)
        save(attempt, updated)
        updated.accessToken
    }
    private fun checkStatus(response: GitHubHttpResponse) {
        when (response.status) {
            in 200..299 -> Unit
            401 -> throw DevelopmentApiException(401, "Your GitHub connection was revoked or expired. Connect again.")
            403, 429 -> throw DevelopmentApiException(response.status, "GitHub denied access or its rate limit was reached. Check app access and try later.")
            404 -> throw DevelopmentApiException(404, "Repository unavailable. Check your GitHub App installation.")
            422 -> throw DevelopmentApiException(422, "GitHub rejected the issue. Check its title, labels and assignees.")
            else -> throw DevelopmentApiException(response.status, "GitHub is unavailable. Check your connection and retry.")
        }
    }
    private suspend inline fun <reified T> api(path: String, token: String): T {
        val response = transport.request("https://api.github.com$path", "GET", null, token)
        checkStatus(response)
        return json.decodeFromString(response.body)
    }
    private suspend inline fun <reified T> allPages(path: String, token: String): List<T> {
        val items = mutableListOf<T>()
        var page = 1
        do {
            val response = transport.request("https://api.github.com$path?per_page=100&page=$page", "GET", null, token)
            checkStatus(response)
            items += json.decodeFromString<List<T>>(response.body)
            page++
        } while (response.hasNext)
        return items
    }
    override suspend fun profile(): GitHubProfile {
        val profile = api<GitHubProfile>("/user", token())
        if (!profile.login.equals(configuration.login, ignoreCase = true)) throw DevelopmentApiException(401, "Connect your configured GitHub account again.")
        return profile
    }
    override suspend fun repositories(): GitHubRepositories {
        val token = token()
        val repositories = mutableMapOf<Long, GitHubRepository>()
        var page = 1
        do {
            val installations = transport.request("https://api.github.com/user/installations?per_page=100&page=$page", "GET", null, token)
            checkStatus(installations)
            for (installation in json.decodeFromString<InstallationsResponse>(installations.body).installations) {
                var repoPage = 1
                do {
                    val response = transport.request("https://api.github.com/user/installations/${installation.id}/repositories?per_page=100&page=$repoPage", "GET", null, token)
                    checkStatus(response)
                    for (repo in json.decodeFromString<RepositoriesResponse>(response.body).repositories) {
                        if (repo.owner.login.equals(configuration.login, ignoreCase = true) && repo.has_issues && !repo.archived) repositories[repo.id] = repo
                    }
                    repoPage++
                } while (response.hasNext)
            }
            page++
        } while (installations.hasNext)
        return GitHubRepositories(repositories.values.sortedBy { it.name.lowercase() }, configuration.installationUrl)
    }
    private fun repositoryPath(name: String): String {
        require(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid repository name" }
        return "/repos/${configuration.login}/$name"
    }
    override suspend fun issues(repo: String, page: Int): GitHubIssuePage {
        require(page > 0)
        val token = token()
        val path = repositoryPath(repo)
        val items = mutableListOf<GitHubIssue>()
        var cursor = page
        var next: Int? = null
        // Cursor counts upstream records, including PRs. Continue through sparse batches to fill the issue page.
        repeat(5) {
            val upstreamPage = (cursor - 1) / 100 + 1
            val response = transport.request("https://api.github.com$path/issues?state=open&sort=created&direction=desc&per_page=100&page=$upstreamPage", "GET", null, token)
            checkStatus(response)
            val batch = json.decodeFromString<List<IssueResponse>>(response.body)
            var consumed = (cursor - 1) % 100
            for (issue in batch.drop(consumed)) {
                consumed++
                if (issue.pull_request == null) items += issue.issue()
                if (items.size == 10) break
            }
            next = if (consumed < batch.size || response.hasNext) Math.addExact(Math.multiplyExact(upstreamPage - 1, 100), consumed + 1) else null
            if (items.size == 10 || next == null) return GitHubIssuePage(items, next)
            cursor = next
        }
        return GitHubIssuePage(items, next)
    }
    override suspend fun options(repo: String): GitHubIssueOptions {
        val token = token(); val path = repositoryPath(repo)
        return GitHubIssueOptions(allPages<GitHubLabel>("$path/labels", token), allPages<GitHubProfile>("$path/assignees", token))
    }
    override suspend fun create(repo: String, issue: NewGitHubIssue): GitHubIssue {
        val title = issue.title.trim()
        if (title.isEmpty() || title.codePointCount(0, title.length) > 256 || issue.body.toByteArray(Charsets.UTF_8).size > 65536 || issue.labels.size > 100 || issue.assignees.size > 10) throw DevelopmentApiException(422, "Enter a title up to 256 characters, a description up to 64 KB and at most 10 assignees.")
        val token = token()
        val path = repositoryPath(repo)
        // Never retry a POST: a network failure after sending may already have created the issue.
        try {
            val response = transport.request("https://api.github.com$path/issues", "POST", json.encodeToString(issue.copy(title = title)), token)
            if (response.status >= 500 || response.status in 300..399) throw IOException("Uncertain creation")
            checkStatus(response)
            return json.decodeFromString<GitHubIssue>(response.body)
        } catch (error: Exception) {
            if (error is CancellationException || error is DevelopmentApiException) throw error
            throw DevelopmentApiException(504, "Creation could not be confirmed. Check the repository on GitHub before retrying to avoid duplicates.")
        }
    }
}
