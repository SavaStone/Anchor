package app.opora.domain

import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.MedicationPauseEntity
import app.opora.data.db.ScheduleSnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DoseStreakTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 13)

    private fun med(
        active: Boolean,
        createdAt: LocalDate,
        discontinuedAt: LocalDate? = null,
        timesCsv: String = "09:00"
    ): MedicationEntity = MedicationEntity(
        id = "med",
        name = "Арипипразол",
        timesCsv = timesCsv,
        startDate = createdAt.toEpochDay(),
        createdAt = createdAt.atStartOfDay(zone).toInstant().toEpochMilli(),
        active = active,
        discontinuedAt = discontinuedAt?.atTime(13, 0)
            ?.atZone(zone)?.toInstant()?.toEpochMilli()
    )

    private fun taken(date: LocalDate, time: String = "09:00"): DoseLogEntity = DoseLogEntity(
        medicationId = "med",
        scheduledDate = date.toEpochDay(),
        scheduledTime = time,
        status = "taken"
    )

    @Test
    fun discontinuedMedicationKeepsDaysUpToStopMoment() {
        val createdAt = today.minusDays(4)
        val med = med(active = false, createdAt = createdAt, discontinuedAt = today)
        val logs = (4L downTo 0L).map { taken(today.minusDays(it)) }

        val streak = doseStreak(
            meds = listOf(med),
            logs = logs,
            forgivenDays = emptySet(),
            today = today,
            now = today.atTime(13, 0)
        )

        assertTrue(streak.hasData)
        assertEquals(5, streak.current)
        assertEquals(1, streak.days.last().expected)
        assertEquals(1, streak.days.last().taken)
    }


    @Test
    fun snapshotKeepsOldTimeAfterScheduleChange() {
        val day = today.minusDays(1)
        val med = med(active = true, createdAt = today.minusDays(3), timesCsv = "12:00")
        val snapshot = ScheduleSnapshotEntity(
            date = day.toEpochDay(),
            medicationId = med.id,
            timesCsv = "09:00"
        )
        val streak = doseStreak(
            meds = listOf(med),
            logs = listOf(taken(day, "09:00")),
            forgivenDays = emptySet(),
            today = today,
            now = today.atTime(13, 0),
            snapshots = listOf(snapshot)
        )

        val state = streak.days.first { it.date == day }
        assertEquals(1, state.expected)
        assertTrue(state.complete)
    }

    @Test
    fun pauseGapIsRestDayAndDoesNotBurnStreak() {
        val day1 = today.minusDays(2)
        val day2 = today.minusDays(1)
        val med = med(active = true, createdAt = today.minusDays(3), timesCsv = "09:00")
        val snapshots = listOf(day1, day2, today).map { day ->
            ScheduleSnapshotEntity(date = day.toEpochDay(), medicationId = med.id, timesCsv = "09:00")
        }
        val pauseStart = day2.atStartOfDay(zone).toInstant().toEpochMilli()
        val pauseEnd = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val pause = MedicationPauseEntity(
            medicationId = med.id,
            startAt = pauseStart,
            endAt = pauseEnd
        )
        val streak = doseStreak(
            meds = listOf(med),
            logs = listOf(taken(day1, "09:00")),
            forgivenDays = emptySet(),
            today = today,
            now = today.atTime(13, 0),
            snapshots = snapshots,
            pauses = listOf(pause)
        )

        val pausedDay = streak.days.first { it.date == day2 }
        assertEquals(0, pausedDay.expected)
        assertTrue(pausedDay.restDay)
        assertTrue(streak.days.first { it.date == day1 }.complete)
        assertEquals(1, streak.current)
    }

    @Test
    fun snapshotAfterDiscontinueStillCountsPastDay() {
        val day = today.minusDays(1)
        val med = med(active = false, createdAt = today.minusDays(3), discontinuedAt = today)
        val snapshot = ScheduleSnapshotEntity(
            date = day.toEpochDay(),
            medicationId = med.id,
            timesCsv = "09:00"
        )
        val streak = doseStreak(
            meds = listOf(med),
            logs = listOf(taken(day, "09:00")),
            forgivenDays = emptySet(),
            today = today,
            now = today.atTime(13, 0),
            snapshots = listOf(snapshot)
        )

        assertTrue(streak.days.first { it.date == day }.complete)
    }

    @Test
    fun doseAfterStopMomentIsNotExpected() {
        val med = med(
            active = false,
            createdAt = today.minusDays(2),
            discontinuedAt = today
        ).copy(
            discontinuedAt = today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        )

        val streak = doseStreak(
            meds = listOf(med),
            logs = listOf(taken(today.minusDays(1))),
            forgivenDays = emptySet(),
            today = today,
            now = today.atTime(13, 0)
        )

        // Приём в 09:00 отменён в 08:00 — сегодня ничего не ждём.
        assertEquals(0, streak.days.last().expected)
        assertEquals(1, streak.current)
    }

    @Test
    fun pausedMedicationIsNotExpected() {
        val med = med(active = false, createdAt = today.minusDays(3))

        val streak = doseStreak(
            meds = listOf(med),
            logs = emptyList(),
            forgivenDays = emptySet(),
            today = today
        )

        assertFalse(streak.hasData)
        assertEquals(0, streak.current)
    }

    @Test
    fun activeMedicationCountsTakenDay() {
        val med = med(active = true, createdAt = today.minusDays(3))

        val streak = doseStreak(
            meds = listOf(med),
            logs = listOf(taken(today)),
            forgivenDays = emptySet(),
            today = today,
            now = today.atTime(13, 0)
        )

        assertEquals(1, streak.current)
        assertEquals(1, streak.days.last().expected)
    }
}
