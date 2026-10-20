package dev.rafaelm.pandora.data

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Runs against the device's real calendar provider, using a throwaway Google-type calendar. */
class AndroidCalendarTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val account = "pandora.test.${UUID.randomUUID()}@gmail.com"
    private val calendar = AndroidCalendar(context)
    private var calendarId = 0L
    private val hour = 3_600_000L
    private val start = Instant.parse("2030-01-07T12:00:00Z").toEpochMilli() // a Monday

    private fun asSyncAdapter(uri: Uri) = uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, account).appendQueryParameter(Calendars.ACCOUNT_TYPE, "com.google").build()

    @Before fun createCalendar() {
        // Read the command's output so each grant has finished before the test touches the provider.
        calendarPermissions.forEach { permission ->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission"))
                .use { it.readBytes() }
        }
        val uri = context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account); put(Calendars.ACCOUNT_TYPE, "com.google"); put(Calendars.NAME, account)
            put(Calendars.CALENDAR_DISPLAY_NAME, account); put(Calendars.OWNER_ACCOUNT, account)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER); put(Calendars.SYNC_EVENTS, 1); put(Calendars.VISIBLE, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC")
        })!!
        calendarId = ContentUris.parseId(uri)
    }

    @After fun deleteCalendar() {
        context.contentResolver.delete(asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)), null, null)
    }

    @Test fun insertedEventsAreTaggedAsTasks() {
        val id = calendar.insert(CalendarEvent(calendarId, "Dentist", "Bring the card", start, start + hour, false))
        assertTrue(id in calendar.taskEventIds())
        assertEquals(listOf(Task(id, calendarId, "Dentist", "Bring the card", start, start + hour, false)),
            calendar.tasks(setOf(calendarId), start - hour))
        // Ended tasks are filtered by the provider's last occurrence date.
        assertTrue(calendar.tasks(setOf(calendarId), start + 2 * hour).isEmpty())
        assertTrue(calendar.calendars().any { it.id == calendarId && it.main && it.writable })
    }

    @Test fun legacyTaggedTasksRemainReadableAndOrdinaryEventsStayOut() {
        val legacy = calendar.insert(CalendarEvent(calendarId, "Legacy task", "Original notes", start, start + hour, false))
        // Older versions stored the task tag only in ExtendedProperties.
        context.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, legacy),
            ContentValues().apply { put(Events.DESCRIPTION, "Original notes") }, null, null)
        val ordinary = context.contentResolver.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId); put(Events.TITLE, "Ordinary event")
            put(Events.DESCRIPTION, "Original notes"); put(Events.DTSTART, start); put(Events.DTEND, start + hour)
            put(Events.EVENT_TIMEZONE, "UTC")
        })!!
        assertTrue(legacy in calendar.taskEventIds())
        assertFalse(ContentUris.parseId(ordinary) in calendar.taskEventIds())
        assertEquals("Original notes", calendar.tasks(setOf(calendarId), start - hour).single().description)
    }

    @Test fun tasksRemainDiscoverableWithoutSyncAdapterProperties() {
        val id = calendar.insert(CalendarEvent(calendarId, "Dentist", "Bring the card", start, start + hour, false))
        // A sync adapter owns this metadata and can replace it when refreshing an event.
        context.contentResolver.delete(asSyncAdapter(ExtendedProperties.CONTENT_URI),
            "${ExtendedProperties.EVENT_ID} = ?", arrayOf(id.toString()))
        val reopened = AndroidCalendar(context)
        assertTrue(id in reopened.taskEventIds())
        assertEquals("Bring the card", reopened.tasks(setOf(calendarId), start - hour).single().description)
        reopened.update(id, reopened.event(id)!!.copy(description = "Updated notes"))
        assertEquals("Updated notes", reopened.tasks(setOf(calendarId), start - hour).single().description)
    }

    @Test fun repeatingTasksExpandAndCanEndBeforeAnOccurrence() {
        val id = calendar.insert(CalendarEvent(calendarId, "Gym", "", start, start + hour, false, "FREQ=WEEKLY;BYDAY=MO,WE"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE", calendar.event(id)!!.rrule)
        assertEquals(start + hour, calendar.event(id)!!.end)
        val occurrences = calendar.occurrences(id, start, start + 3 * 24 * hour)
        assertEquals(listOf(start, start + 2 * 24 * hour), occurrences.map { it.start })
        assertEquals(listOf(id), calendar.tasks(setOf(calendarId), start + 365L * 24 * hour).map { it.eventId })

        val wednesday = occurrences[1]
        calendar.endSeries(id, endBefore("FREQ=WEEKLY;BYDAY=MO,WE", wednesday, java.time.ZoneOffset.UTC), wednesday.start)
        assertFalse(id in calendar.taskEventIds())
        assertEquals(listOf(start), calendar.occurrences(id, start, start + 14 * 24 * hour).map { it.start })
        assertEquals(listOf(start to true), calendar.agenda(setOf(calendarId), start - hour, start + 14 * 24 * hour).map { it.occurrence.start to it.repeating })
    }

    @Test fun deletingRemovesTheEvent() {
        val id = calendar.insert(CalendarEvent(calendarId, "Call bank", "", start, start + hour, false))
        calendar.delete(id)
        assertNull(calendar.event(id))
    }
}
