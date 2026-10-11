package dev.rafaelm.pandora.data

import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DAY_MILLIS = 86_400_000L

/** Times follow Android Calendar: all-day spans are UTC midnights, timed spans are epoch millis. */
data class Occurrence(val start: Long, val end: Long, val allDay: Boolean) {
    fun date(zone: ZoneId): LocalDate =
        if (allDay) LocalDate.ofEpochDay(Math.floorDiv(start, DAY_MILLIS)) else Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    fun startsAt(zone: ZoneId): Instant = if (allDay) date(zone).atStartOfDay(zone).toInstant() else Instant.ofEpochMilli(start)
    fun endsAt(zone: ZoneId): Instant =
        if (allDay) LocalDate.ofEpochDay(Math.floorDiv(end, DAY_MILLIS)).atStartOfDay(zone).toInstant() else Instant.ofEpochMilli(end)
}

/**
 * A task is a calendar event carrying Pandora's tag, so it survives reinstalls and lives wherever the
 * event does. [start] and [end] are the first occurrence; [rrule] makes the task repeat.
 */
data class Task(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val rrule: String? = null,
) {
    val occurrence get() = Occurrence(start, end, allDay)
    val repeating get() = rrule != null
    val durationMinutes: Int get() = ((end - start) / 60_000).toInt()
}

/** [next] is the occurrence shown in the list: the task itself, or a repeating task's next occurrence. */
data class TaskItem(val task: Task, val next: Occurrence = task.occurrence) {
    /** Edits and deletes of a repeating task apply from [next] on; earlier occurrences stay. */
    val hasPastOccurrences get() = task.repeating && task.start < next.start
}
data class TaskCalendar(val id: Long, val name: String, val account: String, val main: Boolean, val writable: Boolean = true, val color: Int = 0)
data class CalendarEvent(
    val calendarId: Long, val title: String, val description: String, val start: Long, val end: Long, val allDay: Boolean,
    val rrule: String? = null,
)
/** One occurrence of a calendar event, shown read-only next to tasks. */
data class AgendaEvent(
    val eventId: Long,
    val originalId: Long?,
    val title: String,
    val calendarId: Long,
    val calendarName: String,
    val color: Int,
    val occurrence: Occurrence,
    val repeating: Boolean,
)

data class TaskDraft(
    val title: String,
    val description: String,
    val date: LocalDate,
    val time: LocalTime,
    val durationMinutes: Int,
    val allDay: Boolean,
    val calendarId: Long,
    val repeat: Repeat? = null,
    /** A Google Calendar rule Pandora cannot edit, kept while [repeat] is unset. */
    val rule: String? = null,
) {
    fun event(zone: ZoneId): CalendarEvent {
        val first = repeat?.firstDate(date) ?: date
        val start = if (allDay) first.toEpochDay() * DAY_MILLIS else ZonedDateTime.of(first, time, zone).toInstant().toEpochMilli()
        val end = if (allDay) start + DAY_MILLIS else start + durationMinutes * 60_000L
        return CalendarEvent(calendarId, title.trim(), description.trim(), start, end, allDay, repeat?.rrule(first, allDay, zone) ?: rule)
    }

    /** Ended tasks are no longer listed, so one that has already ended cannot be saved. */
    fun problem(clock: Clock): String? {
        val until = repeat?.until
        val ends = Occurrence(event(clock.zone).start, event(clock.zone).end, allDay).endsAt(clock.zone)
        return when {
            title.isBlank() -> "Add a title."
            !allDay && durationMinutes <= 0 -> "Pick a duration."
            until != null && until < date -> "The repeat must end on or after the first date."
            until != null && until < LocalDate.now(clock) -> "This repeat has already ended. Pick a later end date."
            repeat == null && rule == null && ends <= clock.instant() -> "This time has already passed. Pick a later time."
            else -> null
        }
    }
}

class TaskException(message: String) : IOException(message)

/** Reading or writing events throws SecurityException when calendar access is revoked. */
interface CalendarGateway {
    fun hasAccess(): Boolean
    /** Synced Google calendars, grouped by account with each account's main calendar first. */
    fun calendars(): List<TaskCalendar>
    /** Events tagged as Pandora tasks in [calendarIds] whose last occurrence ends after [after]. */
    fun tasks(calendarIds: Set<Long>, after: Long): List<Task>
    /** Every event tagged as a Pandora task, including ended ones. */
    fun taskEventIds(): Set<Long>
    fun event(id: Long): CalendarEvent?
    /** Occurrences of a series overlapping [from, to), including single changed occurrences, without cancelled ones. */
    fun occurrences(eventId: Long, from: Long, to: Long): List<Occurrence>
    fun agenda(calendarIds: Set<Long>, from: Long, to: Long): List<AgendaEvent>
    /** Creates the event already tagged as a Pandora task. */
    fun insert(event: CalendarEvent): Long
    fun update(id: Long, event: CalendarEvent)
    /** Gives a series a rule ending before [from], drops its changed occurrences from then on, and untags it. */
    fun endSeries(id: Long, rrule: String, from: Long)
    fun delete(id: Long)
}

