package dev.rafaelm.pandora.ui

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.*
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class TasksActions(
    val refresh: () -> Unit = {},
    val newTask: () -> Unit = {},
    val open: (TaskItem) -> Unit = {},
    val editTitle: (String) -> Unit = {},
    val editDescription: (String) -> Unit = {},
    val editDate: (LocalDate) -> Unit = {},
    val editTime: (LocalTime) -> Unit = {},
    val editDuration: (Int) -> Unit = {},
    val editAllDay: (Boolean) -> Unit = {},
    val editCalendar: (Long) -> Unit = {},
    val setRepeat: (Repeat?) -> Unit = {},
    val save: () -> Unit = {},
    val closeEditor: () -> Unit = {},
    val delete: (TaskItem) -> Unit = {},
    val setDefaultCalendar: (Long) -> Unit = {},
    val setCalendarShown: (Long, Boolean) -> Unit = { _, _ -> },
    val setShowEvents: (Boolean) -> Unit = {},
    val showMoreDays: () -> Unit = {},
    val openEvent: ((AgendaEvent) -> Unit)? = null,
) {
    companion object {
        fun from(model: TasksViewModel) = TasksActions(
            refresh = model::refresh, newTask = model::newTask, open = model::open,
            editTitle = { model.edit(title = it) }, editDescription = { model.edit(description = it) },
            editDate = { model.edit(date = it) }, editTime = { model.edit(time = it) },
            editDuration = { model.edit(durationMinutes = it) }, editAllDay = { model.edit(allDay = it) },
            editCalendar = { model.edit(calendarId = it) }, setRepeat = model::setRepeat, save = model::save,
            closeEditor = model::closeEditor, delete = { model.delete(it) },
            setDefaultCalendar = model::setDefaultCalendar, setCalendarShown = model::setCalendarShown,
            setShowEvents = model::setShowEvents, showMoreDays = model::showMoreDays,
        )
    }
}

private val durations = listOf(15, 30, 45, 60, 90, 120)
private const val DAY_MILLIS = 86_400_000L
private const val REPEAT_CUE = "🔁"
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

internal fun durationLabel(minutes: Int) = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

internal fun TaskCalendar.label() = if (name == account) name else "$name · $account"

private fun Occurrence.label(clock: Clock) = if (allDay) "All day" else
    "${Instant.ofEpochMilli(start).atZone(clock.zone).format(timeFormat)} – ${Instant.ofEpochMilli(end).atZone(clock.zone).format(timeFormat)}"

private fun Task.repeatLabel(clock: Clock): String? =
    rrule?.let { Repeat.parse(it, clock.zone)?.label(occurrence.date(clock.zone)) ?: "Custom repeat" }

private fun openInCalendar(context: Context, event: AgendaEvent) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.occurrence.start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.occurrence.end)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
}

