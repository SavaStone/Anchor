package app.opora.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class JournalDateLabelTest {
    private val today = LocalDate.of(2026, 11, 13)
    private val ru = Locale.forLanguageTag("ru")
    private val en = Locale.ENGLISH

    @Test
    fun currentYearOmitsYear() {
        assertEquals("13 ноября", LocalDate.of(2026, 11, 13).journalDateLabel(today, ru))
        assertEquals("Nov 13", LocalDate.of(2026, 11, 13).journalDateLabel(today, en))
    }

    @Test
    fun otherYearIncludesYear() {
        assertEquals("13 ноября 2025", LocalDate.of(2025, 11, 13).journalDateLabel(today, ru))
        assertEquals("Nov 13, 2025", LocalDate.of(2025, 11, 13).journalDateLabel(today, en))
    }
}