/** Display preferences; tasks themselves live in the calendar. */
interface TaskStore {
    fun defaultCalendar(): Long?
    fun setDefaultCalendar(id: Long)
    fun hiddenCalendars(): Set<Long>
    fun setHiddenCalendars(ids: Set<Long>)
    fun showEvents(): Boolean
    fun setShowEvents(show: Boolean)
}

class TasksRepository(
    private val calendar: CalendarGateway,
    private val store: TaskStore,
    val clock: Clock = Clock.systemDefaultZone(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    fun hasAccess() = calendar.hasAccess()
    suspend fun calendars() = withContext(io) { calendar.calendars() }
    fun defaultCalendar(calendars: List<TaskCalendar>): TaskCalendar? = calendars.filter { it.writable }.let { writable ->
        writable.firstOrNull { it.id == store.defaultCalendar() } ?: writable.firstOrNull { it.main } ?: writable.firstOrNull()
    }
    fun setDefaultCalendar(id: Long) = store.setDefaultCalendar(id)
    fun hiddenCalendars() = store.hiddenCalendars()
    fun setHiddenCalendars(ids: Set<Long>) = store.setHiddenCalendars(ids)
    fun showEvents() = store.showEvents()
    fun setShowEvents(show: Boolean) = store.setShowEvents(show)

    /** Tasks that have not ended, at their next occurrence. */
    suspend fun tasks(calendars: List<TaskCalendar>): List<TaskItem> = locked {
        val zone = clock.zone
        val now = clock.instant()
        val writable = calendars.filter { it.writable }.map { it.id }.toSet()
        // All-day events end at UTC midnight, so ask for a little more and filter precisely here.
        calendar.tasks(writable, now.minus(Duration.ofDays(2)).toEpochMilli()).mapNotNull { task ->
            val next = if (task.repeating) nextOccurrence(task.eventId) else task.occurrence.takeIf { it.endsAt(zone) > now }
            next?.let { TaskItem(task, it) }
        }.sortedWith(compareBy({ it.next.startsAt(zone) }, { it.task.title }))
    }

    /** Occurrences of events in shown calendars from today through [days] days ahead, without tasks. */
    suspend fun events(days: Int, calendars: List<TaskCalendar>): List<AgendaEvent> = withContext(io) {
        val zone = clock.zone
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val shown = calendars.map { it.id }.toSet() - store.hiddenCalendars()
        if (shown.isEmpty()) return@withContext emptyList()
        val tasks = calendar.taskEventIds()
        // Start a day early: all-day occurrences are stored at UTC midnight.
        calendar.agenda(shown, today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(days + 1L).atStartOfDay(zone).toInstant().toEpochMilli())
            .filter { it.eventId !in tasks && it.originalId !in tasks }
            .filter { it.occurrence.endsAt(zone) > now && it.occurrence.date(zone) < today.plusDays(days.toLong()) }
            .sortedWith(compareBy({ it.occurrence.date(zone) }, { !it.occurrence.allDay }, { it.occurrence.startsAt(zone) }, { it.title }))
    }

    suspend fun create(draft: TaskDraft): Long = locked { calendar.insert(validated(draft)) }

    /**
     * A task with past occurrences is split: its series ends before [TaskItem.next] and a new tagged
     * series starts from the edited values. Otherwise the event is updated in place, or recreated in
     * a newly chosen calendar.
     */
    suspend fun update(item: TaskItem, draft: TaskDraft): Long = locked {
        val event = validated(draft)
        val id = item.task.eventId
        val existing = calendar.event(id) ?: throw TaskException("This task's event was deleted from Google Calendar.")
        if (!item.hasPastOccurrences && existing.calendarId == event.calendarId) {
            calendar.update(id, event)
            return@locked id
        }
        val newId = calendar.insert(event)
        if (item.hasPastOccurrences) calendar.endSeries(id, endBefore(existing.rrule!!, item.next, clock.zone), item.next.start)
        else calendar.delete(id)
        newId
    }

    /** A repeating task with past occurrences stops before its next one; any other task's event is deleted. */
    suspend fun delete(item: TaskItem) = locked {
        val id = item.task.eventId
        val existing = calendar.event(id) ?: return@locked
        if (item.hasPastOccurrences && existing.rrule != null) calendar.endSeries(id, endBefore(existing.rrule, item.next, clock.zone), item.next.start)
        else calendar.delete(id)
    }

    /** Searches widening windows so rare repeats are found without expanding years of daily events. */
    private fun nextOccurrence(eventId: Long): Occurrence? {
        val zone = clock.zone
        val now = clock.instant()
        var from = now.minus(Duration.ofDays(2))
        for (days in listOf(31L, 400L, 2_000L)) {
            val to = now.plus(Duration.ofDays(days))
            calendar.occurrences(eventId, from.toEpochMilli(), to.toEpochMilli())
                .filter { it.endsAt(zone) > now }.minByOrNull { it.startsAt(zone) }?.let { return it }
            from = to
        }
        return null
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(io) { mutex.withLock { block() } }
    private fun validated(draft: TaskDraft): CalendarEvent {
        draft.problem(clock)?.let { throw TaskException(it) }
        return draft.event(clock.zone)
    }
}
