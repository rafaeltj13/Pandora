package dev.rafaelm.pandora.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.rafaelm.pandora.data.AgendaEvent
import dev.rafaelm.pandora.data.Occurrence
import dev.rafaelm.pandora.data.Task
import dev.rafaelm.pandora.data.TaskCalendar
import dev.rafaelm.pandora.data.TaskItem
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TasksScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneId.of("UTC"))
    private val calendar = TaskCalendar(2, "me@gmail.com", "me@gmail.com", true)
    private val hour = 3_600_000L
    private val today = Instant.parse("2026-10-10T14:00:00Z").toEpochMilli()
    private val loaded = TasksState(access = true, loaded = true, calendars = listOf(calendar), defaultCalendarId = 2, tasks = listOf(
        TaskItem(Task(10, 2, "Dentist", "Bring the card", today, today + hour, false)),
        TaskItem(Task(11, 2, "Call bank", "", today + 24 * hour, today + 25 * hour, false)),
    ))
    private val editor = TaskEditor(date = LocalDate.of(2026, 10, 10), time = LocalTime.of(13, 0), calendarId = 2)

    @Test
    fun tasksAreGroupedByDay() {
        var opened: TaskItem? = null
        compose.setContent { PandoraTheme { TasksScreen(loaded, TasksActions(open = { opened = it }), clock) } }
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Dentist").assertIsDisplayed()
        compose.onNodeWithText("Tomorrow").assertIsDisplayed()
        compose.onNodeWithTag("task:11").performClick()
        assertEquals(loaded.tasks[1], opened)
    }

    @Test
    fun deletingAsksForConfirmationFirst() {
        var deleted: TaskItem? = null
        val item = loaded.tasks[1]
        compose.setContent { PandoraTheme { TasksScreen(loaded.copy(editor = editor.copy(item = item, title = "Call bank")),
            TasksActions(delete = { deleted = it }), clock) } }
        compose.onNodeWithText("Delete task").performScrollTo().performClick()
        compose.onNodeWithText("“Call bank” will be deleted from Google Calendar.").assertIsDisplayed()
        assertEquals(null, deleted)
        compose.onNodeWithTag("confirm_delete").performClick()
        assertEquals(item, deleted)
    }

    @Test
    fun repeatingTasksWithPastOccurrencesChangeFromTheNextOne() {
        val series = Task(12, 2, "Gym", "", today - 48 * hour, today - 47 * hour, false, "FREQ=DAILY")
        val item = TaskItem(series, Occurrence(today, today + hour, false))
        compose.setContent { PandoraTheme { TasksScreen(loaded.copy(tasks = listOf(item), editor = editor.copy(item = item, title = "Gym")),
            TasksActions(), clock) } }
        compose.onNodeWithText("Changes apply from Oct 10, 2026 on. Earlier events stay as they are.").assertIsDisplayed()
        compose.onNodeWithText("Delete task").performScrollTo().performClick()
        compose.onNodeWithText("“Gym” will stop repeating from Oct 10, 2026. Earlier events stay in your calendar.").assertIsDisplayed()
    }

    @Test
    fun formRequiresATitleAndShowsTheCalendar() {
        compose.setContent { PandoraTheme { TasksScreen(loaded.copy(editor = editor), TasksActions(), clock) } }
        compose.onNodeWithTag("save_task").assertIsNotEnabled()
        compose.onNodeWithText("me@gmail.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("duration:30").assertIsDisplayed()
    }

    @Test
    fun withoutAccessTheScreenAsksForCalendarPermission() {
        compose.setContent { PandoraTheme { TasksScreen(TasksState(), TasksActions(), clock) } }
        compose.onNodeWithTag("allow_calendar").assertIsDisplayed()
        compose.onNodeWithTag("new_task").assertDoesNotExist()
    }

    @Test
    fun calendarEventsAppearWithRepeatCuesAndOpenInTheCalendarApp() {
        val standup = AgendaEvent(20, null, "Standup", 5, "Team", 0xFF3366CC.toInt(), Occurrence(today + 2 * hour, today + 3 * hour, false), repeating = true)
        val weekly = loaded.tasks[0].copy(task = loaded.tasks[0].task.copy(rrule = "FREQ=WEEKLY;BYDAY=SA"))
        var opened: AgendaEvent? = null
        var showEvents: Boolean? = null
        compose.setContent { PandoraTheme { TasksScreen(loaded.copy(tasks = listOf(weekly), events = listOf(standup)),
            TasksActions(openEvent = { opened = it }, setShowEvents = { showEvents = it }), clock) } }
        compose.onNodeWithText("🔁 Repeats weekly on Sat").assertIsDisplayed()
        compose.onNodeWithText("Standup").assertIsDisplayed()
        compose.onNodeWithContentDescription("Repeats").assertIsDisplayed()
        compose.onNodeWithTag("event:20").performClick()
        assertEquals(standup, opened)
        compose.onNodeWithTag("filter_tasks").performClick()
        assertEquals(false, showEvents)
    }

    @Test
    fun tasksFilterHidesCalendarEvents() {
        val event = AgendaEvent(20, null, "Standup", 5, "Team", 0, Occurrence(today + 2 * hour, today + 3 * hour, false), repeating = false)
        compose.setContent { PandoraTheme { TasksScreen(loaded.copy(events = listOf(event), showEvents = false), TasksActions(), clock) } }
        compose.onNodeWithText("Dentist").assertIsDisplayed()
        compose.onNodeWithText("Standup").assertDoesNotExist()
    }
}
