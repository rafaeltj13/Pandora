package dev.rafaelm.pandora.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.rafaelm.pandora.BuildConfig
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.ThemeMode
import dev.rafaelm.pandora.data.CatTheme
import dev.rafaelm.pandora.data.AppearancePreferences

@StringRes
internal fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
internal fun CatTheme.label(): Int = when (this) {
    CatTheme.PANDORA -> R.string.app_name
    CatTheme.OTTO -> R.string.theme_otto
}

@Composable
fun SettingsScreen(appearance: AppearancePreferences, onAppearance: () -> Unit) {
    PandoraPage(stringResource(R.string.nav_settings)) {
        PandoraSection(stringResource(R.string.settings_interface)) {
            PandoraListItem(stringResource(R.string.appearance_title),
                supporting = stringResource(appearance.mode.label()), icon = R.drawable.ic_palette,
                onClick = onAppearance, modifier = Modifier.testTag("settings_appearance"))
        }
        PandoraSection(stringResource(R.string.about_title)) {
            PandoraListItem(stringResource(R.string.app_name),
                supporting = stringResource(R.string.version, BuildConfig.VERSION_NAME), icon = R.drawable.ic_info)
        }
    }
}
