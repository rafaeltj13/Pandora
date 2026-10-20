package dev.rafaelm.pandora.data

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeCalendar : CalendarGateway {
    var access = true
    val events = mutableMapOf<Long, CalendarEvent>()
    val tagged = mutableSetOf<Long>()
    val ended = mutableMapOf<Long, Long>()
    var calendars = listOf(TaskCalendar(1, "Work", "me@gmail.com", false), TaskCalendar(2, "me@gmail.com", "me@gmail.com", true),
        TaskCalendar(3, "Holidays", "me@gmail.com", false, writable = false))
    /** Occurrences of repeating events; other events occur once at their own times. */
    val occurrences = mutableMapOf<Long, List<Occurrence>>()
    val agenda = mutableListOf<AgendaEvent>()
    private var nextId = 100L
    private fun checkAccess() { if (!access) throw SecurityException() }
    override fun hasAccess() = access
    override fun calendars() = calendars
    override fun tasks(calendarIds: Set<Long>, after: Long): List<Task> { checkAccess()
        return events.filter { (id, event) -> id in tagged && event.calendarId in calendarIds && (event.rrule != null || event.end > after) }
            .map { (id, e) -> Task(id, e.calendarId, e.title, e.description, e.start, e.end, e.allDay, e.rrule) }
    }
    override fun taskEventIds() = tagged.toSet()
    override fun event(id: Long): CalendarEvent? { checkAccess(); return events[id] }
    override fun occurrences(eventId: Long, from: Long, to: Long): List<Occurrence> {
        val event = events[eventId] ?: return emptyList()
        return (occurrences[eventId] ?: listOf(Occurrence(event.start, event.end, event.allDay))).filter { it.end > from && it.start < to }
    }
    override fun agenda(calendarIds: Set<Long>, from: Long, to: Long) =
        agenda.filter { it.calendarId in calendarIds && it.occurrence.end > from && it.occurrence.start < to }
    override fun insert(event: CalendarEvent): Long { checkAccess(); return nextId++.also { events[it] = event; tagged += it } }
    override fun update(id: Long, event: CalendarEvent) { events[id] = event.copy(calendarId = events.getValue(id).calendarId) }
    override fun endSeries(id: Long, rrule: String, from: Long) {
        events[id] = events.getValue(id).copy(rrule = rrule); tagged -= id; ended[id] = from
    }
    override fun delete(id: Long) { events.remove(id); tagged -= id }
}

class MemoryTaskStore : TaskStore {
    var default: Long? = null
    var hidden = setOf<Long>()
    var eventsShown = false
    override fun defaultCalendar() = default
    override fun setDefaultCalendar(id: Long) { default = id }
    override fun hiddenCalendars() = hidden
    override fun setHiddenCalendars(ids: Set<Long>) { hidden = ids }
    override fun showEvents() = eventsShown
    override fun setShowEvents(show: Boolean) { eventsShown = show }
}

class TasksRepositoryTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private var now = Instant.parse("2026-10-10T12:00:00Z") // 09:00 on Saturday in São Paulo
    private val clock get() = Clock.fixed(now, zone)
    private val calendar = FakeCalendar()
    private val store = MemoryTaskStore()
    private val repository get() = TasksRepository(calendar, store, clock, Dispatchers.Unconfined)
    private fun draft(title: String = "Dentist", time: LocalTime = LocalTime.of(14, 0), allDay: Boolean = false, calendarId: Long = 2,
                      date: LocalDate = LocalDate.of(2026, 10, 10), repeat: Repeat? = null) =
        TaskDraft(title, "Bring the card", date, time, 45, allDay, calendarId, repeat)
    private fun at(text: String) = Instant.parse(text).toEpochMilli()
    private fun occurrence(start: String) = Occurrence(at(start), at(start) + 45 * 60_000, false)
    private suspend fun tasks() = repository.tasks(calendar.calendars)
    private fun agendaEvent(id: Long, title: String, start: String, end: String, calendarId: Long = 3, originalId: Long? = null) =
        AgendaEvent(id, originalId, title, calendarId, "Holidays", 0, Occurrence(at(start), at(end), false), repeating = originalId != null)

    @Test fun `creating a task creates a tagged calendar event`() = runTest {
        val id = repository.create(draft())
        assertEquals(CalendarEvent(2, "Dentist", "Bring the card", at("2026-10-10T17:00:00Z"), at("2026-10-10T17:45:00Z"), false),
            calendar.events.getValue(id))
        assertTrue(id in calendar.tagged)
        assertEquals(listOf(id), tasks().map { it.task.eventId })
    }

    @Test fun `all-day tasks use UTC midnights like Android Calendar`() = runTest {
        val event = calendar.events.getValue(repository.create(draft(allDay = true)))
        assertEquals(LocalDate.of(2026, 10, 10).toEpochDay() * 86_400_000, event.start)
        assertEquals(event.start + 86_400_000, event.end)
        assertTrue(event.allDay)
    }

    @Test fun `a task that would already be over is rejected without touching the calendar`() = runTest {
        assertTrue(runCatching { repository.create(draft(time = LocalTime.of(8, 0))) }.exceptionOrNull() is TaskException)
        assertTrue(runCatching { repository.create(draft(title = " ")) }.exceptionOrNull() is TaskException)
        assertTrue(calendar.events.isEmpty())
        // An all-day task for today is still allowed until the day ends.
        repository.create(draft(allDay = true))
    }

    @Test fun `editing updates the same event and changing calendar moves it`() = runTest {
        val id = repository.create(draft())
        assertEquals(id, repository.update(tasks().single(), draft(title = "Dentist appointment")))
        assertEquals("Dentist appointment", calendar.events.getValue(id).title)

        val moved = repository.update(tasks().single(), draft(calendarId = 1))
        assertNull(calendar.events[id])
        assertEquals(1L, calendar.events.getValue(moved).calendarId)
        assertEquals(setOf(moved), calendar.tagged)
    }

    @Test fun `deleting a task deletes its event`() = runTest {
        repository.create(draft())
        repository.delete(tasks().single())
        assertTrue(calendar.events.isEmpty()); assertTrue(tasks().isEmpty())
    }

    @Test fun `tasks are read from the calendar, so changes and deletions there are followed`() = runTest {
        val id = repository.create(draft())
        calendar.events[id] = calendar.events.getValue(id).copy(title = "Moved", start = at("2026-10-10T18:00:00Z"), end = at("2026-10-10T19:00:00Z"))
        assertEquals("Moved", tasks().single().task.title)
        calendar.events.remove(id)
        assertTrue(tasks().isEmpty())
    }

    @Test fun `ended tasks are no longer listed but keep their events`() = runTest {
        val timed = repository.create(draft())
        val allDay = repository.create(draft(title = "Holiday", allDay = true))
        now = Instant.parse("2026-10-10T17:30:00Z") // in progress
        assertEquals(2, tasks().size)
        now = Instant.parse("2026-10-10T17:45:00Z")
        assertEquals(listOf(allDay), tasks().map { it.task.eventId })
        now = Instant.parse("2026-10-11T03:00:00Z") // midnight in São Paulo
        assertTrue(tasks().isEmpty())
        assertEquals(setOf(timed, allDay), calendar.events.keys)
    }

    @Test fun `repeating tasks save their rule and are listed at the next occurrence`() = runTest {
        // Saturday Oct 10 is not a Monday, so the series starts on Monday Oct 12.
        val id = repository.create(draft(repeat = Repeat(RepeatFrequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY))))
        val event = calendar.events.getValue(id)
        assertEquals(at("2026-10-12T17:00:00Z"), event.start)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", event.rrule)
        calendar.occurrences[id] = listOf(occurrence("2026-10-12T17:00:00Z"), occurrence("2026-10-19T17:00:00Z"))
        now = Instant.parse("2026-10-13T12:00:00Z")
        val item = tasks().single()
        assertEquals(at("2026-10-19T17:00:00Z"), item.next.start)
        assertTrue(item.hasPastOccurrences)
    }

    @Test fun `a repeating task is listed until its last occurrence ends`() = runTest {
        val id = repository.create(draft(repeat = Repeat(RepeatFrequency.DAILY, until = LocalDate.of(2026, 10, 11))))
        calendar.occurrences[id] = listOf(occurrence("2026-10-10T17:00:00Z"), occurrence("2026-10-11T17:00:00Z"))
        now = Instant.parse("2026-10-11T12:00:00Z")
        assertEquals(1, tasks().size)
        now = Instant.parse("2026-10-11T18:00:00Z")
        assertTrue(tasks().isEmpty())
        assertTrue(id in calendar.events)
    }

    @Test fun `editing a repeating task with past occurrences splits it at the next occurrence`() = runTest {
        val id = repository.create(draft(repeat = Repeat(RepeatFrequency.DAILY)))
        calendar.occurrences[id] = listOf(occurrence("2026-10-10T17:00:00Z"), occurrence("2026-10-11T17:00:00Z"), occurrence("2026-10-12T17:00:00Z"))
        now = Instant.parse("2026-10-11T12:00:00Z")
        val item = tasks().single()
        val newId = repository.update(item, draft(title = "Dentist, new time", time = LocalTime.of(16, 0), date = LocalDate.of(2026, 10, 11),
            repeat = Repeat(RepeatFrequency.DAILY)))
        // The old series keeps Oct 10 and stops one second before the Oct 11 occurrence.
        assertEquals("FREQ=DAILY;UNTIL=20261011T165959Z", calendar.events.getValue(id).rrule)
        assertEquals(at("2026-10-11T17:00:00Z"), calendar.ended[id])
        assertEquals(setOf(newId), calendar.tagged)
        assertEquals(at("2026-10-11T19:00:00Z"), calendar.events.getValue(newId).start)
    }

    @Test fun `a repeating task without past occurrences is edited in place`() = runTest {
        val id = repository.create(draft(repeat = Repeat(RepeatFrequency.DAILY)))
        assertEquals(id, repository.update(tasks().single(), draft(title = "Renamed", repeat = Repeat(RepeatFrequency.DAILY))))
        assertEquals("Renamed", calendar.events.getValue(id).title)
        assertTrue(calendar.ended.isEmpty())
    }

    @Test fun `deleting a repeating task keeps past occurrences`() = runTest {
        val id = repository.create(draft(repeat = Repeat(RepeatFrequency.DAILY)))
        calendar.occurrences[id] = listOf(occurrence("2026-10-10T17:00:00Z"), occurrence("2026-10-11T17:00:00Z"))
        now = Instant.parse("2026-10-11T12:00:00Z")
        repository.delete(tasks().single())
        assertEquals("FREQ=DAILY;UNTIL=20261011T165959Z", calendar.events.getValue(id).rrule)
        assertTrue(tasks().isEmpty())

        // With nothing in the past, the whole series goes.
        now = Instant.parse("2026-10-10T12:00:00Z")
        val fresh = repository.create(draft(repeat = Repeat(RepeatFrequency.DAILY)))
        repository.delete(tasks().single())
        assertNull(calendar.events[fresh])
    }

    @Test fun `repeating drafts may start in the past but not end before they start`() {
        val daily = Repeat(RepeatFrequency.DAILY)
        assertNull(draft(time = LocalTime.of(8, 0), repeat = daily).problem(clock))
        assertEquals("The repeat must end on or after the first date.",
            draft(date = LocalDate.of(2026, 10, 12), repeat = daily.copy(until = LocalDate.of(2026, 10, 11))).problem(clock))
        assertEquals("This repeat has already ended. Pick a later end date.",
            draft(date = LocalDate.of(2026, 10, 1), repeat = daily.copy(until = LocalDate.of(2026, 10, 9))).problem(clock))
    }

    @Test fun `calendar events skip tasks, hidden calendars, ended events, and days past the window`() = runTest {
        val task = repository.create(draft())
        calendar.tagged += 77 // an ended part of a split series
        calendar.agenda += listOf(
            agendaEvent(1, "Holiday lunch", "2026-10-10T15:00:00Z", "2026-10-10T16:00:00Z"),
            agendaEvent(2, "Ended", "2026-10-10T10:00:00Z", "2026-10-10T11:00:00Z"),
            agendaEvent(task, "Dentist", "2026-10-10T17:00:00Z", "2026-10-10T17:45:00Z", calendarId = 2),
            agendaEvent(50, "Moved dentist", "2026-10-11T17:00:00Z", "2026-10-11T17:45:00Z", calendarId = 2, originalId = task),
            agendaEvent(77, "Old series", "2026-10-11T17:00:00Z", "2026-10-11T17:45:00Z", calendarId = 2),
            agendaEvent(3, "Day 13", "2026-10-23T15:00:00Z", "2026-10-23T16:00:00Z"),
            agendaEvent(4, "Day 14", "2026-10-24T15:00:00Z", "2026-10-24T16:00:00Z"),
        )
        assertEquals(listOf("Holiday lunch", "Day 13"), repository.events(14, calendar.calendars).map { it.title })
        assertEquals(listOf("Holiday lunch", "Day 13", "Day 14"), repository.events(28, calendar.calendars).map { it.title })
        repository.setHiddenCalendars(setOf(3))
        assertTrue(repository.events(14, calendar.calendars).isEmpty())
    }

    @Test fun `read-only calendars are never the default for tasks`() {
        store.default = 3
        assertEquals(2L, repository.defaultCalendar(calendar.calendars)?.id)
    }

    @Test fun `default calendar falls back to the account's main calendar`() {
        assertEquals(2L, repository.defaultCalendar(calendar.calendars)?.id)
        repository.setDefaultCalendar(1)
        assertEquals(1L, repository.defaultCalendar(calendar.calendars)?.id)
        repository.setDefaultCalendar(99)
        assertEquals(2L, repository.defaultCalendar(calendar.calendars)?.id)
    }
}
