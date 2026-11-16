package app.opora.domain

import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DoseStreakPerfTest {
    @Test
    fun fourHundredDaysWith120Meds() {
        val today = LocalDate.of(2026, 11, 14)
        val meds = (0 until 120).map { index ->
            MedicationEntity(
                id = "perf-med-$index",
                name = "Med $index",
                timesCsv = "08:00,20:00",
                startDate = today.minusDays(400).toEpochDay()
            )
        }
        val logs = (0 until 5000).map { index ->
            val day = today.minusDays((index % 400).toLong())
            DoseLogEntity(
                id = "perf-dose-$index",
                medicationId = meds[index % meds.size].id,
                medicationName = "Med ${index % meds.size}",
                scheduledDate = day.toEpochDay(),
                scheduledTime = if (index % 2 == 0) "08:00" else "20:00",
                status = if (index % 4 == 0) "missed" else "taken",
                createdAt = day.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        }
        repeat(3) {
            doseStreak(meds, logs, emptySet(), today, today.atTime(22, 0))
        }
        val started = System.nanoTime()
        repeat(5) {
            doseStreak(meds, logs, emptySet(), today, today.atTime(22, 0))
        }
        val ms = (System.nanoTime() - started) / 1_000_000.0
        println("DOSE_STREAK_MS=${"%.2f".format(ms)} avg=${"%.2f".format(ms / 5)}")

        val cache = DoseStreakCache()
        val states = cache.statesFor(meds, logs, emptySet(), emptyList(), emptyList(), today)
        repeat(3) { doseStreak(meds, logs, emptySet(), today, today.atTime(22, 0), dayCache = states) }
        val cachedStarted = System.nanoTime()
        repeat(5) {
            doseStreak(meds, logs, emptySet(), today, today.atTime(22, 0), dayCache = states)
        }
        val cachedMs = (System.nanoTime() - cachedStarted) / 1_000_000.0
        println("DOSE_STREAK_CACHED_MS=${"%.2f".format(cachedMs)} avg=${"%.2f".format(cachedMs / 5)}")
    }
}
