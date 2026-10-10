package dev.rafaelm.pandora.data

import java.io.IOException
import kotlinx.serialization.Serializable

@Serializable data class GitHubProfile(val login: String, val name: String? = null, val avatar_url: String, val html_url: String)
@Serializable data class GitHubOwner(val login: String)
@Serializable data class GitHubRepository(val id: Long, val name: String, val full_name: String, val private: Boolean, val html_url: String, val owner: GitHubOwner, val has_issues: Boolean, val archived: Boolean)
@Serializable data class GitHubIssue(val number: Long, val title: String, val html_url: String)
data class GitHubIssuePage(val items: List<GitHubIssue>, val next_page: Int? = null)
@Serializable data class GitHubLabel(val name: String, val color: String)
data class GitHubIssueOptions(val labels: List<GitHubLabel>, val assignees: List<GitHubProfile>)
@Serializable data class NewGitHubIssue(val title: String, val body: String, val labels: List<String>, val assignees: List<String>)
data class GitHubRepositories(val repositories: List<GitHubRepository>, val installation_url: String)
data class GitHubAuthorization(val userCode: String, val verificationUrl: String, val expiresAt: Long)

class DevelopmentApiException(val status: Int, override val message: String) : IOException(message)

interface DevelopmentRepository {
    fun isConfigured(): Boolean
    fun hasSession(): Boolean
    fun isPending(): Boolean
    fun pendingAuthorization(): GitHubAuthorization?
    suspend fun connect(): GitHubAuthorization
    suspend fun connection(): Boolean
    suspend fun profile(): GitHubProfile
    suspend fun repositories(): GitHubRepositories
    suspend fun issues(repo: String, page: Int = 1): GitHubIssuePage
    suspend fun options(repo: String): GitHubIssueOptions
    suspend fun create(repo: String, issue: NewGitHubIssue): GitHubIssue
    suspend fun disconnect()
    fun clearSession()
    fun selectedRepositories(login: String): Set<String>
    fun selectRepositories(login: String, names: Set<String>)
}

@Serializable
data class GitHubCredentials(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val refreshExpiresAt: Long,
    val clientId: String,
    val login: String,
)

interface GitHubCredentialStore {
    fun credentials(): GitHubCredentials?
    fun save(credentials: GitHubCredentials)
    fun clear()
    fun selected(login: String): Set<String>
    fun select(login: String, names: Set<String>)
}
