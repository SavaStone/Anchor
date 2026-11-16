package app.opora.ui

import app.opora.ui.screens.shouldOpenMoodEditor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoodCalendarTapTest {

    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun todayAlwaysOpensEditor() {
        assertTrue(shouldOpenMoodEditor(today, today, hasEntries = false))
        assertTrue(shouldOpenMoodEditor(today, today, hasEntries = true))
    }

    @Test
    fun emptyYesterdayOpensEditor() {
        assertTrue(
            shouldOpenMoodEditor(today.minusDays(1), today, hasEntries = false)
        )
    }

    @Test
    fun filledYesterdayOnlySelects() {
        assertFalse(
            shouldOpenMoodEditor(today.minusDays(1), today, hasEntries = true)
        )
    }

    @Test
    fun olderDaysOnlySelect() {
        assertFalse(shouldOpenMoodEditor(today.minusDays(2), today, hasEntries = false))
        assertFalse(shouldOpenMoodEditor(today.minusMonths(1), today, hasEntries = false))
    }

    @Test
    fun futureDayOnlySelects() {
        assertFalse(shouldOpenMoodEditor(today.plusDays(1), today, hasEntries = false))
    }
}
