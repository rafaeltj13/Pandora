package dev.rafaelm.pandora.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RepeatTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val tuesday = LocalDate.of(2026, 10, 13)

    @Test fun `rules use RFC 5545 with UTC end times for timed series`() {
        assertEquals("FREQ=DAILY", Repeat(RepeatFrequency.DAILY).rrule(tuesday, false, zone))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE",
            Repeat(RepeatFrequency.WEEKLY, 2, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)).rrule(tuesday, false, zone))
        assertEquals("FREQ=WEEKLY;BYDAY=TU", Repeat(RepeatFrequency.WEEKLY).rrule(tuesday, false, zone))
        // The end of Dec 31 in São Paulo (UTC-3) is 02:59:59 UTC on Jan 1.
        assertEquals("FREQ=MONTHLY;UNTIL=20270101T025959Z",
            Repeat(RepeatFrequency.MONTHLY, until = LocalDate.of(2026, 12, 31)).rrule(tuesday, false, zone))
        assertEquals("FREQ=YEARLY;UNTIL=20261231", Repeat(RepeatFrequency.YEARLY, until = LocalDate.of(2026, 12, 31)).rrule(tuesday, true, zone))
    }

    @Test fun `editable rules parse back, including ones written by Google Calendar`() {
        val repeat = Repeat(RepeatFrequency.WEEKLY, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalDate.of(2026, 12, 31))
        assertEquals(repeat, Repeat.parse(repeat.rrule(tuesday, false, zone), zone))
        assertEquals(Repeat(RepeatFrequency.WEEKLY, weekdays = setOf(DayOfWeek.TUESDAY)), Repeat.parse("RRULE:FREQ=WEEKLY;WKST=SU;BYDAY=TU", zone))
        assertEquals(Repeat(RepeatFrequency.DAILY, until = LocalDate.of(2026, 10, 20)), Repeat.parse("FREQ=DAILY;UNTIL=20261020", zone))
    }

    @Test fun `rules Pandora cannot edit are left to Google Calendar`() {
        assertNull(Repeat.parse("FREQ=DAILY;COUNT=5", zone))
        assertNull(Repeat.parse("FREQ=MONTHLY;BYDAY=2TU", zone))
        assertNull(Repeat.parse("FREQ=MONTHLY;BYMONTHDAY=1,15", zone))
        assertNull(Repeat.parse("FREQ=HOURLY", zone))
    }

    @Test fun `weekly series start on the first chosen weekday`() {
        val repeat = Repeat(RepeatFrequency.WEEKLY, weekdays = setOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY))
        assertEquals(LocalDate.of(2026, 10, 15), repeat.firstDate(tuesday))
        assertEquals(tuesday, Repeat(RepeatFrequency.MONTHLY).firstDate(tuesday))
    }

    @Test fun `labels describe the repeat`() {
        assertEquals("Repeats daily", Repeat(RepeatFrequency.DAILY).label(tuesday))
        assertEquals("Repeats every weekday", Repeat(RepeatFrequency.WEEKLY, weekdays = workweek).label(tuesday))
        assertEquals("Repeats every 2 weeks on Mon, Wed",
            Repeat(RepeatFrequency.WEEKLY, 2, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)).label(tuesday))
        assertEquals("Repeats monthly on day 13", Repeat(RepeatFrequency.MONTHLY).label(tuesday))
        assertEquals("Repeats every 3 days until Oct 31, 2026", Repeat(RepeatFrequency.DAILY, 3, until = LocalDate.of(2026, 10, 31)).label(tuesday))
    }

    @Test fun `ending a series before an occurrence replaces any end and keeps the rest of the rule`() {
        val next = Occurrence(java.time.Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(), java.time.Instant.parse("2026-10-14T13:00:00Z").toEpochMilli(), false)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261014T115959Z", endBefore("FREQ=WEEKLY;BYDAY=MO,WE;COUNT=10", next, zone))
        assertEquals("FREQ=MONTHLY;BYDAY=2TU;UNTIL=20261014T115959Z", endBefore("RRULE:FREQ=MONTHLY;UNTIL=20271231T000000Z;BYDAY=2TU", next, zone))
        val allDay = Occurrence(LocalDate.of(2026, 10, 14).toEpochDay() * 86_400_000, LocalDate.of(2026, 10, 15).toEpochDay() * 86_400_000, true)
        assertEquals("FREQ=DAILY;UNTIL=20261013", endBefore("FREQ=DAILY", allDay, zone))
    }
}
