package app.opora.domain

import app.opora.data.db.DoseLogEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DoseTimeTest {
    private val date = LocalDate.of(2026, 11, 10)
    private val zone = ZoneId.of("Europe/Berlin")

    private fun dose(time: String = "09:00", staleMillis: Long = 0L) = DoseLogEntity(
        medicationId = "med",
        dosage = "1 таблетка",
        scheduledDate = date.toEpochDay(),
        scheduledTime = time,
        // Как будто запись создана в другом поясе и epoch больше не совпадает.
        scheduledAt = staleMillis,
        status = "pending"
    )

    @Test
    fun overdueUsesLocalScheduleNotStaleEpoch() {
        val stale = date.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val noon = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        assertTrue(dose(staleMillis = stale).isOverdue(noon, zone))
        assertFalse(dose(staleMillis = stale).isOverdue(
            date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
            zone
        ))
    }

    @Test
    fun snoozeIsAvailableOnlyAfterScheduledTime() {
        val morning = dose(time = "09:00")
        assertFalse(morning.snoozeAvailable(date.atTime(8, 59)))
        assertTrue(morning.snoozeAvailable(date.atTime(9, 0)))
        assertTrue(morning.snoozeAvailable(date.atTime(12, 0)))
        assertFalse(morning.copy(status = "taken").snoozeAvailable(date.atTime(12, 0)))
        assertFalse(morning.copy(isPrn = true).snoozeAvailable(date.atTime(12, 0)))
    }

    @Test
    fun springForwardGapResolvesToNextValidInstant() {
        val dstDate = LocalDate.of(2026, 3, 29)
        val gap = DoseLogEntity(
            medicationId = "med",
            scheduledDate = dstDate.toEpochDay(),
            scheduledTime = "02:30",
            status = "pending"
        )

        val millis = gap.scheduledEpochMillis(zone)
        val local = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
        assertTrue(local.isAfter(LocalDateTime.of(2026, 3, 29, 2, 30)))
    }
}
