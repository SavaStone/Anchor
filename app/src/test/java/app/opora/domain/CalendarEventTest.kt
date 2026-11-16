package app.opora.domain

import app.opora.data.db.AppointmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CalendarEventTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val begin = 1_800_000_000_000L

    @Test
    fun endTimeIsBeginPlusDuration() {
        val event = calendarEvent(
            title = "Psychiatrist",
            doctor = "",
            place = "",
            notes = "",
            beginMillis = begin,
            durationMinutes = 50,
            timeZone = zone
        )
        assertEquals(begin, event.beginMillis)
        assertEquals(begin + 50 * 60_000L, event.endMillis)
        assertEquals("Europe/Moscow", event.timeZone)
    }

    @Test
    fun extrasCarryTitleTimesTimezoneAndAllDayFalse() {
        val extras = calendarEvent("Психиатр", "", "", "", begin, 30, zone).extras()
        assertEquals("Психиатр", extras[CalendarExtra.TITLE])
        assertEquals(begin, extras[CalendarExtra.EVENT_BEGIN_TIME])
        assertEquals(begin + 30 * 60_000L, extras[CalendarExtra.EVENT_END_TIME])
        assertEquals("Europe/Moscow", extras[CalendarExtra.EVENT_TIMEZONE])
        assertEquals(false, extras[CalendarExtra.EVENT_ALL_DAY])
    }

    @Test
    fun blankLocationAndNotesAreOmitted() {
        val extras = calendarEvent("Приём", "", "  ", "  ", begin, 50, zone).extras()
        assertNull(extras[CalendarExtra.EVENT_LOCATION])
        assertNull(extras[CalendarExtra.DESCRIPTION])
    }

    @Test
    fun descriptionCombinesNotesAndDoctor() {
        val event = calendarEvent(
            "Приём",
            doctor = "Елена Сергеевна",
            place = "ПНД, каб. 214",
            notes = "Взять дневник",
            beginMillis = begin,
            durationMinutes = 50,
            timeZone = zone
        )
        assertEquals("Взять дневник\n\nЕлена Сергеевна", event.description)
        assertEquals("ПНД, каб. 214", event.location)
    }

    @Test
    fun negativeDurationDoesNotMoveEndBeforeBegin() {
        val event = calendarEvent("Приём", "", "", "", begin, -10, zone)
        assertEquals(begin, event.endMillis)
    }

    @Test
    fun appointmentEntityMapsToCalendarEvent() {
        val med = AppointmentEntity(
            title = "Психотерапия",
            doctor = "Анна",
            place = "онлайн",
            startsAt = begin,
            durationMinutes = 60,
            notes = "границы"
        )
        val extras = med.toCalendarEvent(zone).extras()
        assertEquals("Психотерапия", extras[CalendarExtra.TITLE])
        assertEquals(begin + 3_600_000L, extras[CalendarExtra.EVENT_END_TIME])
        assertTrue((extras[CalendarExtra.DESCRIPTION] as String).contains("Анна"))
        assertFalse(extras[CalendarExtra.EVENT_ALL_DAY] as Boolean)
    }
}
