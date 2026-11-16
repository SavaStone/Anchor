package app.opora.domain

import app.opora.data.db.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedScheduleTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun med(
        scheduleType: String = "daily",
        times: String = "09:00",
        weekdays: String = "1,2,3,4,5,6,7",
        intervalDays: Int = 1,
        endDate: LocalDate? = null,
        active: Boolean = true
    ) = MedicationEntity(
        id = "med-1",
        name = "Тест",
        scheduleType = scheduleType,
        timesCsv = times,
        weekdaysCsv = weekdays,
        intervalDays = intervalDays,
        startDate = start.toEpochDay(),
        endDate = endDate?.toEpochDay(),
        active = active
    )

    @Test
    fun `parseTimes sorts, dedupes and drops invalid values`() {
        val parsed = MedSchedule.parseTimes(med(times = "20:00, 08:00,08:00, nope, 7:30"))
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), parsed)
    }

    @Test
    fun `daily schedule fires every day after start`() {
        val m = med()
        assertTrue(MedSchedule.isScheduledOn(m, start))
        assertTrue(MedSchedule.isScheduledOn(m, start.plusDays(30)))
        assertFalse(MedSchedule.isScheduledOn(m, start.minusDays(1)))
    }

    @Test
    fun `weekly schedule respects iso weekdays`() {
        val m = med(scheduleType = "weekly", weekdays = "1,3")
        // 2026-01-01 is Thursday (4); 2026-01-05 is Monday (1)
        assertTrue(MedSchedule.isScheduledOn(m, LocalDate.of(2026, 1, 5)))
        assertFalse(MedSchedule.isScheduledOn(m, LocalDate.of(2026, 1, 6)))
        assertTrue(MedSchedule.isScheduledOn(m, LocalDate.of(2026, 1, 7)))
    }

    @Test
    fun `interval schedule counts days from start`() {
        val m = med(scheduleType = "interval", intervalDays = 3)
        assertTrue(MedSchedule.isScheduledOn(m, start))
        assertFalse(MedSchedule.isScheduledOn(m, start.plusDays(1)))
        assertTrue(MedSchedule.isScheduledOn(m, start.plusDays(3)))
        assertTrue(MedSchedule.isScheduledOn(m, start.plusDays(30)))
    }

    @Test
    fun `end date is inclusive`() {
        val m = med(endDate = start.plusDays(9))
        assertTrue(MedSchedule.isScheduledOn(m, start.plusDays(9)))
        assertFalse(MedSchedule.isScheduledOn(m, start.plusDays(10)))
    }

    @Test
    fun `as needed and inactive are never scheduled`() {
        assertFalse(MedSchedule.isScheduledOn(med(scheduleType = "as_needed"), start))
        assertFalse(MedSchedule.isScheduledOn(med(active = false), start))
        assertTrue(MedSchedule.occurrencesOn(med(scheduleType = "as_needed"), start).isEmpty())
    }

    @Test
    fun `occurrencesOn returns each configured time on that date`() {
        val m = med(times = "08:00,20:00")
        assertEquals(
            listOf(start.atTime(8, 0), start.atTime(20, 0)),
            MedSchedule.occurrencesOn(m, start)
        )
    }

    @Test
    fun `nextForTime skips today when the slot already passed`() {
        val m = med(times = "09:00")
        val after = start.atTime(10, 0)
        assertEquals(start.plusDays(1).atTime(9, 0), MedSchedule.nextForTime(m, LocalTime.of(9, 0), after))
    }

    @Test
    fun `nextForTime respects weekly schedule`() {
        val m = med(scheduleType = "weekly", weekdays = "1", times = "09:00")
        // Thursday 2026-01-01 -> next Monday 2026-01-05
        val next = MedSchedule.nextForTime(m, LocalTime.of(9, 0), start.atTime(10, 0))
        assertEquals(LocalDate.of(2026, 1, 5).atTime(9, 0), next)
    }

    @Test
    fun `nextOccurrence returns null when schedule has ended`() {
        val m = med(endDate = start.plusDays(2))
        assertNull(MedSchedule.nextOccurrence(m, start.plusDays(3).atStartOfDay()))
    }

    @Test
    fun `nextOccurrence finds interval beyond sixty days`() {
        val m = med(scheduleType = "interval", intervalDays = 90)
        val after = start.plusDays(20).atStartOfDay()

        assertEquals(
            start.plusDays(90).atTime(9, 0),
            MedSchedule.nextOccurrence(m, after)
        )
    }

    @Test
    fun `expectedDoses excludes future slots and ends today`() {
        val m = med(times = "08:00,20:00", endDate = start.plusDays(2))
        val now = start.plusDays(1).atTime(12, 0)
        val expected = MedSchedule.expectedDoses(m, start, now)
        assertEquals(
            listOf(
                start.atTime(8, 0), start.atTime(20, 0),
                start.plusDays(1).atTime(8, 0)
            ),
            expected
        )
    }

    @Test
    fun `expectedDoses is empty for as needed medication`() {
        val m = med(scheduleType = "as_needed")
        assertTrue(MedSchedule.expectedDoses(m, start, start.plusDays(5).atStartOfDay()).isEmpty())
    }

    @Test
    fun `nextOccurrenceToday only looks at today`() {
        val m = med(times = "09:00,21:00")
        val now = LocalDateTime.of(2026, 1, 1, 10, 0)
        assertEquals(now.toLocalDate().atTime(21, 0), MedSchedule.nextOccurrenceToday(m, now))
        assertNull(MedSchedule.nextOccurrenceToday(m, now.toLocalDate().atTime(22, 0)))
    }
}
