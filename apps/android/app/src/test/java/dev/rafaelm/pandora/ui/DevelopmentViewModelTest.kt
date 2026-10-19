package dev.rafaelm.pandora.ui

import dev.rafaelm.pandora.data.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevelopmentViewModelTest {
    private val repo = GitHubRepository(1, "pandora", "owner/pandora", true, "https://github.com/owner/pandora", GitHubOwner("owner"), true, false)
    private val issue = GitHubIssue(9, "Bug", "https://github.com/owner/pandora/issues/9")
    private inner class Fake : DevelopmentRepository {
        var attachmentConfigured = true
        override fun hasUploadToken() = attachmentConfigured
        override fun setUploadToken(token: String) {
            require(token.startsWith("github_pat_"))
            attachmentConfigured = true
        }
        override fun clearUploadToken() { attachmentConfigured = false }
        var uploadResult: CompletableDeferred<String>? = null
        var uploadError: Exception? = null
        var uploads = 0
        override suspend fun uploadImage(repo: Long, image: GitHubImage): String {
            uploads++; uploadError?.let { throw it }; return uploadResult?.await() ?: "https://github.com/user-attachments/assets/image"
        }
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
        var profiles = 0
        override suspend fun profile(): GitHubProfile { profiles++; profileError?.let { throw it }; return GitHubProfile("owner", "Owner", "https://avatars.githubusercontent.com/1", "https://github.com/owner") }
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
    @Test fun `a new model stays idle until its module asks it to refresh`() = runTest {
        val fake = Fake(); DevelopmentViewModel(fake); advanceUntilIdle()
        assertEquals(0, fake.profiles)
    }
    @Test fun `stopping cancels loading and drops the open form`() = runTest {
        val fake = Fake(); val model = DevelopmentViewModel(fake).apply { refresh() }; advanceUntilIdle()
        model.newIssue(repo); advanceUntilIdle()
        model.stop(); assertNull(model.state.value.editor)

        fake.session = false; model.connect(); runCurrent(); assertTrue(model.state.value.connecting)
        model.stop(); advanceUntilIdle()
        assertFalse(model.state.value.connecting); assertTrue(fake.pending)
    }
    @Test fun `device authorization polls until GitHub approval before loading dashboard`() = runTest {
        val fake = Fake().apply { session = false }
        val model = DevelopmentViewModel(fake).apply { refresh() }; advanceUntilIdle()
        model.connect(); runCurrent()
        assertTrue(model.state.value.connecting); assertTrue(fake.pending)
        assertNull(model.state.value.profile)
        assertEquals("ABCD-EFGH", model.state.value.authorization!!.userCode)
        fake.approved = true; advanceUntilIdle()
        assertFalse(fake.pending); assertFalse(model.state.value.connecting)
        assertEquals("owner", model.state.value.profile!!.login)
    }
    @Test fun `dashboard remembers selection and pagination deduplicates issues`() = runTest {
        val model = DevelopmentViewModel(Fake()).apply { refresh() }; advanceUntilIdle()
        assertEquals(setOf("pandora"), model.state.value.selected)
        model.loadMore("pandora"); advanceUntilIdle()
        assertEquals(listOf(9L, 10L), model.state.value.issues["pandora"]!!.items.map { it.number })
        assertNull(model.state.value.issues["pandora"]!!.next_page)
    }
    @Test fun `leaving the form discards every field and picker selection`() = runTest {
        val model = DevelopmentViewModel(Fake()).apply { refresh() }; advanceUntilIdle()
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
        val model = DevelopmentViewModel(fake).apply { refresh() }; advanceUntilIdle()
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
        val model = DevelopmentViewModel(fake).apply { refresh() }; advanceUntilIdle()
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "  Bug  ", labels = setOf("bug"), assignees = setOf("owner"))
        repeat(3) { model.submit() }; runCurrent(); assertEquals(1, fake.submissions)
        assertEquals("Bug", fake.submitted!!.title); assertEquals(listOf("owner"), fake.submitted!!.assignees)
        response.complete(issue); advanceUntilIdle()
        assertNull(model.state.value.editor); assertEquals(issue, model.state.value.created)
    }
    @Test fun `offline dashboard prevents new issues and recovers with retry`() = runTest {
        val fake = Fake(); val model = DevelopmentViewModel(fake).apply { refresh() }; advanceUntilIdle()
        fake.profileError = IOException("Offline"); model.refresh(); advanceUntilIdle()
        assertTrue(model.state.value.offline)
        model.newIssue(repo); assertNull(model.state.value.editor)
        fake.profileError = null; model.refresh(); advanceUntilIdle(); assertFalse(model.state.value.offline)
    }
    @Test fun `expired session returns to connect and clears local credentials`() = runTest {
        val fake = Fake().apply { profileError = DevelopmentApiException(401, "Reconnect") }
        val model = DevelopmentViewModel(fake).apply { refresh() }; advanceUntilIdle()
        assertFalse(fake.session); assertNull(model.state.value.profile); assertEquals("Reconnect", model.state.value.error)
    }
    @Test fun `a new form is blocked while a discarded form is still submitting`() = runTest {
        val response = CompletableDeferred<GitHubIssue>(); val fake = Fake().apply { result = response }
        val model = DevelopmentViewModel(fake).apply { refresh() }; advanceUntilIdle()
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "Bug"); model.submit(); runCurrent()
        model.closeEditor(); model.newIssue(repo); assertNull(model.state.value.editor)
        response.complete(issue); advanceUntilIdle(); assertEquals(issue, model.state.value.created)
    }
    @Test fun `upload blocks submission then includes image Markdown without reupload on retry`() = runTest {
        val upload = CompletableDeferred<String>()
        val fake = Fake().apply { uploadResult = upload; submissionError = IOException("Offline") }
        val model = DevelopmentViewModel(fake) { GitHubImage("screen[1].png", "image/png", byteArrayOf(1)) }
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "Bug", body = "Steps")
        model.addImage("content://image"); runCurrent()
        model.submit(); assertEquals(0, fake.submissions)
        assertTrue(model.state.value.editor!!.uploadingImage)
        upload.complete("https://github.com/user-attachments/assets/image"); advanceUntilIdle()
        assertEquals("content://image", model.state.value.editor!!.images.single().previewUri)
        model.submit(); advanceUntilIdle(); model.submit(); advanceUntilIdle()
        assertEquals(1, fake.uploads)
        assertEquals("Steps\n\n![screen\\[1\\].png](https://github.com/user-attachments/assets/image)", fake.submitted!!.body)
        model.removeImage("https://github.com/user-attachments/assets/image")
        assertTrue(model.state.value.editor!!.images.isEmpty())
    }
    @Test fun `image upload failure preserves draft and permits retry`() = runTest {
        val fake = Fake().apply { uploadError = IOException("Upload denied") }
        val model = DevelopmentViewModel(fake) { GitHubImage("screen.png", "image/png", byteArrayOf(1)) }
        model.newIssue(repo); advanceUntilIdle(); model.edit(body = "Keep this")
        model.addImage("content://image"); advanceUntilIdle()
        assertEquals("Keep this", model.state.value.editor!!.body)
        assertEquals("Upload denied", model.state.value.editor!!.imageError)
        assertFalse(model.state.value.editor!!.uploadingImage)
        fake.uploadError = null; model.addImage("content://image"); advanceUntilIdle()
        assertEquals(1, model.state.value.editor!!.images.size)
        assertNull(model.state.value.editor!!.imageError)
    }
    @Test fun `discarding an uploading form cannot attach to a later form`() = runTest {
        val upload = CompletableDeferred<String>(); val fake = Fake().apply { uploadResult = upload }
        val model = DevelopmentViewModel(fake) { GitHubImage("screen.png", "image/png", byteArrayOf(1)) }
        model.newIssue(repo); advanceUntilIdle(); model.addImage("content://image"); runCurrent()
        model.closeEditor(); model.newIssue(repo); runCurrent()
        upload.complete("https://github.com/user-attachments/assets/image"); advanceUntilIdle()
        assertTrue(model.state.value.editor!!.images.isEmpty())
        assertFalse(model.state.value.editor!!.uploadingImage)
    }

    @Test fun `saving and removing an attachment token updates presence without exposing the secret`() = runTest {
        val fake = Fake().apply { attachmentConfigured = false }
        val model = DevelopmentViewModel(fake)
        assertFalse(model.state.value.uploadTokenConfigured)
        model.setUploadToken("github_pat_example")
        val saved = model.state.first { !it.uploadTokenSaving }
        assertTrue(saved.uploadTokenConfigured)
        assertFalse(saved.toString().contains("github_pat_example"))
        model.clearUploadToken()
        val removed = model.state.first { !it.uploadTokenSaving }
        assertFalse(removed.uploadTokenConfigured)
        assertTrue(fake.session)
    }
    @Test fun `invalid attachment token keeps the existing token and reports a safe error`() = runTest {
        val model = DevelopmentViewModel(Fake())
        model.setUploadToken("secret-that-is-not-a-pat")
        val state = model.state.first { !it.uploadTokenSaving }
        assertTrue(state.uploadTokenConfigured)
        assertTrue(state.uploadTokenError!!.contains("fine-grained"))
        assertFalse(state.toString().contains("secret-that-is-not-a-pat"))
    }
    @Test fun `missing attachment token blocks image reading and preserves the issue draft`() = runTest {
        val fake = Fake().apply { attachmentConfigured = false }
        val model = DevelopmentViewModel(fake) { error("Must not read image") }
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "Keep this")
        model.addImage("content://image"); advanceUntilIdle()
        assertEquals(0, fake.uploads)
        assertEquals("Keep this", model.state.value.editor!!.title)
        assertTrue(model.state.value.editor!!.imageError!!.contains("attachment token"))
    }
    @Test fun `expired attachment token leaves the GitHub App session and draft intact`() = runTest {
        val fake = Fake().apply { uploadError = DevelopmentApiException(401, "Attachment token expired") }
        val model = DevelopmentViewModel(fake) { GitHubImage("screen.png", "image/png", byteArrayOf(1)) }
        model.newIssue(repo); advanceUntilIdle(); model.edit(title = "Keep this")
        model.addImage("content://image"); advanceUntilIdle()
        assertTrue(fake.session)
        assertEquals("Keep this", model.state.value.editor!!.title)
        assertEquals("Attachment token expired", model.state.value.editor!!.imageError)
    }

}