@Composable
fun TasksScreen(state: TasksState, actions: TasksActions, clock: Clock = Clock.systemDefaultZone(), onOpenSettings: (() -> Unit)? = null) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.refresh() }
    DisposableEffect(Unit) { onDispose { actions.closeEditor() } }
    var deleting by remember { mutableStateOf<TaskItem?>(null) }
    val editor = state.editor
    if (editor != null) {
        BackHandler { actions.closeEditor() }
        TaskForm(editor, state.taskCalendars, actions, clock, onDelete = { deleting = it })
    } else {
        TaskList(state, actions, clock, onOpenSettings)
    }
    deleting?.let { item ->
        val task = item.task
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete task?") },
            text = { Text(when {
                item.hasPastOccurrences -> "“${task.title}” will stop repeating from ${item.next.date(clock.zone).format(dateFormat)}. " +
                    "Earlier events stay in your calendar."
                task.repeating -> "“${task.title}” and every event in its repeating series will be deleted from Google Calendar."
                else -> "“${task.title}” will be deleted from Google Calendar."
            }) },
            confirmButton = { TextButton(onClick = { actions.delete(item); deleting = null }, modifier = Modifier.testTag("confirm_delete")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun TaskList(state: TasksState, actions: TasksActions, clock: Clock, onOpenSettings: (() -> Unit)?) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { actions.refresh() }
    val openEvent = actions.openEvent ?: { event: AgendaEvent -> openInCalendar(context, event) }
    val today = LocalDate.now(clock)
    PandoraScaffold(stringResource(R.string.nav_tasks), onOpenSettings = onOpenSettings) { bottom ->
        Box(Modifier.fillMaxSize().testTag("tasks_screen")) {
            LazyColumn(contentPadding = PaddingValues(PandoraSpacing.large, PandoraSpacing.large, PandoraSpacing.large, 96.dp + bottom),
                verticalArrangement = Arrangement.spacedBy(PandoraSpacing.extraLarge), modifier = Modifier.fillMaxSize()) {
                if (!state.access) {
                    item { PandoraNotice("Connect your calendar",
                        "Every task becomes a Google Calendar event. Allow calendar access so Pandora can create, update, and check those events.") {
                        Button(onClick = { permission.launch(calendarPermissions) }, modifier = Modifier.testTag("allow_calendar")) { Text("Allow calendar access") }
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }) { Text("Open app settings") }
                    } }
                } else if (!state.loaded) {
                    if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                } else if (state.taskCalendars.isEmpty()) {
                    item { PandoraNotice("No Google calendar found",
                        "Add your Google account to this phone and turn on Calendar sync for it, then refresh.") {
                        Button(onClick = actions.refresh, enabled = !state.loading) { Text("Refresh") }
                    } }
                } else {
                    item { Row(horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                        FilterChip(selected = state.showEvents, onClick = { actions.setShowEvents(true) }, label = { Text("Everything") },
                            modifier = Modifier.testTag("filter_everything"))
                        FilterChip(selected = !state.showEvents, onClick = { actions.setShowEvents(false) }, label = { Text("Tasks") },
                            modifier = Modifier.testTag("filter_tasks"))
                    } }
                    if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    val days = state.days(clock)
                    if (days.isEmpty() && !state.loading) item {
                        Text(if (state.showEvents) "Nothing planned in the next ${state.agendaDays} days. Tap + to add a task." else "No upcoming tasks. Tap + to add one.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PandoraSpacing.large))
                    }
                    items(days, key = { "day:${it.date}" }) { day ->
                        PandoraSection(when (day.date) {
                            today -> "Today"
                            today.plusDays(1) -> "Tomorrow"
                            else -> day.date.format(dayFormat)
                        }) {
                            day.entries.forEach { entry ->
                                when (entry) {
                                    is TaskEntry -> TaskRow(entry.item, state, actions, clock)
                                    is EventEntry -> EventRow(entry.event, clock, onClick = { openEvent(entry.event) })
                                }
                            }
                        }
                    }
                    if (state.showEvents) item {
                        TextButton(onClick = actions.showMoreDays, modifier = Modifier.fillMaxWidth().testTag("show_more_days")) { Text("Show $AGENDA_DAYS more days") }
                    }
                }
                state.error?.let { message -> item {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = PandoraSpacing.large))
                } }
            }
            if (state.access && state.taskCalendars.isNotEmpty()) {
                FloatingActionButton(onClick = actions.newTask, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = bottom).padding(PandoraSpacing.large).testTag("new_task")) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "New task")
                }
            }
        }
    }
}

