package dev.rafaelm.pandora.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.rafaelm.pandora.data.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val AGENDA_DAYS = 14

data class TaskEditor(
    /** The task being edited; null for a new task. */
    val item: TaskItem? = null,
    val title: String = "",
    val description: String = "",
    val date: LocalDate,
    val time: LocalTime,
    val durationMinutes: Int = 30,
    val allDay: Boolean = false,
    val calendarId: Long? = null,
    val repeat: Repeat? = null,
    /** A repeat set in Google Calendar that Pandora keeps but cannot edit. */
    val customRule: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
)

data class TasksState(
    val loading: Boolean = false,
    val access: Boolean = false,
    val loaded: Boolean = false,
    val calendars: List<TaskCalendar> = emptyList(),
    val defaultCalendarId: Long? = null,
    val hiddenCalendars: Set<Long> = emptySet(),
    val tasks: List<TaskItem> = emptyList(),
    val events: List<AgendaEvent> = emptyList(),
    val agendaDays: Int = AGENDA_DAYS,
    val showEvents: Boolean = false,
    val editor: TaskEditor? = null,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val taskCalendars get() = calendars.filter { it.writable }
}

sealed interface AgendaEntry {
    val occurrence: Occurrence
    val title: String
}
data class TaskEntry(val item: TaskItem) : AgendaEntry {
    override val occurrence get() = item.next
    override val title get() = item.task.title
}
data class EventEntry(val event: AgendaEvent) : AgendaEntry {
    override val occurrence get() = event.occurrence
    override val title get() = event.title
}
data class AgendaDay(val date: LocalDate, val entries: List<AgendaEntry>)

/** Tasks and, unless filtered out, calendar events, by day; all-day items first, then by time. */
fun TasksState.days(clock: Clock): List<AgendaDay> {
    val zone = clock.zone
    val entries = tasks.map(::TaskEntry) + if (showEvents) events.map(::EventEntry) else emptyList()
    return entries.groupBy { it.occurrence.date(zone) }.toSortedMap().map { (date, items) ->
        AgendaDay(date, items.sortedWith(compareBy({ !it.occurrence.allDay }, { it.occurrence.startsAt(zone) }, { it.title })))
    }
}

class TasksViewModel(private val repository: TasksRepository) : ViewModel() {
    private val mutable = MutableStateFlow(TasksState(access = repository.hasAccess(), showEvents = repository.showEvents(),
        hiddenCalendars = repository.hiddenCalendars()))
    val state = mutable.asStateFlow()
    val clock get() = repository.clock
    private var loadJob: Job? = null
    private fun update(block: (TasksState) -> TasksState) { mutable.value = block(mutable.value) }

    private fun failure(error: Exception) {
        if (error is CancellationException) throw error
        val message = if (error is SecurityException) "Pandora needs calendar access to keep tasks in Google Calendar."
            else error.message ?: "The calendar could not be updated."
        update { it.copy(loading = false, busy = false, access = repository.hasAccess(),
            editor = it.editor?.copy(saving = false, error = message), error = if (it.editor == null) message else it.error) }
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        if (!repository.hasAccess()) { update { it.copy(access = false, loading = false) }; return }
        loadJob = viewModelScope.launch {
            update { it.copy(access = true, loading = true, error = null) }
            try {
                val calendars = repository.calendars()
                val tasks = repository.tasks(calendars)
                val events = repository.events(mutable.value.agendaDays, calendars)
                update { it.copy(loading = false, loaded = true, calendars = calendars,
                    defaultCalendarId = repository.defaultCalendar(calendars)?.id, tasks = tasks, events = events) }
            } catch (error: Exception) { failure(error) }
        }
    }

    fun showMoreDays() {
        update { it.copy(agendaDays = it.agendaDays + AGENDA_DAYS) }
        viewModelScope.launch { reloadEvents() }
    }

    fun setShowEvents(show: Boolean) {
        repository.setShowEvents(show)
        update { it.copy(showEvents = show) }
    }

    fun setCalendarShown(id: Long, shown: Boolean) {
        val hidden = if (shown) mutable.value.hiddenCalendars - id else mutable.value.hiddenCalendars + id
        repository.setHiddenCalendars(hidden)
        update { it.copy(hiddenCalendars = hidden) }
        viewModelScope.launch { reloadEvents() }
    }

