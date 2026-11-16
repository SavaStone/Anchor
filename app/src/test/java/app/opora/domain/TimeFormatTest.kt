package app.opora.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class TimeFormatTest {

    @Test
    fun hhmmStaysAsciiWithNativeDigitsLocale() {
        val previous = Locale.getDefault()
        try {
            // В арабской локали %02d даёт «٠٨:٠٥»: такие значения не разбирает
            // LocalTime.parse, и напоминания о приёме молча исчезают.
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("08:05", LocalTime.of(8, 5).hhmm())
            assertEquals("21:00", LocalDateTime.of(2026, 11, 13, 21, 0).hhmm())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
