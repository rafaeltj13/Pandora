package dev.rafaelm.pandora.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.rafaelm.pandora.data.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcomeFromServerIsVisible() {
        compose.setContent {
            PandoraTheme {
                WelcomeScreen(WelcomeState.Ready(ServerStatus("Pandora", "Welcome to Pandora", "0.1.0"))) {}
            }
        }
        compose.onNodeWithText("Welcome to Pandora").assertIsDisplayed()
        compose.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun errorOffersAnActionableRetry() {
        var retries = 0
        compose.setContent {
            PandoraTheme { WelcomeScreen(WelcomeState.Error) { retries++ } }
        }
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }
}