@Composable
private fun TaskRow(item: TaskItem, state: TasksState, actions: TasksActions, clock: Clock) {
    val task = item.task
    ListItem(
        headlineContent = { Text(task.title) },
        supportingContent = { Column(verticalArrangement = Arrangement.spacedBy(PandoraSpacing.extraSmall)) {
            Text(item.next.label(clock), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            task.repeatLabel(clock)?.let { Text("$REPEAT_CUE $it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("repeat:${task.eventId}")) }
            if (task.description.isNotBlank()) Text(task.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } },
        leadingContent = { Icon(painterResource(R.drawable.ic_tasks), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { actions.open(item) }.testTag("task:${task.eventId}"),
    )
}

/** Calendar events are read-only in Pandora; tapping one opens it in the calendar app. */
@Composable
private fun EventRow(event: AgendaEvent, clock: Clock, onClick: () -> Unit) {
    PandoraListItem(event.title, supporting = "${event.occurrence.label(clock)} · ${event.calendarName}",
        leading = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).background(Color(event.color or 0xFF000000.toInt()), CircleShape))
        } },
        trailing = if (event.repeating) ({ Text(REPEAT_CUE, modifier = Modifier.semantics { contentDescription = "Repeats" }) }) else ({}),
        onClick = onClick, modifier = Modifier.testTag("event:${event.eventId}"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskForm(editor: TaskEditor, calendars: List<TaskCalendar>, actions: TasksActions, clock: Clock, onDelete: (TaskItem) -> Unit) {
    var picker by remember { mutableStateOf<String?>(null) }
    val enabled = !editor.saving
    val today = LocalDate.now(clock)
    val repeat = editor.repeat
    fun pick(name: String): (() -> Unit)? = if (enabled) ({ picker = name }) else null
    PandoraScaffold(if (editor.item == null) "New task" else "Edit task", navigationIcon = {
        IconButton(onClick = actions.closeEditor, enabled = enabled) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Discard") }
    }, actions = {
        TextButton(onClick = actions.save, enabled = enabled && editor.title.isNotBlank(), modifier = Modifier.testTag("save_task")) {
            Text(when { editor.saving -> "Saving…"; editor.item == null -> "Add"; else -> "Save" })
        }
    }) { bottom ->
        // A plain scrolling column: the form is short and every field stays composed.
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(PandoraSpacing.large).padding(bottom = bottom).testTag("task_form"),
            verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large)) {
            if (editor.item?.hasPastOccurrences == true) {
                Text("Changes apply from ${editor.item.next.date(clock.zone).format(dateFormat)} on. Earlier events stay as they are.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PandoraSpacing.large).testTag("task_split_note"))
            }
            OutlinedTextField(editor.title, actions.editTitle, label = { Text("Title") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("task_title"))
            OutlinedTextField(editor.description, actions.editDescription, label = { Text("Description (optional)") }, minLines = 3, enabled = enabled, modifier = Modifier.fillMaxWidth())
            PandoraSection("When") {
                PandoraListItem("All day",
                    trailing = { Switch(editor.allDay, actions.editAllDay, enabled = enabled, modifier = Modifier.testTag("task_all_day")) })
                PandoraListItem(if (repeat != null || editor.customRule != null) "Starts" else "Date",
                    supporting = editor.date.format(dateFormat), onClick = pick("date"))
                if (!editor.allDay) {
                    PandoraListItem("Time", supporting = editor.time.format(timeFormat), onClick = pick("time"))
                    Text("Duration", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PandoraSpacing.large, vertical = PandoraSpacing.small))
                    // Rows of three keep every duration visible without scrolling.
                    Column(Modifier.padding(horizontal = PandoraSpacing.large), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.extraSmall)) {
                        (durations + editor.durationMinutes).distinct().sorted().chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                                row.forEach { minutes ->
                                    FilterChip(selected = minutes == editor.durationMinutes, onClick = { actions.editDuration(minutes) }, enabled = enabled,
                                        label = { Text(durationLabel(minutes)) }, modifier = Modifier.testTag("duration:$minutes"))
                                }
                            }
                        }
                    }
                }
            }
            PandoraSection("Details") {
                PandoraListItem("Repeat", supporting = when {
                    repeat != null -> "$REPEAT_CUE ${repeat.copy(until = null).label(repeat.firstDate(editor.date))}"
                    editor.customRule != null -> "$REPEAT_CUE Custom repeat (set in Google Calendar)"
                    else -> "Doesn't repeat"
                }, onClick = pick("repeat"), modifier = Modifier.testTag("task_repeat"))
                if (repeat != null) PandoraListItem("Ends", supporting = repeat.until?.format(dateFormat) ?: "Never",
                    onClick = pick("ends"), modifier = Modifier.testTag("task_repeat_ends"))
                val calendar = calendars.firstOrNull { it.id == editor.calendarId }
                PandoraListItem("Calendar", supporting = calendar?.label() ?: "Choose a calendar",
                    onClick = pick("calendar"))
            }
            editor.error?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = PandoraSpacing.large).testTag("task_error"))
            }
            editor.item?.let { item ->
                OutlinedButton(onClick = { onDelete(item) }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete task") }
            }
        }
    }
    when (picker) {
        "date" -> DateDialog(editor.date, today, onDismiss = { picker = null }, onPick = { actions.editDate(it); picker = null })
        "until" -> DateDialog(repeat?.until ?: maxOf(editor.date, today).plusMonths(1), maxOf(editor.date, today),
            onDismiss = { picker = null }, onPick = { date -> repeat?.let { actions.setRepeat(it.copy(until = date)) }; picker = null })
        "time" -> {
            val state = rememberTimePickerState(editor.time.hour, editor.time.minute, DateFormat.is24HourFormat(LocalContext.current))
            AlertDialog(onDismissRequest = { picker = null }, text = { TimePicker(state) },
                confirmButton = { TextButton(onClick = { actions.editTime(LocalTime.of(state.hour, state.minute)); picker = null }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } })
        }
        "repeat" -> RepeatPicker(editor, onDismiss = { picker = null }, onSelect = { actions.setRepeat(it); picker = null }, onCustom = { picker = "custom" })
        "custom" -> CustomRepeatDialog(editor, onDismiss = { picker = null }, onDone = { actions.setRepeat(it); picker = null })
        "ends" -> AlertDialog(onDismissRequest = { picker = null }, title = { Text("Ends") },
            text = { Text("Repeat forever, or stop after a date. Past events stay in your calendar either way.") },
            confirmButton = { TextButton(onClick = { picker = "until" }, modifier = Modifier.testTag("ends_on_date")) { Text("On a date") } },
            dismissButton = { TextButton(onClick = { repeat?.let { actions.setRepeat(it.copy(until = null)) }; picker = null }) { Text("Never") } })
        "calendar" -> CalendarPicker(calendars, editor.calendarId, onDismiss = { picker = null }, onSelect = { actions.editCalendar(it); picker = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, earliest: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * DAY_MILLIS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= earliest.toEpochDay() * DAY_MILLIS
            override fun isSelectableYear(year: Int) = year >= earliest.year
        })
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MILLIS))) } ?: onDismiss()
        }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) { DatePicker(state) }
}

