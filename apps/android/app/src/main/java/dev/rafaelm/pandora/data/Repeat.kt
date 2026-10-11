package dev.rafaelm.pandora.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

enum class RepeatFrequency(val unit: String) { DAILY("day"), WEEKLY("week"), MONTHLY("month"), YEARLY("year") }

private val dayCodes = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
    "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
)
private val untilTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
val workweek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** The repeat rules Pandora can edit; any other RRULE from Google Calendar is kept verbatim. */
data class Repeat(
    val frequency: RepeatFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
) {
    /** Weekly rules without chosen days repeat on the first date's weekday. */
    fun days(start: LocalDate): Set<DayOfWeek> = if (frequency == RepeatFrequency.WEEKLY) weekdays.ifEmpty { setOf(start.dayOfWeek) } else emptySet()

    /** The first date the rule includes, so a weekly series never starts on an unchosen day. */
    fun firstDate(date: LocalDate): LocalDate =
        if (frequency != RepeatFrequency.WEEKLY) date else generateSequence(date) { it.plusDays(1) }.first { it.dayOfWeek in days(date) }

    fun rrule(start: LocalDate, allDay: Boolean, zone: ZoneId): String = buildString {
        append("FREQ=${frequency.name}")
        if (interval > 1) append(";INTERVAL=$interval")
        if (frequency == RepeatFrequency.WEEKLY) {
            append(";BYDAY=" + days(start).sorted().joinToString(",") { day -> dayCodes.entries.first { it.value == day }.key })
        }
        until?.let {
            // RFC 5545: all-day series end on a date; timed series end at a UTC time, here the end of that local day.
            append(";UNTIL=" + if (allDay) it.format(DateTimeFormatter.BASIC_ISO_DATE)
                else it.plusDays(1).atStartOfDay(zone).minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC).format(untilTime) + "Z")
        }
    }

    fun label(start: LocalDate): String {
        val every = if (interval == 1) null else "every $interval ${frequency.unit}s"
        val base = when (frequency) {
            RepeatFrequency.DAILY -> "Repeats ${every ?: "daily"}"
            RepeatFrequency.WEEKLY -> if (interval == 1 && days(start) == workweek) "Repeats every weekday"
                else "Repeats ${every ?: "weekly"} on ${days(start).sorted().joinToString { it.shortName() }}"
            RepeatFrequency.MONTHLY -> "Repeats ${every ?: "monthly"} on day ${start.dayOfMonth}"
            RepeatFrequency.YEARLY -> "Repeats ${every ?: "yearly"} on ${start.format(DateTimeFormatter.ofPattern("MMM d"))}"
        }
        return base + (until?.let { " until ${it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}" } ?: "")
    }

    companion object {
        /** Returns null for rules Pandora cannot edit, such as COUNT or "2nd Tuesday". */
        fun parse(rule: String, zone: ZoneId): Repeat? {
            val parts = rule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }
                .associate { it.substringBefore('=').uppercase() to it.substringAfter('=').uppercase() }
            if (parts.keys.any { it !in setOf("FREQ", "INTERVAL", "BYDAY", "UNTIL", "WKST") }) return null
            val frequency = RepeatFrequency.entries.firstOrNull { it.name == parts["FREQ"] } ?: return null
            val interval = parts["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n > 0 } ?: return null } ?: 1
            val days = parts["BYDAY"]?.split(',')?.map { dayCodes[it] ?: return null }?.toSet().orEmpty()
            if (days.isNotEmpty() && frequency != RepeatFrequency.WEEKLY) return null
            val until = parts["UNTIL"]?.let { until(it, zone) ?: return null }
            return Repeat(frequency, interval, days, until)
        }

        private fun until(value: String, zone: ZoneId): LocalDate? = try {
            when {
                value.length == 8 -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), untilTime).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate()
                else -> LocalDateTime.parse(value, untilTime).toLocalDate()
            }
        } catch (_: Exception) { null }
    }
}

/**
 * Ends any repeat rule, including ones Pandora cannot edit, just before [next], so that occurrence
 * and the ones after it can be replaced or dropped while earlier ones stay.
 */
fun endBefore(rule: String, next: Occurrence, zone: ZoneId): String {
    val kept = rule.removePrefix("RRULE:").split(';')
        .filter { it.isNotBlank() && it.substringBefore('=').uppercase() !in setOf("UNTIL", "COUNT") }
    val until = if (next.allDay) next.date(zone).minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
        else Instant.ofEpochMilli(next.start - 1_000).atZone(ZoneOffset.UTC).format(untilTime) + "Z"
    return (kept + "UNTIL=$until").joinToString(";")
}
