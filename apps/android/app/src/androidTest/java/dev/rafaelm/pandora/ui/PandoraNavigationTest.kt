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
import dev.rafaelm.pandora.data.GitHubOwner
import dev.rafaelm.pandora.data.GitHubProfile
import dev.rafaelm.pandora.data.GitHubRepository
import dev.rafaelm.pandora.data.Module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PandoraNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render() {
        compose.setContent {
            PandoraTheme { PandoraApp() }
        }
    }

    @Test
    fun bottomBarSwitchesBetweenAllPages() {
        render()
        compose.onNodeWithTag("navigation_home").assertIsSelected()
        compose.onNodeWithText("A small beginning. Room to grow.").assertIsDisplayed()

        compose.onNodeWithTag("navigation_tasks").performClick().assertIsSelected()
        compose.onNodeWithText("Connect your calendar").assertIsDisplayed()

        compose.onNodeWithTag("navigation_development").performClick().assertIsSelected()
        compose.onNodeWithText("Connect GitHub").assertIsDisplayed()

        compose.onNodeWithTag("navigation_home").performClick().assertIsSelected()
        compose.onNodeWithText("A small beginning. Room to grow.").assertIsDisplayed()
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
    fun dockAddSheetStartsATaskOnTheTasksPage() {
        var started = false
        compose.setContent {
            PandoraTheme { PandoraApp(tasks = TasksState(access = true, loaded = true),
                tasksActions = TasksActions(newTask = { started = true })) }
        }
        compose.onNodeWithTag("dock_add").performClick()
        compose.onNodeWithTag("create_option_issue").assertIsDisplayed()
        compose.onNodeWithTag("create_option_task").performClick()

        compose.onNodeWithTag("create_sheet").assertDoesNotExist()
        compose.onNodeWithTag("navigation_tasks").assertIsSelected()
        compose.runOnIdle { assertTrue(started) }
    }

    @Test
    fun dockAddSheetPicksTheRepositoryForANewIssue() {
        val repo = GitHubRepository(1, "pandora", "owner/pandora", true, "https://github.com/owner/pandora", GitHubOwner("owner"), true, false)
        var chosen: GitHubRepository? = null
        compose.setContent {
            PandoraTheme { PandoraApp(modules = setOf(Module.DEVELOPMENT),
                development = DevelopmentState(profile = GitHubProfile("owner", "Owner", "", "https://github.com/owner"), repositories = listOf(repo)),
                developmentActions = DevelopmentActions(newIssue = { chosen = it })) }
        }
        compose.onNodeWithTag("dock_add").performClick()
        compose.onNodeWithTag("create_option_task").assertDoesNotExist()
        compose.onNodeWithTag("create_option_issue").performClick()
        compose.onNodeWithTag("create_repository:pandora").performClick()

        compose.onNodeWithTag("navigation_development").assertIsSelected()
        compose.runOnIdle { assertEquals(repo, chosen) }
    }

    @Test
    fun dockHidesAddWithoutModules() {
        compose.setContent { PandoraTheme { PandoraApp(modules = emptySet()) } }
        compose.onNodeWithTag("navigation_home").assertIsSelected()
        compose.onNodeWithTag("dock_add").assertDoesNotExist()
    }

    @Test
    fun repeatedTabTapsDoNotPreventBackFromReturningHome() {
        render()
        repeat(3) { compose.onNodeWithTag("navigation_tasks").performClick() }
        repeat(3) { compose.onNodeWithTag("navigation_development").performClick() }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("navigation_home").assertIsSelected()
        compose.onNodeWithText("A small beginning. Room to grow.").assertIsDisplayed()
    }

    @Test
    fun selectedPageIsRestoredAfterStateRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PandoraTheme { PandoraApp() }
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
                PandoraApp(modules = modules,
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
        compose.onNodeWithText("A small beginning. Room to grow.").assertIsDisplayed()
        compose.onNodeWithTag("navigation_tasks").assertDoesNotExist()
        compose.onNodeWithTag("navigation_development").assertExists()
    }
}
