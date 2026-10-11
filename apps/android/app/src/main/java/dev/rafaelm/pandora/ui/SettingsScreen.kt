package dev.rafaelm.pandora.ui

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
    onBack: () -> Unit = {},
) {
    var choosingCalendar by remember { mutableStateOf(false) }
    var choosingShown by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tasksActions.refresh() }
    PandoraPage(stringResource(R.string.nav_settings), navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
        }
    }) {
        PandoraSection(stringResource(R.string.settings_interface)) {
            PandoraListItem(stringResource(R.string.appearance_title),
                supporting = stringResource(appearance.mode.label()), icon = R.drawable.ic_palette,
                onClick = onAppearance, modifier = Modifier.testTag("settings_appearance"))
        }
        PandoraSection(stringResource(R.string.settings_tasks)) {
            val calendar = tasks.taskCalendars.firstOrNull { it.id == tasks.defaultCalendarId }
            PandoraListItem(stringResource(R.string.default_calendar_title),
                supporting = calendar?.label() ?: stringResource(when {
                    !tasks.access -> R.string.default_calendar_access
                    !tasks.loaded -> R.string.default_calendar_loading
                    else -> R.string.default_calendar_none
                }),
                icon = R.drawable.ic_calendar,
                onClick = if (calendar != null) ({ choosingCalendar = true }) else null,
                modifier = Modifier.testTag("settings_default_calendar"))
            val shownCount = tasks.calendars.count { it.id !in tasks.hiddenCalendars }
            PandoraListItem(stringResource(R.string.shown_calendars_title),
                supporting = when {
                    !tasks.access -> stringResource(R.string.default_calendar_access)
                    !tasks.loaded -> stringResource(R.string.default_calendar_loading)
                    tasks.calendars.isEmpty() -> stringResource(R.string.default_calendar_none)
                    shownCount == tasks.calendars.size -> stringResource(R.string.shown_calendars_all)
                    else -> stringResource(R.string.shown_calendars_some, shownCount, tasks.calendars.size)
                },
                icon = R.drawable.ic_visibility,
                onClick = if (tasks.calendars.isNotEmpty()) ({ choosingShown = true }) else null,
                modifier = Modifier.testTag("settings_shown_calendars"))
        }
        PandoraSection(stringResource(R.string.about_title)) {
            PandoraListItem(stringResource(R.string.app_name),
                supporting = stringResource(R.string.version, BuildConfig.VERSION_NAME), icon = R.drawable.ic_info)
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
