package dev.rafaelm.pandora.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import dev.rafaelm.pandora.BuildConfig
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.ThemeMode
import dev.rafaelm.pandora.data.CatTheme
import dev.rafaelm.pandora.data.AppearancePreferences
import dev.rafaelm.pandora.data.Module

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
    modules: Set<Module> = emptySet(),
    onModuleChange: (Module, Boolean) -> Unit = { _, _ -> },
    tasks: TasksState = TasksState(),
    tasksActions: TasksActions = TasksActions(),
    development: DevelopmentState = DevelopmentState(),
    developmentActions: DevelopmentActions = DevelopmentActions(),
    onOpenUrl: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var configuringUpload by remember { mutableStateOf(false) }
    var choosingCalendar by remember { mutableStateOf(false) }
    var choosingShown by remember { mutableStateOf(false) }
    val tasksOn = Module.TASKS in modules
    if (tasksOn) LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tasksActions.refresh() }
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
            ModuleSwitch(Module.TASKS, tasksOn, onModuleChange, R.string.nav_tasks, R.string.module_tasks_description, R.drawable.ic_tasks)
            if (!tasksOn) return@PandoraSection
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
        PandoraSection(stringResource(R.string.settings_development)) {
            ModuleSwitch(Module.DEVELOPMENT, Module.DEVELOPMENT in modules, onModuleChange, R.string.settings_development,
                R.string.module_development_description, R.drawable.ic_development)
            if (Module.DEVELOPMENT in modules) PandoraListItem("Attachment token",
                supporting = if (development.uploadTokenConfigured) "Saved on this device" else "Optional, for image uploads",
                onClick = { configuringUpload = true }, modifier = Modifier.testTag("settings_attachment_token"))
        }
        PandoraSection(stringResource(R.string.about_title)) {
            PandoraListItem(stringResource(R.string.app_name),
                supporting = stringResource(R.string.version, BuildConfig.VERSION_NAME), icon = R.drawable.ic_info)
        }
    }
    if (configuringUpload) GitHubUploadTokenDialog(development, developmentActions, onOpenUrl) { configuringUpload = false }
    if (choosingCalendar) {
        CalendarPicker(tasks.taskCalendars, tasks.defaultCalendarId, onDismiss = { choosingCalendar = false },
            onSelect = { tasksActions.setDefaultCalendar(it); choosingCalendar = false })
    }
    if (choosingShown) {
        ShownCalendarsPicker(tasks.calendars, tasks.hiddenCalendars, onDismiss = { choosingShown = false },
            onToggle = tasksActions.setCalendarShown)
    }
}

/** The first row of a module's section; the module's other settings show below it only while it is on. */
@Composable
private fun ModuleSwitch(
    module: Module,
    on: Boolean,
    onChange: (Module, Boolean) -> Unit,
    @StringRes name: Int,
    @StringRes description: Int,
    @DrawableRes icon: Int,
) {
    PandoraListItem(stringResource(name), supporting = stringResource(description), icon = icon,
        trailing = { Switch(on, onCheckedChange = null) },
        modifier = Modifier.toggleable(on, role = Role.Switch) { onChange(module, it) }
            .testTag("module_${module.name.lowercase()}"))
}
