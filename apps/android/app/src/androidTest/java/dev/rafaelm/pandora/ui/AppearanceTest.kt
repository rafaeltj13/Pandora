package dev.rafaelm.pandora.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafaelm.pandora.data.AppearanceRepository
import dev.rafaelm.pandora.data.ThemeMode
import dev.rafaelm.pandora.data.CatTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppearanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectionChangesThemePersistsAndPreservesNavigation() {
        val name = "appearance_test_${UUID.randomUUID()}"
        val repository = AppearanceRepository(compose.activity, name)
        val model = AppearanceViewModel(repository)
        val systemDark = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        var background = Color.Unspecified
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (systemDark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    this.fontScale = fontScale.value
                }
                CompositionLocalProvider(
                    LocalConfiguration provides configuration,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
                ) {
                    val appearance by model.appearance.collectAsStateWithLifecycle()
                    PandoraTheme(darkTheme = appearance.mode.isDark(),
                        theme = if (appearance.mode.isDark()) appearance.darkTheme else appearance.lightTheme) {
                        val color = MaterialTheme.colorScheme.background
                        SideEffect { background = color }
                        PandoraApp(WelcomeState.Error, {}, appearance = appearance, onThemeModeChange = model::setMode,
                            onThemeChange = model::setTheme, onBothThemesChange = model::setBothThemes)
                    }
                }
            }
            compose.onNodeWithTag("navigation_settings").performClick()
            compose.onNodeWithTag("settings_appearance").performClick()
            compose.onNodeWithTag("theme_system").assertIsSelected()
            compose.runOnIdle { assertTrue(background.luminance() > 0.8f) }

            compose.onNodeWithTag("theme_dark").performClick().assertIsSelected()
            compose.runOnIdle {
                assertTrue(background.luminance() < 0.1f)
                assertEquals(ThemeMode.DARK, AppearanceRepository(compose.activity, name).appearance.value.mode)
                systemDark.value = true
            }
            compose.onNodeWithTag("theme_light").performClick().assertIsSelected()
            compose.runOnIdle {
                assertTrue(background.luminance() > 0.8f)
                assertEquals(ThemeMode.LIGHT, AppearanceRepository(compose.activity, name).appearance.value.mode)
            }

            compose.onNodeWithTag("palette_otto").performScrollTo().performClick()
            compose.onNodeWithTag("palette_otto_light").assertIsSelected()
            compose.onNodeWithTag("palette_otto_dark").assertIsSelected()
            compose.onNodeWithTag("palette_pandora_dark").performClick().assertIsSelected()
            compose.onNodeWithTag("palette_otto_light").assertIsSelected()
            compose.runOnIdle {
                val saved = AppearanceRepository(compose.activity, name).appearance.value
                assertEquals(CatTheme.OTTO, saved.lightTheme)
                assertEquals(CatTheme.PANDORA, saved.darkTheme)
                assertEquals(ThemeMode.LIGHT, saved.mode)
            }

            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("navigation_settings").assertDoesNotExist()
            compose.onNodeWithTag("theme_light").assertIsSelected()

            compose.onNodeWithTag("theme_system").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle {
                assertTrue(background.luminance() < 0.1f)
                assertEquals(ThemeMode.SYSTEM, AppearanceRepository(compose.activity, name).appearance.value.mode)
                systemDark.value = false
            }
            compose.runOnIdle {
                assertTrue(background.luminance() > 0.8f)
                fontScale.value = 2f
            }
            compose.onNodeWithTag("theme_dark").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            compose.onNodeWithTag("appearance_back").performClick()
            compose.onNodeWithTag("settings_appearance").assertIsDisplayed()
        } finally {
            compose.activity.deleteSharedPreferences(name)
        }
    }
}
