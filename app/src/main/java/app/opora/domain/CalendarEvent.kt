package app.opora.domain

import app.opora.data.db.AppointmentEntity
import java.time.ZoneId

/**
 * Calendar insert payload, independent of Android so the time arithmetic is
 * unit-testable. [extras] uses the same keys as CalendarContract.Events.
 */
data class CalendarEvent(
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val timeZone: String,
    val location: String,
    val description: String
) {
    fun extras(): Map<String, Any> = buildMap {
        put(CalendarExtra.TITLE, title)
        put(CalendarExtra.EVENT_BEGIN_TIME, beginMillis)
        put(CalendarExtra.EVENT_END_TIME, endMillis)
        put(CalendarExtra.EVENT_TIMEZONE, timeZone)
        put(CalendarExtra.EVENT_ALL_DAY, false)
        if (location.isNotBlank()) put(CalendarExtra.EVENT_LOCATION, location)
        if (description.isNotBlank()) put(CalendarExtra.DESCRIPTION, description)
    }
}

/** Names and values match android.provider.CalendarContract.Events. */
object CalendarExtra {
    const val TITLE = "title"
    const val EVENT_LOCATION = "eventLocation"
    const val DESCRIPTION = "description"
    const val EVENT_BEGIN_TIME = "beginTime"
    const val EVENT_END_TIME = "endTime"
    const val EVENT_TIMEZONE = "eventTimezone"
    const val EVENT_ALL_DAY = "allDay"
}

fun calendarEvent(
    title: String,
    doctor: String,
    place: String,
    notes: String,
    beginMillis: Long,
    durationMinutes: Int,
    timeZone: ZoneId = ZoneId.systemDefault()
): CalendarEvent {
    val end = beginMillis + durationMinutes.coerceAtLeast(0) * 60_000L
    val description = listOf(notes.trim(), doctor.trim())
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
    return CalendarEvent(
        title = title.trim(),
        beginMillis = beginMillis,
        endMillis = end,
        timeZone = timeZone.id,
        location = place.trim(),
        description = description
    )
}

fun AppointmentEntity.toCalendarEvent(
    timeZone: ZoneId = ZoneId.systemDefault()
): CalendarEvent = calendarEvent(
    title = title,
    doctor = doctor,
    place = place,
    notes = notes,
    beginMillis = startsAt,
    durationMinutes = durationMinutes,
    timeZone = timeZone
)