    fun newTask() {
        val state = mutable.value
        if (!state.access || state.taskCalendars.isEmpty() || state.busy) return
        // Start at the next full hour.
        val start = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        update { it.copy(editor = TaskEditor(date = start.toLocalDate(), time = start.toLocalTime(), calendarId = state.defaultCalendarId)) }
    }

    /** A repeating task opens at its next occurrence, since edits apply from there on. */
    fun open(item: TaskItem) {
        if (mutable.value.busy) return
        val task = item.task
        val zone = clock.zone
        val repeat = task.rrule?.let { Repeat.parse(it, zone) }
        update { it.copy(editor = TaskEditor(
            item = item, title = task.title, description = task.description, date = item.next.date(zone),
            time = if (task.allDay) LocalTime.of(9, 0) else Instant.ofEpochMilli(item.next.start).atZone(zone).toLocalTime(),
            durationMinutes = if (task.allDay) 30 else task.durationMinutes, allDay = task.allDay,
            calendarId = task.calendarId.takeIf { id -> it.taskCalendars.any { calendar -> calendar.id == id } } ?: it.defaultCalendarId,
            repeat = repeat, customRule = task.rrule.takeIf { repeat == null },
        )) }
    }

    fun edit(
        title: String? = null, description: String? = null, date: LocalDate? = null, time: LocalTime? = null,
        durationMinutes: Int? = null, allDay: Boolean? = null, calendarId: Long? = null,
    ) {
        update { state -> state.copy(editor = state.editor?.let {
            if (it.saving) it else it.copy(title = title ?: it.title, description = description ?: it.description,
                date = date ?: it.date, time = time ?: it.time, durationMinutes = durationMinutes ?: it.durationMinutes,
                allDay = allDay ?: it.allDay, calendarId = calendarId ?: it.calendarId, error = null)
        }) }
    }

    /** Choosing any repeat, including none, replaces a custom Google Calendar rule. */
    fun setRepeat(repeat: Repeat?) {
        update { state -> state.copy(editor = state.editor?.let { if (it.saving) it else it.copy(repeat = repeat, customRule = null, error = null) }) }
    }

    fun closeEditor() { if (mutable.value.editor?.saving != true) update { it.copy(editor = null) } }

    /** The module was switched off: a save in progress still finishes in the background. */
    fun stop() { loadJob?.cancel(); update { it.copy(loading = false, editor = null) } }

    private fun draft(editor: TaskEditor): TaskDraft? = editor.calendarId?.let {
        TaskDraft(editor.title, editor.description, editor.date, editor.time, editor.durationMinutes, editor.allDay, it,
            editor.repeat, editor.customRule)
    }

    fun save() {
        val editor = mutable.value.editor ?: return
        if (editor.saving) return
        val draft = draft(editor)
        val problem = if (draft == null) "Choose a calendar." else draft.problem(clock)
        if (problem != null) { update { it.copy(editor = editor.copy(error = problem)) }; return }
        update { it.copy(editor = editor.copy(saving = true, error = null), busy = true) }
        viewModelScope.launch {
            try {
                if (editor.item == null) repository.create(draft!!) else repository.update(editor.item, draft!!)
                update { it.copy(editor = null, busy = false) }
                reload()
            } catch (error: Exception) { failure(error) }
        }
    }

    fun delete(item: TaskItem) = mutate { repository.delete(item); update { it.copy(editor = null) } }

    fun setDefaultCalendar(id: Long) {
        repository.setDefaultCalendar(id)
        update { it.copy(defaultCalendarId = id) }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block(); update { it.copy(busy = false) }; reload() } catch (error: Exception) { failure(error) }
        }
    }

    private suspend fun reload() {
        try { val tasks = repository.tasks(mutable.value.calendars); update { it.copy(tasks = tasks) } } catch (error: Exception) { failure(error) }
        reloadEvents()
    }

    private suspend fun reloadEvents() {
        if (!mutable.value.loaded) return
        try {
            val events = repository.events(mutable.value.agendaDays, mutable.value.calendars)
            update { it.copy(events = events) }
        } catch (error: Exception) { failure(error) }
    }

    class Factory(private val repository: TasksRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TasksViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return TasksViewModel(repository) as T
        }
    }
}
