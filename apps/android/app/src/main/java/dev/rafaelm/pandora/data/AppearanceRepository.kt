package dev.rafaelm.pandora.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class CatTheme { PANDORA, OTTO }

data class AppearancePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val lightTheme: CatTheme = CatTheme.PANDORA,
    val darkTheme: CatTheme = CatTheme.PANDORA,
)

/** Device-local preferences, available before the first Compose frame. */
class AppearanceRepository(context: Context, preferencesName: String = "appearance") {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private fun savedTheme(key: String) = CatTheme.entries.firstOrNull {
        it.name == preferences.getString(key, null)
    } ?: CatTheme.PANDORA
    private val mutablePreferences = MutableStateFlow(AppearancePreferences(
        mode = ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme_mode", null) }
            ?: ThemeMode.SYSTEM,
        lightTheme = savedTheme("light_theme"),
        darkTheme = savedTheme("dark_theme"),
    ))
    val appearance = mutablePreferences.asStateFlow()

    fun setMode(mode: ThemeMode) {
        preferences.edit { putString("theme_mode", mode.name) }
        mutablePreferences.value = mutablePreferences.value.copy(mode = mode)
    }

    fun setTheme(theme: CatTheme, dark: Boolean) {
        preferences.edit { putString(if (dark) "dark_theme" else "light_theme", theme.name) }
        mutablePreferences.value = if (dark) mutablePreferences.value.copy(darkTheme = theme)
            else mutablePreferences.value.copy(lightTheme = theme)
    }

    fun setBothThemes(theme: CatTheme) {
        preferences.edit { putString("light_theme", theme.name); putString("dark_theme", theme.name) }
        mutablePreferences.value = mutablePreferences.value.copy(lightTheme = theme, darkTheme = theme)
    }
}
