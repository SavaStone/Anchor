package app.opora.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.domain.CalendarExtra
import app.opora.domain.calendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CalendarInsertIntentTest {
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var started: Intent? = null

        override fun startActivity(intent: Intent) {
            started = intent
        }
    }

    @Test
    fun insertIntentCarriesCalendarContractFields() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val recorder = RecordingContext(base)
        val begin = 1_800_000_000_000L
        val event = calendarEvent(
            title = "Психиатр",
            doctor = "Елена Сергеевна",
            place = "ПНД, каб. 214",
            notes = "Взять дневник",
            beginMillis = begin,
            durationMinutes = 50,
            timeZone = ZoneId.of("Europe/Moscow")
        )

        assertTrue(recorder.openCalendarInsert(event))

        val intent = recorder.started!!
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Психиатр", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("ПНД, каб. 214", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertEquals(
            "Взять дневник\n\nЕлена Сергеевна",
            intent.getStringExtra(CalendarContract.Events.DESCRIPTION)
        )
        assertEquals(
            begin,
            intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
        )
        assertEquals(
            begin + 50 * 60_000L,
            intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
        )
        assertEquals(
            "Europe/Moscow",
            intent.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE)
        )
        assertFalse(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true))
        // Guard against a key typo in CalendarExtra: the platform keys must match.
        assertEquals(CalendarContract.Events.EVENT_LOCATION, CalendarExtra.EVENT_LOCATION)
    }

    @Test
    fun blankOptionalFieldsAreOmitted() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val recorder = RecordingContext(base)
        val event = calendarEvent("Приём", "", "", "", 1_800_000_000_000L, 30)

        assertTrue(recorder.openCalendarInsert(event))

        val intent = recorder.started!!
        assertNull(intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertNull(intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
    }
}
