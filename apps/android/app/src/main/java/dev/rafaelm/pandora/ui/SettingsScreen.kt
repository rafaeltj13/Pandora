package dev.rafaelm.pandora.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
fun SettingsScreen(
    appearance: AppearancePreferences,
    onAppearance: () -> Unit,
    tasks: TasksState = TasksState(),
    tasksActions: TasksActions = TasksActions(),
) {
    var choosingCalendar by remember { mutableStateOf(false) }
    var choosingShown by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tasksActions.refresh() }
    PandoraPage(stringResource(R.string.nav_settings), stringResource(R.string.settings_subtitle)) {
        Card(onClick = onAppearance, modifier = Modifier.fillMaxWidth().testTag("settings_appearance")) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.appearance_title)) },
                supportingContent = { Text(stringResource(appearance.mode.label())) },
            )
        }
        val calendar = tasks.taskCalendars.firstOrNull { it.id == tasks.defaultCalendarId }
        Card(onClick = { choosingCalendar = true }, enabled = calendar != null,
            modifier = Modifier.fillMaxWidth().testTag("settings_default_calendar")) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.default_calendar_title)) },
                supportingContent = { Text(calendar?.label() ?: stringResource(when {
                    !tasks.access -> R.string.default_calendar_access
                    !tasks.loaded -> R.string.default_calendar_loading
                    else -> R.string.default_calendar_none
                })) },
            )
        }
        val shownCount = tasks.calendars.count { it.id !in tasks.hiddenCalendars }
        Card(onClick = { choosingShown = true }, enabled = tasks.calendars.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("settings_shown_calendars")) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.shown_calendars_title)) },
                supportingContent = { Text(when {
                    !tasks.access -> stringResource(R.string.default_calendar_access)
                    !tasks.loaded -> stringResource(R.string.default_calendar_loading)
                    tasks.calendars.isEmpty() -> stringResource(R.string.default_calendar_none)
                    shownCount == tasks.calendars.size -> stringResource(R.string.shown_calendars_all)
                    else -> stringResource(R.string.shown_calendars_some, shownCount, tasks.calendars.size)
                }) },
            )
        }
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) },
            )
        }
    }
    if (choosingCalendar) {
        CalendarPicker(tasks.taskCalendars, tasks.defaultCalendarId, onDismiss = { choosingCalendar = false },
            onSelect = { tasksActions.setDefaultCalendar(it); choosingCalendar = false })
    }
    if (choosingShown) {
        ShownCalendarsPicker(tasks.calendars, tasks.hiddenCalendars, onDismiss = { choosingShown = false },
            onToggle = tasksActions.setCalendarShown)
    }
}
