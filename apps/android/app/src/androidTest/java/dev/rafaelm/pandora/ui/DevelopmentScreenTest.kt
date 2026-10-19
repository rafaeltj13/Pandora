package dev.rafaelm.pandora.ui

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.rafaelm.pandora.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DevelopmentScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val repo = GitHubRepository(1, "pandora", "owner/pandora", true, "https://github.com/owner/pandora", GitHubOwner("owner"), true, false)
    private val profile = GitHubProfile("owner", "Owner", "", "https://github.com/owner")
    @Test fun disconnectedScreenOffersGitHubConnection() {
        var connected = false
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(), DevelopmentActions(connect = { connected = true }), {}) } }
        compose.onNodeWithText("Connect GitHub").performClick()
        compose.runOnIdle { assertTrue(connected) }
    }
    @Test fun connectingShowsTheGitHubDeviceCodeAndOpensItsVerificationPage() {
        var url: String? = null
        val authorization = GitHubAuthorization("ABCD-EFGH", "https://github.com/login/device", Long.MAX_VALUE)
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(connecting = true, authorization = authorization), DevelopmentActions(), { url = it }) } }
        compose.onNodeWithText("ABCD-EFGH").assertIsDisplayed()
        compose.onNodeWithText("Open GitHub").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("https://github.com/login/device", url) }
        compose.onNodeWithText("Confirm connection").assertDoesNotExist()
    }
    @Test fun missingConfigurationShowsSetupStateAndDisablesConnect() {
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(configurationRequired = true), DevelopmentActions(), {}) } }
        compose.onNodeWithText("Connect GitHub").assertIsNotEnabled()
        compose.onNodeWithText("GitHub setup is pending.", substring = true).assertIsDisplayed()
    }
    @Test fun copyGitHubCodeCopiesTheDisplayedCode() {
        val authorization = GitHubAuthorization("ABCD-EFGH", "https://github.com/login/device", Long.MAX_VALUE)
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(connecting = true, authorization = authorization), DevelopmentActions(), {}) } }
        // Android only permits clipboard reads while this activity has window focus.
        compose.waitUntil(timeoutMillis = 5_000) { compose.activity.hasWindowFocus() }
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        compose.runOnIdle {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }
        compose.onNodeWithContentDescription("Copy GitHub code").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "ABCD-EFGH"
        }
    }
    @Test fun dashboardGroupsIssuesByRepositoryWithoutCreatingThere() {
        val state = DevelopmentState(profile = profile, repositories = listOf(repo), selected = setOf(repo.name), issues = mapOf(repo.name to GitHubIssuePage(listOf(GitHubIssue(7, "Fix a bug", "https://github.com/owner/pandora/issues/7")), null)))
        compose.setContent { PandoraTheme { DevelopmentScreen(state, DevelopmentActions(), {}) } }
        compose.onNodeWithText("@owner").assertIsDisplayed()
        compose.onNodeWithText("pandora").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fix a bug").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("New issue").assertDoesNotExist()
        captureReview("development-dashboard")
    }
    @Test fun offlineShowsEmptyStateWithoutIssueCreation() {
        val state = DevelopmentState(profile = profile, repositories = listOf(repo), selected = setOf(repo.name), offline = true)
        compose.setContent { PandoraTheme { DevelopmentScreen(state, DevelopmentActions(), {}) } }
        compose.onNodeWithText("GitHub is unavailable").assertIsDisplayed()
        compose.onNodeWithText("New issue").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }
    @Test fun issueFormRequiresTitleAndHasRepositoryPickersAndDiscard() {
        var discarded = false
        val editor = IssueEditor(repo, options = GitHubIssueOptions(listOf(GitHubLabel("bug", "ff0000")), listOf(profile)), loading = false)
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(editor = editor), DevelopmentActions(closeEditor = { discarded = true }), {}) } }
        compose.onNodeWithTag("create_issue").assertIsNotEnabled()
        compose.onNodeWithText("Labels").performScrollTo().performClick()
        compose.onNodeWithText("bug").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.runOnIdle { assertTrue(discarded) }
    }
    @Test fun imageUploadShowsProgressAndBlocksCreation() {
        val editor = IssueEditor(repo, title = "Bug", options = GitHubIssueOptions(emptyList(), emptyList()),
            loading = false, uploadingImage = true, imageError = "Upload failed")
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(editor = editor), DevelopmentActions(), {}) } }
        compose.onNodeWithTag("add_issue_image").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Upload failed").assertIsDisplayed()
        compose.onNodeWithTag("create_issue").assertIsNotEnabled()
    }
    @Test fun attachedImageCanBeRemovedBeforeCreation() {
        var removed: String? = null
        val image = IssueImage("screen.png", "https://github.com/user-attachments/assets/image", previewUri = previewFixture())
        val editor = IssueEditor(repo, title = "Improve the sign-in screen", body = "The error message should explain how to recover.", images = listOf(image), options = GitHubIssueOptions(emptyList(), emptyList()), loading = false)
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(editor = editor, uploadTokenConfigured = true),
            DevelopmentActions(removeImage = { removed = it }), {}) } }
        compose.onNodeWithContentDescription("Remove screen.png").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(image.url, removed) }
        compose.onNodeWithTag("create_issue").assertIsDisplayed()
        compose.onNodeWithText("Manage attachment token").assertDoesNotExist()
        captureReview("issue-form")
    }

    @Test fun gallerySelectionPassesOnlyTheSelectedUriToTheUploadAction() {
        var selected: String? = null
        val registry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I,
                options: androidx.core.app.ActivityOptionsCompat?) {
                val intent = contract.createIntent(compose.activity, input)
                assertTrue(intent.type == "image/*")
                dispatchResult(requestCode, android.app.Activity.RESULT_OK,
                    android.content.Intent().setData(android.net.Uri.parse("content://photos/selected")))
            }
        }
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {
                PandoraTheme { DevelopmentScreen(DevelopmentState(editor = IssueEditor(repo), uploadTokenConfigured = true),
                    DevelopmentActions(addImage = { selected = it }), {}) }
            }
        }
        compose.onNodeWithTag("add_issue_image").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("content://photos/selected", selected) }
    }

    @Test fun imagePickerIsDisabledUntilAnAttachmentTokenIsConfigured() {
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(editor = IssueEditor(repo), sessionAvailable = true),
            DevelopmentActions(), {}) } }
        compose.onNodeWithTag("add_issue_image").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Set up image uploads").performScrollTo().performClick()
        compose.onNodeWithTag("attachment_token_input").assertIsDisplayed()
    }

    @Test fun accountActionsAreAvailableWithoutCrowdingTheDashboard() {
        var disconnected = false
        var opened: String? = null
        val state = DevelopmentState(profile = profile, repositories = listOf(repo), selected = setOf(repo.name),
            installationUrl = "https://github.com/apps/pandora/installations/new")
        compose.setContent { PandoraTheme { DevelopmentScreen(state,
            DevelopmentActions(disconnect = { disconnected = true }), { opened = it }) } }
        compose.onNodeWithText("Disconnect").assertDoesNotExist()
        compose.onNodeWithTag("development_account").performClick()
        compose.onNodeWithText("Manage access").performClick()
        compose.runOnIdle { assertEquals(state.installationUrl, opened) }
        compose.onNodeWithTag("development_account").performClick()
        compose.onNodeWithText("Disconnect").performClick()
        compose.runOnIdle { assertTrue(disconnected) }
    }
    @Test fun repositorySelectionAndCreationFeedbackHaveClearActions() {
        var dismissed = false
        val state = DevelopmentState(profile = profile, repositories = listOf(repo), selected = setOf(repo.name),
            created = GitHubIssue(7, "Fix a bug", "https://github.com/owner/pandora/issues/7"))
        compose.setContent { PandoraTheme { DevelopmentScreen(state,
            DevelopmentActions(dismissCreated = { dismissed = true }), {}) } }
        compose.onNodeWithContentDescription("Dismiss creation message").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
        compose.onNodeWithTag("select_repositories").performClick()
        compose.onNodeWithText("Apply").assertIsDisplayed()
    }

    @Test fun createActionStaysVisibleWhileEditingTheDescription() {
        val editor = IssueEditor(repo, title = "Bug", options = GitHubIssueOptions(emptyList(), emptyList()), loading = false)
        compose.setContent { PandoraTheme { DevelopmentScreen(DevelopmentState(editor = editor, uploadTokenConfigured = true), DevelopmentActions(), {}) } }
        compose.waitUntil(timeoutMillis = 5_000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("issue_body").performClick().performTextInput("Steps to reproduce")
        compose.runOnUiThread {
            androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithTag("create_issue").assertIsDisplayed().assertIsEnabled()
        captureReview("issue-form-keyboard")
    }

    private fun previewFixture(): String {
        val bitmap = android.graphics.Bitmap.createBitmap(160, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(49, 96, 130))
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRoundRect(20f, 20f, 140f, 80f, 8f, 8f, paint)
        paint.color = android.graphics.Color.rgb(49, 96, 130)
        canvas.drawRect(32f, 34f, 100f, 42f, paint)
        canvas.drawRect(32f, 52f, 120f, 60f, paint)
        val file = java.io.File(compose.activity.cacheDir, "attachment-preview.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return file.toURI().toString()
    }

    private fun captureReview(name: String) {
        val arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if (arguments.getString("pandora.captureScreenshots") != "true" || android.os.Build.VERSION.SDK_INT < 29) return
        val bitmap = if (name.endsWith("keyboard")) {
            checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        } else compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = compose.activity.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PandoraUX")
        }
        val uri = checkNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

}
