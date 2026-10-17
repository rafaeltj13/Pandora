package dev.rafaelm.pandora.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcomeIsVisibleWithoutANetwork() {
        compose.setContent { PandoraTheme { WelcomeScreen() } }
        compose.onNodeWithText("A small beginning. Room to grow.").assertIsDisplayed()
    }

    @Test
    fun modulesHintOpensSettings() {
        var opened = 0
        compose.setContent { PandoraTheme { WelcomeScreen(onOpenSettings = { opened++ }, showModulesHint = true) } }
        compose.onNodeWithTag("modules_hint_open_settings").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }
}
