package dev.rafaelm.pandora.ui

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
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
        compose.onNodeWithContentDescription("Copy GitHub code").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals("ABCD-EFGH", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }
    @Test fun dashboardGroupsIssuesByRepositoryWithoutCreatingThere() {
        val state = DevelopmentState(profile = profile, repositories = listOf(repo), selected = setOf(repo.name), issues = mapOf(repo.name to GitHubIssuePage(listOf(GitHubIssue(7, "Fix a bug", "https://github.com/owner/pandora/issues/7")), null)))
        compose.setContent { PandoraTheme { DevelopmentScreen(state, DevelopmentActions(), {}) } }
        compose.onNodeWithText("@owner").assertIsDisplayed()
        compose.onNodeWithText("owner/pandora").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fix a bug").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("New issue").assertDoesNotExist()
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
        compose.onNodeWithTag("create_issue").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Labels: None").performScrollTo().performClick()
        compose.onNodeWithText("bug").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(discarded) }
    }
}
