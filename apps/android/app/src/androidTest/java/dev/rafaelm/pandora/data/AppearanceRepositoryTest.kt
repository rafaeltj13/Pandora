package dev.rafaelm.pandora.data

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceRepositoryTest {
    @Test
    fun missingOrUnknownPreferenceFallsBackToSystem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "appearance_test_${UUID.randomUUID()}"
        try {
            assertEquals(ThemeMode.SYSTEM, AppearanceRepository(context, name).appearance.value.mode)
            context.getSharedPreferences(name, 0).edit().putString("theme_mode", "future-mode")
                .putString("light_theme", "future-cat").putString("dark_theme", "future-cat").commit()
            assertEquals(AppearancePreferences(), AppearanceRepository(context, name).appearance.value)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
