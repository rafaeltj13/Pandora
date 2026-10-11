package dev.rafaelm.pandora.ui

import dev.rafaelm.pandora.data.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private val calendar = FakeCalendar()
    private val store = MemoryTaskStore()
    private val repository = TasksRepository(calendar, store,
        Clock.fixed(Instant.parse("2026-10-10T12:20:00Z"), ZoneId.of("UTC")), Dispatchers.Unconfined)
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `new tasks start at the next hour in the default calendar`() = runTest {
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        model.newTask()
        val editor = model.state.value.editor!!
        assertEquals(LocalDate.of(2026, 10, 10), editor.date)
        assertEquals(LocalTime.of(13, 0), editor.time)
        assertEquals(30, editor.durationMinutes)
        assertEquals(2L, editor.calendarId)
    }

    @Test fun `stopping drops the open form`() = runTest {
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        model.newTask(); model.stop()
        assertNull(model.state.value.editor)
        assertFalse(model.state.value.loading)
    }

    @Test fun `saving creates the event and returns to the list`() = runTest {
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        model.newTask(); model.edit(title = "Gym", durationMinutes = 60)
        model.save(); advanceUntilIdle()
        assertNull(model.state.value.editor)
        assertEquals("Gym", model.state.value.tasks.single().task.title)
        assertEquals(3_600_000L, calendar.events.values.single().let { it.end - it.start })
    }

    @Test fun `past times keep the form open with an explanation`() = runTest {
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        model.newTask(); model.edit(title = "Late", time = LocalTime.of(9, 0))
        model.save(); advanceUntilIdle()
        assertEquals("This time has already passed. Pick a later time.", model.state.value.editor!!.error)
        assertTrue(calendar.events.isEmpty())
    }

    @Test fun `without calendar access nothing loads and tasks cannot be created`() = runTest {
        calendar.access = false
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        assertFalse(model.state.value.access)
        model.newTask()
        assertNull(model.state.value.editor)
    }

    @Test fun `revoked access while saving keeps the form and asks for access again`() = runTest {
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        model.newTask(); model.edit(title = "Gym")
        calendar.access = false
        model.save(); advanceUntilIdle()
        assertFalse(model.state.value.access)
        assertEquals("Gym", model.state.value.editor!!.title)
        assertFalse(model.state.value.editor!!.saving)
    }

    @Test fun `days merge tasks and events, all-day first, and the filter hides events`() = runTest {
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        model.newTask(); model.edit(title = "Gym"); model.save(); advanceUntilIdle()
        val day = 86_400_000L
        val today = LocalDate.of(2026, 10, 10).toEpochDay() * day
        val events = listOf(
            AgendaEvent(1, null, "Lunch", 3, "Holidays", 0, Occurrence(today + 12 * 3_600_000L + 1_800_000L, today + 14 * 3_600_000L, false), false),
            AgendaEvent(2, null, "Holiday", 3, "Holidays", 0, Occurrence(today, today + day, true), true),
            AgendaEvent(3, null, "Standup", 3, "Holidays", 0, Occurrence(today + day + 9 * 3_600_000L, today + day + 10 * 3_600_000L, false), true),
        )
        val state = model.state.value.copy(events = events)
        val clock = repository.clock
        assertEquals(listOf(listOf("Holiday", "Lunch", "Gym"), listOf("Standup")), state.days(clock).map { d -> d.entries.map { it.title } })
        assertEquals(listOf(listOf("Gym")), state.copy(showEvents = false).days(clock).map { d -> d.entries.map { it.title } })
        model.setShowEvents(false)
        assertFalse(store.eventsShown)
    }

    @Test fun `a repeating task opens at its next occurrence`() = runTest {
        val model = TasksViewModel(repository)
        model.refresh(); advanceUntilIdle()
        model.newTask(); model.edit(title = "Standup", time = LocalTime.of(9, 0)); model.setRepeat(Repeat(RepeatFrequency.DAILY))
        model.save(); advanceUntilIdle()
        val (id, event) = calendar.events.entries.single()
        calendar.occurrences[id] = listOf(Occurrence(event.start, event.end, false), Occurrence(event.start + 86_400_000, event.end + 86_400_000, false))
        model.refresh(); advanceUntilIdle()
        val item = model.state.value.tasks.single()
        // 09:00 today has passed (it is 12:20), so the next occurrence is tomorrow.
        assertEquals(LocalDate.of(2026, 10, 11), item.next.date(repository.clock.zone))
        model.open(item)
        assertEquals(LocalDate.of(2026, 10, 11), model.state.value.editor!!.date)
        assertEquals(Repeat(RepeatFrequency.DAILY), model.state.value.editor!!.repeat)
    }
}
