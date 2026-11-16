package app.opora.domain

import app.opora.data.db.AppointmentEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentReminderTest {
    private val now = 1_000_000L
    private val appointment = AppointmentEntity(
        title = "Врач",
        startsAt = now + 30 * 60_000L,
        remindMinutesBefore = 60
    )

    @Test
    fun elapsedTriggerDoesNotPromiseFutureReminder() {
        assertFalse(appointment.reminderIsScheduled(now))
    }

    @Test
    fun futureTriggerIsScheduled() {
        assertTrue(appointment.copy(startsAt = now + 2 * 60 * 60_000L).reminderIsScheduled(now))
    }

    @Test
    fun doneAppointmentHasNoReminder() {
        assertFalse(appointment.copy(done = true).reminderIsScheduled(now))
    }
}
