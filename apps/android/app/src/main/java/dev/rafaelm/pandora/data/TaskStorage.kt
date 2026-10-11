package dev.rafaelm.pandora.data

import android.content.Context
import androidx.core.content.edit

/** Device-local display preferences for Tasks; the tasks themselves are tagged calendar events. */
class TaskStorage(context: Context, name: String = "tasks") : TaskStore {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun defaultCalendar(): Long? =
        preferences.getLong("default_calendar", -1).takeIf { it >= 0 }

    override fun setDefaultCalendar(id: Long) = preferences.edit { putLong("default_calendar", id) }

    // Hidden rather than shown calendars are stored, so newly added calendars appear by default.
    override fun hiddenCalendars(): Set<Long> =
        preferences.getStringSet("hidden_calendars", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    override fun setHiddenCalendars(ids: Set<Long>) = preferences.edit { putStringSet("hidden_calendars", ids.map { it.toString() }.toSet()) }

    override fun showEvents() = preferences.getBoolean("show_events", true)

    override fun setShowEvents(show: Boolean) = preferences.edit { putBoolean("show_events", show) }
}
