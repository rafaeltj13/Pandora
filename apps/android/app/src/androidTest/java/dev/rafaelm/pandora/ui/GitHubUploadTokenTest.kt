package dev.rafaelm.pandora.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import dev.rafaelm.pandora.data.Module
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GitHubUploadTokenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun savingFromSettingsShowsTheSavedTokenView() {
        var state by mutableStateOf(DevelopmentState(sessionAvailable = true))
        var saved: String? = null
        compose.setContent { PandoraTheme { PandoraApp(development = state, modules = setOf(Module.DEVELOPMENT),
            developmentActions = DevelopmentActions(setUploadToken = {
                saved = it
                state = state.copy(uploadTokenConfigured = true, uploadTokenVersion = state.uploadTokenVersion + 1)
            })) } }
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("settings_attachment_token").performScrollTo().performClick()
        compose.onNodeWithTag("attachment_token_input")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextInput("github_pat_example")
        compose.onNodeWithTag("save_attachment_token").performClick()
        compose.runOnIdle { assertEquals("github_pat_example", saved) }
        compose.onNodeWithTag("attachment_token_input").assertDoesNotExist()
        compose.onNodeWithText("Token saved").assertIsDisplayed()
        compose.onNodeWithText("Create token on GitHub").assertDoesNotExist()
    }

    @Test fun removingTheAttachmentTokenDoesNotDisconnectGitHub() {
        var state by mutableStateOf(DevelopmentState(sessionAvailable = true, uploadTokenConfigured = true))
        var removed = false
        compose.setContent { PandoraTheme { GitHubUploadTokenDialog(state,
            DevelopmentActions(clearUploadToken = {
                removed = true
                state = state.copy(uploadTokenConfigured = false, uploadTokenVersion = state.uploadTokenVersion + 1)
            }), {}, {}) } }
        compose.onNodeWithTag("remove_attachment_token").performScrollTo().performClick()
        compose.onNodeWithText("No attachment token saved.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(removed); assertTrue(state.sessionAvailable) }
    }

    @Test fun tokenEntryDoesNotSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PandoraTheme { GitHubUploadTokenDialog(DevelopmentState(sessionAvailable = true), DevelopmentActions(), {}, {}) } }
        compose.onNodeWithTag("attachment_token_input").performTextInput("github_pat_unsaved")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("attachment_token_input")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
    @Test fun savedTokenHasASeparateReplaceActionAndCancelKeepsIt() {
        compose.setContent { PandoraTheme { GitHubUploadTokenDialog(
            DevelopmentState(sessionAvailable = true, uploadTokenConfigured = true), DevelopmentActions(), {}, {}) } }
        compose.onNodeWithText("Token saved").assertIsDisplayed()
        compose.onNodeWithTag("attachment_token_input").assertDoesNotExist()
        compose.onNodeWithText("Create token on GitHub").assertDoesNotExist()
        compose.onNodeWithText("Replace token").performClick()
        compose.onNodeWithTag("attachment_token_input").performTextInput("github_pat_replacement")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Token saved").assertIsDisplayed()
        compose.onNodeWithTag("attachment_token_input").assertDoesNotExist()
    }

}