/** Presets keep a chosen end date; "Custom…" opens the interval and weekday editor. */
@Composable
private fun RepeatPicker(editor: TaskEditor, onDismiss: () -> Unit, onSelect: (Repeat?) -> Unit, onCustom: () -> Unit) {
    val date = editor.date
    val until = editor.repeat?.until
    fun Repeat.normal() = copy(until = null, weekdays = days(firstDate(date)))
    val presets = listOf(
        "Doesn't repeat" to null,
        "Every day" to Repeat(RepeatFrequency.DAILY),
        "Every weekday (Mon–Fri)" to Repeat(RepeatFrequency.WEEKLY, weekdays = workweek),
        "Every week on ${date.dayOfWeek.shortName()}" to Repeat(RepeatFrequency.WEEKLY, weekdays = setOf(date.dayOfWeek)),
        "Every month on day ${date.dayOfMonth}" to Repeat(RepeatFrequency.MONTHLY),
        "Every year on ${date.format(DateTimeFormatter.ofPattern("MMM d"))}" to Repeat(RepeatFrequency.YEARLY),
    )
    val current = editor.repeat?.normal()
    val isPreset = editor.customRule == null && presets.any { it.second?.normal() == current }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Repeat") },
        text = { Column(Modifier.selectableGroup()) {
            presets.forEach { (label, repeat) ->
                ChoiceRow(label, selected = editor.customRule == null && repeat?.normal() == current, tag = "repeat_option:$label") {
                    onSelect(repeat?.copy(until = until))
                }
            }
            if (editor.customRule != null) ChoiceRow("Custom repeat (set in Google Calendar)", selected = true, tag = "repeat_option:google") { onDismiss() }
            ChoiceRow(if (!isPreset && editor.repeat != null) "Custom: ${editor.repeat.copy(until = null).label(editor.repeat.firstDate(date))}" else "Custom…",
                selected = !isPreset && editor.repeat != null, tag = "repeat_option:custom", onClick = onCustom)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = PandoraSpacing.small).testTag(tag)) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = PandoraSpacing.medium))
    }
}

