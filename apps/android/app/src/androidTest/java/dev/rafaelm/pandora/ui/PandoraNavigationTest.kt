package dev.rafaelm.pandora.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rafaelm.pandora.data.Module
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

    @Test
    fun moduleSwitchesShowAndHideTheirPagesAndSettings() {
        var modules by mutableStateOf(emptySet<Module>())
        compose.setContent {
            PandoraTheme {
                PandoraApp(welcome, onRetry = {}, modules = modules,
                    onModuleChange = { module, on -> modules = if (on) modules + module else modules - module })
            }
        }
        compose.onNodeWithTag("navigation_tasks").assertDoesNotExist()
        compose.onNodeWithTag("navigation_development").assertDoesNotExist()

        compose.onNodeWithTag("modules_hint_open_settings").performClick()
        compose.onNodeWithTag("settings_default_calendar").assertDoesNotExist()
        compose.onNodeWithTag("module_tasks").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("settings_default_calendar").assertExists()
        compose.onNodeWithTag("module_development").performClick().assertIsOn()
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("modules_hint_open_settings").assertDoesNotExist()

        compose.onNodeWithTag("navigation_tasks").performClick().assertIsSelected()
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("module_tasks").performClick().assertIsOff()
        compose.onNodeWithTag("settings_default_calendar").assertDoesNotExist()
        compose.onNodeWithTag("settings_back").performClick()

        compose.onNodeWithTag("navigation_home").assertIsSelected()
        compose.onNodeWithText("Welcome to Pandora").assertIsDisplayed()
        compose.onNodeWithTag("navigation_tasks").assertDoesNotExist()
        compose.onNodeWithTag("navigation_development").assertExists()
    }
}
