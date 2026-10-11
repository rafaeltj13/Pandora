package dev.rafaelm.pandora.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.time.Duration
import java.util.TimeZone

val calendarPermissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

/** Marks an event as a Pandora task; extended properties travel with the event. */
private const val TASK_TAG = "dev.rafaelm.pandora.task"

/** Writes through the phone's calendar provider; Android syncs Google calendars to Google. */
class AndroidCalendar(private val context: Context) : CalendarGateway {
    private val resolver = context.contentResolver
    private val notCancelled = "IFNULL(${Instances.STATUS}, ${Events.STATUS_TENTATIVE}) != ${Events.STATUS_CANCELED}"

    override fun hasAccess() = calendarPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun calendars(): List<TaskCalendar> = resolver.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.OWNER_ACCOUNT,
            Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CALENDAR_COLOR),
        "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.SYNC_EVENTS} = 1", arrayOf("com.google"),
        "${Calendars.ACCOUNT_NAME} ASC, ${Calendars._ID} ASC",
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val account = cursor.getString(2)
                // A Google account's main calendar is the one named after, and owned by, the account.
                add(TaskCalendar(cursor.getLong(0), cursor.getString(1) ?: account, account, cursor.getString(3) == account,
                    writable = cursor.getInt(4) >= Calendars.CAL_ACCESS_CONTRIBUTOR, color = cursor.getInt(5)))
            }
        }
    }.orEmpty().sortedWith(compareBy({ it.account }, { !it.main }))

    override fun taskEventIds(): Set<Long> = resolver.query(
        ExtendedProperties.CONTENT_URI, arrayOf(ExtendedProperties.EVENT_ID), "${ExtendedProperties.NAME} = ?", arrayOf(TASK_TAG), null,
    )?.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) } }.orEmpty()

    override fun tasks(calendarIds: Set<Long>, after: Long): List<Task> {
        val ids = taskEventIds()
        if (ids.isEmpty() || calendarIds.isEmpty()) return emptyList()
        // IDs are numbers from the provider, so inlining them is safe and avoids SQLite's bind-argument limit.
        return events("${Events._ID} IN (${ids.joinToString()}) AND ${Events.CALENDAR_ID} IN (${calendarIds.joinToString()})" +
            " AND (${Events.LAST_DATE} IS NULL OR ${Events.LAST_DATE} > ?)", arrayOf(after.toString()))
            .map { (id, event) -> Task(id, event.calendarId, event.title, event.description, event.start, event.end, event.allDay, event.rrule) }
    }

    override fun event(id: Long): CalendarEvent? = events("${Events._ID} = ?", arrayOf(id.toString())).firstOrNull()?.second

    private fun events(selection: String, args: Array<String>): List<Pair<Long, CalendarEvent>> = resolver.query(
        Events.CONTENT_URI,
        arrayOf(Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.DTSTART, Events.DTEND, Events.DURATION,
            Events.ALL_DAY, Events.RRULE),
        "$selection AND ${Events.DELETED} = 0", args, null,
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val start = cursor.getLong(4)
                val end = if (!cursor.isNull(5)) cursor.getLong(5) else start + duration(cursor.getString(6))
                add(cursor.getLong(0) to CalendarEvent(cursor.getLong(1), cursor.getString(2).orEmpty(), cursor.getString(3).orEmpty(),
                    start, end, cursor.getInt(7) == 1, cursor.getString(8)?.takeIf { it.isNotBlank() }))
            }
        }
    }.orEmpty()

    override fun occurrences(eventId: Long, from: Long, to: Long): List<Occurrence> = resolver.query(
        instances(from, to), arrayOf(Instances.BEGIN, Instances.END, Instances.ALL_DAY),
        "(${Instances.EVENT_ID} = ? OR ${Instances.ORIGINAL_ID} = ?) AND $notCancelled",
        arrayOf(eventId.toString(), eventId.toString()), "${Instances.BEGIN} ASC",
    )?.use { cursor ->
        buildList { while (cursor.moveToNext()) add(Occurrence(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2) == 1)) }
    }.orEmpty()

    override fun agenda(calendarIds: Set<Long>, from: Long, to: Long): List<AgendaEvent> = resolver.query(
        instances(from, to),
        arrayOf(Instances.EVENT_ID, Instances.ORIGINAL_ID, Instances.TITLE, Instances.CALENDAR_ID, Instances.CALENDAR_DISPLAY_NAME,
            Instances.DISPLAY_COLOR, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.RRULE),
        "${Instances.CALENDAR_ID} IN (${calendarIds.joinToString { "?" }}) AND $notCancelled",
        calendarIds.map { it.toString() }.toTypedArray(), "${Instances.BEGIN} ASC",
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val originalId = if (cursor.isNull(1)) null else cursor.getLong(1)
                add(AgendaEvent(cursor.getLong(0), originalId, cursor.getString(2).orEmpty().ifBlank { "(No title)" }, cursor.getLong(3),
                    cursor.getString(4).orEmpty(), cursor.getInt(5), Occurrence(cursor.getLong(6), cursor.getLong(7), cursor.getInt(8) == 1),
                    repeating = !cursor.getString(9).isNullOrBlank() || originalId != null))
            }
        }
    }.orEmpty()

    override fun insert(event: CalendarEvent): Long {
        // One batch, so an event is never created without its tag.
        val results = resolver.applyBatch(CalendarContract.AUTHORITY, arrayListOf(
            ContentProviderOperation.newInsert(Events.CONTENT_URI)
                .withValues(values(event).apply { put(Events.CALENDAR_ID, event.calendarId) }).build(),
            ContentProviderOperation.newInsert(properties(event.calendarId))
                .withValueBackReference(ExtendedProperties.EVENT_ID, 0)
                .withValue(ExtendedProperties.NAME, TASK_TAG).withValue(ExtendedProperties.VALUE, "1").build(),
        ))
        return results[0].uri?.let(ContentUris::parseId) ?: throw TaskException("Could not create the calendar event.")
    }

    override fun update(id: Long, event: CalendarEvent) {
        if (resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values(event), null, null) == 0) {
            throw TaskException("Could not update the calendar event.")
        }
    }

    override fun endSeries(id: Long, rrule: String, from: Long) {
        val event = event(id) ?: return
        // The provider only re-expands a repeat rule when DTSTART comes with it, as the stock Calendar app sends.
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id),
            ContentValues().apply { put(Events.DTSTART, event.start); put(Events.RRULE, rrule) }, null, null)
        resolver.delete(Events.CONTENT_URI, "${Events.ORIGINAL_ID} = ? AND ${Events.ORIGINAL_INSTANCE_TIME} >= ?", arrayOf(id.toString(), from.toString()))
        resolver.delete(properties(event.calendarId), "${ExtendedProperties.EVENT_ID} = ? AND ${ExtendedProperties.NAME} = ?", arrayOf(id.toString(), TASK_TAG))
    }

    override fun delete(id: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
    }

    /** Only a calendar's sync adapter may write extended properties, so writes name the calendar's account. */
    private fun properties(calendarId: Long): Uri {
        val (name, type) = resolver.query(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId),
            arrayOf(Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }
            ?: throw TaskException("That calendar is no longer on this phone.")
        return ExtendedProperties.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, name)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, type).build()
    }

    private fun instances(from: Long, to: Long) = Instances.CONTENT_URI.buildUpon()
        .also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()

    /** Repeating events need a DURATION instead of DTEND. */
    private fun values(event: CalendarEvent) = ContentValues().apply {
        put(Events.TITLE, event.title)
        put(Events.DESCRIPTION, event.description)
        put(Events.DTSTART, event.start)
        put(Events.ALL_DAY, if (event.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else TimeZone.getDefault().id)
        if (event.rrule == null) {
            put(Events.DTEND, event.end); putNull(Events.DURATION); putNull(Events.RRULE)
        } else {
            val seconds = (event.end - event.start) / 1_000
            putNull(Events.DTEND); put(Events.RRULE, event.rrule)
            put(Events.DURATION, if (event.allDay) "P${seconds / 86_400}D" else "P${seconds}S")
        }
    }

    /** RFC 5545 durations such as P3600S or P1D, used by events that have no end time. */
    private fun duration(value: String?): Long = try {
        Duration.parse(value!!.replace(Regex("^P(\\d+)([HMS])$"), "PT$1$2")).toMillis()
    } catch (_: Exception) { 30 * 60_000L }
}