@Composable
private fun CustomRepeatDialog(editor: TaskEditor, onDismiss: () -> Unit, onDone: (Repeat) -> Unit) {
    val initial = editor.repeat?.takeIf { it.frequency != RepeatFrequency.YEARLY }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: RepeatFrequency.WEEKLY) }
    var days by remember { mutableStateOf(initial?.days(editor.date)?.ifEmpty { null } ?: setOf(editor.date.dayOfWeek)) }
    val count = interval.toIntOrNull()?.takeIf { it in 1..99 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Custom repeat") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                Text("Every")
                OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(2) }, singleLine = true, isError = count == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(72.dp).testTag("custom_interval"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                listOf(RepeatFrequency.DAILY, RepeatFrequency.WEEKLY, RepeatFrequency.MONTHLY).forEach { option ->
                    FilterChip(selected = frequency == option, onClick = { frequency = option },
                        label = { Text(if (count == 1) option.unit else "${option.unit}s") }, modifier = Modifier.testTag("custom_unit:${option.name}"))
                }
            }
            if (frequency == RepeatFrequency.WEEKLY) {
                Text("On", style = MaterialTheme.typography.labelLarge)
                // Two rows (Mon–Thu, Fri–Sun) keep every day visible without scrolling.
                DayOfWeek.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.extraSmall)) {
                        row.forEach { day ->
                            FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day },
                                label = { Text(day.shortName()) }, modifier = Modifier.testTag("custom_day:${day.name}"))
                        }
                    }
                }
            }
        } },
        confirmButton = { TextButton(enabled = count != null && (frequency != RepeatFrequency.WEEKLY || days.isNotEmpty()), onClick = {
            onDone(Repeat(frequency, count!!, if (frequency == RepeatFrequency.WEEKLY) days else emptySet(), editor.repeat?.until))
        }, modifier = Modifier.testTag("custom_done")) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun CalendarPicker(calendars: List<TaskCalendar>, selected: Long?, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calendar") },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup()) {
            items(calendars, key = { it.id }) { calendar ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                    .selectable(calendar.id == selected, role = Role.RadioButton, onClick = { onSelect(calendar.id) })
                    .padding(vertical = PandoraSpacing.small).testTag("calendar:${calendar.id}")) {
                    RadioButton(selected = calendar.id == selected, onClick = null)
                    Column(Modifier.padding(start = PandoraSpacing.medium)) {
                        Text(calendar.name)
                        if (calendar.name != calendar.account) Text(calendar.account, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun ShownCalendarsPicker(calendars: List<TaskCalendar>, hidden: Set<Long>, onDismiss: () -> Unit, onToggle: (Long, Boolean) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calendars shown in Tasks") },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(calendars, key = { it.id }) { calendar ->
                val shown = calendar.id !in hidden
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                    .clickable { onToggle(calendar.id, !shown) }.padding(vertical = PandoraSpacing.small).testTag("shown_calendar:${calendar.id}")) {
                    Checkbox(checked = shown, onCheckedChange = null)
                    Box(Modifier.padding(start = PandoraSpacing.small).size(10.dp).background(Color(calendar.color or 0xFF000000.toInt()), CircleShape))
                    Column(Modifier.padding(start = PandoraSpacing.medium)) {
                        Text(calendar.name)
                        Text(if (calendar.writable) calendar.account else "${calendar.account} · read-only", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
