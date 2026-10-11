package dev.rafaelm.pandora.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.rafaelm.pandora.data.ServerStatus
import org.junit.Rule
import org.junit.Test

class PandoraNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val welcome = WelcomeState.Ready(ServerStatus("Pandora", "Welcome to Pandora", "0.1.0"))

    private fun render() {
        compose.setContent {
            PandoraTheme { PandoraApp(welcome, onRetry = {}) }
        }
    }

    @Test
    fun bottomBarSwitchesBetweenAllPages() {
        render()
        compose.onNodeWithTag("navigation_home").assertIsSelected()
        compose.onNodeWithText("Welcome to Pandora").assertIsDisplayed()

        compose.onNodeWithTag("navigation_tasks").performClick().assertIsSelected()
        compose.onNodeWithText("Connect your calendar").assertIsDisplayed()

        compose.onNodeWithTag("navigation_development").performClick().assertIsSelected()
        compose.onNodeWithText("Connect GitHub").assertIsDisplayed()

        compose.onNodeWithTag("navigation_home").performClick().assertIsSelected()
        compose.onNodeWithText("Welcome to Pandora").assertIsDisplayed()
    }

    @Test
    fun headerOpensSettingsWithoutTheDock() {
        render()
        compose.onNodeWithTag("navigation_development").performClick()

        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithText("About Pandora").assertIsDisplayed()
        compose.onNodeWithTag("navigation_home").assertDoesNotExist()
        compose.onNodeWithTag("open_settings").assertDoesNotExist()

        compose.onNodeWithTag("settings_appearance").performClick()
        compose.onNodeWithTag("theme_system").assertIsSelected()
        compose.onNodeWithTag("appearance_back").performClick()
        compose.onNodeWithText("About Pandora").assertIsDisplayed()

        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("navigation_development").assertIsSelected()
        compose.onNodeWithText("Connect GitHub").assertIsDisplayed()
    }

    @Test
    fun dockAddButtonDoesNothingYet() {
        render()
        compose.onNodeWithTag("dock_add").assertIsDisplayed().performClick()

        compose.onNodeWithTag("navigation_home").assertIsSelected()
        compose.onNodeWithText("Welcome to Pandora").assertIsDisplayed()
    }

    @Test
    fun repeatedTabTapsDoNotPreventBackFromReturningHome() {
        render()
        repeat(3) { compose.onNodeWithTag("navigation_tasks").performClick() }
        repeat(3) { compose.onNodeWithTag("navigation_development").performClick() }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("navigation_home").assertIsSelected()
        compose.onNodeWithText("Welcome to Pandora").assertIsDisplayed()
    }

    @Test
    fun selectedPageIsRestoredAfterStateRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PandoraTheme { PandoraApp(welcome, onRetry = {}) }
        }
        compose.onNodeWithTag("navigation_tasks").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("navigation_tasks").assertIsSelected()
        compose.onNodeWithText("Connect your calendar").assertIsDisplayed()
    }
}
