package dev.rafaelm.pandora.ui

import dev.rafaelm.pandora.data.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevelopmentViewModelTest {
    private val repo = GitHubRepository(1, "pandora", "owner/pandora", true, "https://github.com/owner/pandora", GitHubOwner("owner"), true, false)
    private val issue = GitHubIssue(9, "Bug", "https://github.com/owner/pandora/issues/9")
    private inner class Fake : DevelopmentRepository {
        var session = true
        var pending = false
        var approved = false
        override fun isConfigured() = true
        override fun pendingAuthorization() = if (pending) GitHubAuthorization("ABCD-EFGH", "https://github.com/login/device", Long.MAX_VALUE) else null
        var selected = setOf("pandora")
        var profileError: Exception? = null
        var submissionError: Exception? = null
        var submissions = 0
        var submitted: NewGitHubIssue? = null
        var result: CompletableDeferred<GitHubIssue>? = null
        override fun hasSession() = session
        override fun isPending() = pending
        override suspend fun connect(): GitHubAuthorization { pending = true; return pendingAuthorization()!! }
        override suspend fun connection(): Boolean { delay(1_000); if (approved) { pending = false; session = true }; return !pending }
        override suspend fun profile(): GitHubProfile { profileError?.let { throw it }; return GitHubProfile("owner", "Owner", "https://avatars.githubusercontent.com/1", "https://github.com/owner") }
        override suspend fun repositories() = GitHubRepositories(listOf(repo), "https://github.com/apps/pandora/installations/new")
        override suspend fun issues(repo: String, page: Int) = if (page == 1) GitHubIssuePage(listOf(issue), 2) else GitHubIssuePage(listOf(issue, GitHubIssue(10, "Next", "https://github.com/owner/pandora/issues/10")), null)
        override suspend fun options(repo: String) = GitHubIssueOptions(listOf(GitHubLabel("bug", "ff0000")), listOf(profile()))
        override suspend fun create(repo: String, issue: NewGitHubIssue): GitHubIssue { submissions++; submitted = issue; submissionError?.let { throw it }; return result?.await() ?: this@DevelopmentViewModelTest.issue }
        override suspend fun disconnect() { clearSession() }
        override fun clearSession() { session = false; pending = false }
        override fun selectedRepositories(login: String) = selected
        override fun selectRepositories(login: String, names: Set<String>) { selected = names }
    }
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun `device authorization polls until GitHub approval before loading dashboard`() = runTest {
        val fake = Fake().apply { session = false }
        val model = DevelopmentViewModel(fake); advanceUntilIdle()
        model.connect(); runCurrent()
        assertTrue(model.state.value.connecting); assertTrue(fake.pending)
        assertNull(model.state.value.profile)
        assertEquals("ABCD-EFGH", model.state.value.authorization!!.userCode)
        fake.approved = true; advanceUntilIdle()
        assertFalse(fake.pending); assertFalse(model.state.value.connecting)
        assertEquals("owner", model.state.value.profile!!.login)
    }
    @Test fun `dashboard remembers selection and pagination deduplicates issues`() = runTest {
        val model = DevelopmentViewModel(Fake()); advanceUntilIdle()
        assertEquals(setOf("pandora"), model.state.value.selected)
        model.loadMore("pandora"); advanceUntilIdle()
        assertEquals(listOf(9L, 10L), model.state.value.issues["pandora"]!!.items.map { it.number })
        assertNull(model.state.value.issues["pandora"]!!.next_page)
    }
    @Test fun `leaving the form discards every field and picker selection`() = runTest {
        val model = DevelopmentViewModel(Fake()); advanceUntilIdle()
        model.newIssue(repo); advanceUntilIdle()
        model.edit(title = "Unsaved", body = "Description", labels = setOf("bug"), assignees = setOf("owner"))
        model.closeEditor(); model.newIssue(repo); advanceUntilIdle()
        assertEquals("", model.state.value.editor!!.title)
        assertEquals("", model.state.value.editor!!.body)
        assertTrue(model.state.value.editor!!.labels.isEmpty())
        assertTrue(model.state.value.editor!!.assignees.isEmpty())
    }
    @Test fun `failed creation keeps form contents and does not retry automatically`() = runTest {
        val fake = Fake().apply { submissionError = IOException("Disconnected") }
        val model = DevelopmentViewModel(fake); advanceUntilIdle()
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "Bug", labels = setOf("bug"))
        model.submit(); advanceUntilIdle()
        assertEquals(1, fake.submissions)
        assertEquals("Bug", model.state.value.editor!!.title)
        assertEquals(setOf("bug"), model.state.value.editor!!.labels)
        assertTrue(model.state.value.editor!!.error!!.contains("could not be confirmed"))
    }
    @Test fun `double submit sends once and success clears the form`() = runTest {
        val response = CompletableDeferred<GitHubIssue>()
        val fake = Fake().apply { result = response }
        val model = DevelopmentViewModel(fake); advanceUntilIdle()
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "  Bug  ", labels = setOf("bug"), assignees = setOf("owner"))
        repeat(3) { model.submit() }; runCurrent(); assertEquals(1, fake.submissions)
        assertEquals("Bug", fake.submitted!!.title); assertEquals(listOf("owner"), fake.submitted!!.assignees)
        response.complete(issue); advanceUntilIdle()
        assertNull(model.state.value.editor); assertEquals(issue, model.state.value.created)
    }
    @Test fun `offline dashboard prevents new issues and recovers with retry`() = runTest {
        val fake = Fake(); val model = DevelopmentViewModel(fake); advanceUntilIdle()
        fake.profileError = IOException("Offline"); model.refresh(); advanceUntilIdle()
        assertTrue(model.state.value.offline)
        model.newIssue(repo); assertNull(model.state.value.editor)
        fake.profileError = null; model.refresh(); advanceUntilIdle(); assertFalse(model.state.value.offline)
    }
    @Test fun `expired session returns to connect and clears local credentials`() = runTest {
        val fake = Fake().apply { profileError = DevelopmentApiException(401, "Reconnect") }
        val model = DevelopmentViewModel(fake); advanceUntilIdle()
        assertFalse(fake.session); assertNull(model.state.value.profile); assertEquals("Reconnect", model.state.value.error)
    }
    @Test fun `a new form is blocked while a discarded form is still submitting`() = runTest {
        val response = CompletableDeferred<GitHubIssue>(); val fake = Fake().apply { result = response }
        val model = DevelopmentViewModel(fake); advanceUntilIdle()
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "Bug"); model.submit(); runCurrent()
        model.closeEditor(); model.newIssue(repo); assertNull(model.state.value.editor)
        response.complete(issue); advanceUntilIdle(); assertEquals(issue, model.state.value.created)
    }
}
