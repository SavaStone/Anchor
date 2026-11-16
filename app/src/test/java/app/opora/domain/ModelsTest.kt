package app.opora.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class ModelsTest {

    private val ru = Locale.forLanguageTag("ru")
    private val en = Locale.ENGLISH

    @Test
    fun moodLevelMapsKnownValues() {
        assertEquals(app.opora.R.string.mood_1, moodLevel(1).labelRes)
        assertEquals(app.opora.R.string.mood_3, moodLevel(3).labelRes)
        assertEquals(app.opora.R.string.mood_5, moodLevel(5).labelRes)
    }

    @Test
    fun moodLevelFallsBackToNeutralForUnknownValue() {
        assertEquals(3, moodLevel(0).value)
        assertEquals(3, moodLevel(99).value)
    }

    @Test
    fun tagsAreSortedByRussianLabel() {
        val sorted = sortTags(
            listOf(
                TagDef("b", "яблоко"),
                TagDef("a", "апельсин"),
                TagDef("c", "ёлка")
            )
        )
        assertEquals(listOf("апельсин", "ёлка", "яблоко"), sorted.map { it.label })
        assertEquals(sortTags(EMOTIONS), EMOTIONS)
        assertEquals(sortTags(SYMPTOMS), SYMPTOMS)
    }

    @Test
    fun builtInTagsCarryStringResources() {
        assertEquals(app.opora.R.string.tag_joy, EMOTIONS.first { it.key == "joy" }.labelRes)
        assertEquals(app.opora.R.string.tag_headache, SYMPTOMS.first { it.key == "headache" }.labelRes)
        assertEquals(0, TagDef("custom_1", "антистресс").labelRes)
    }

    @Test
    fun customTagsMergeIntoAlphabeticalOrder() {
        val merged = mergeTags(
            listOf(TagDef("joy", "радость"), TagDef("calm", "спокойствие")),
            listOf(TagDef("custom_1", "антистресс"))
        )
        assertEquals(listOf("антистресс", "радость", "спокойствие"), merged.map { it.label })
    }

    @Test
    fun tagFilterKeysSortByVisibleLabel() {
        val labels = mapOf("a" to "яблоко", "b" to "апельсин", "c" to "ёлка")
        assertEquals(listOf("b", "c", "a"), sortedTagKeys(listOf("a", "b", "c"), labels))
    }

    @Test
    fun tagFilterKeysAreCaseInsensitive() {
        assertEquals(
            listOf("сон", "Тревога"),
            sortedTagKeys(listOf("Тревога", "тревога", "сон", "СОН"), emptyMap())
        )
    }

    @Test
    fun homeBlocksKeepSavedOrderAndAppendNewOnes() {
        val order = parseHomeBlocks("journal,removed_block")
        assertEquals(HomeBlock.JOURNAL, order.first())
        assertEquals(HomeBlock.MOOD, order[1])
        assertEquals(HomeBlock.entries.size, order.size)
        assertEquals(
            listOf(HomeBlock.MOOD),
            visibleHomeBlocks("journal,mood", "journal,meds,appointment")
        )
        assertEquals(HomeBlock.APPOINTMENT, HomeBlock.entries.last())
        assertEquals("appointment", DEFAULT_HIDDEN_HOME_BLOCKS)
    }

    @Test
    fun hiddenBlocksStayHiddenAfterReorder() {
        val reordered = moveHomeBlockInOrder(DEFAULT_HOME_ORDER, "meds", -1)
        val visible = visibleHomeBlocks(reordered, "appointment")
        assertEquals(HomeBlock.MEDS, visible.first())
        assertEquals(listOf(HomeBlock.MEDS, HomeBlock.MOOD, HomeBlock.JOURNAL), visible)
    }

    @Test
    fun savedMovedOrderIsReadBackInTheSameOrder() {
        val moved = moveHomeBlockInOrder(DEFAULT_HOME_ORDER, "meds", -1)
        assertEquals(HomeBlock.MEDS, parseHomeBlocks(moved).first())
        assertEquals(HomeBlock.MOOD, parseHomeBlocks(moved)[1])
    }

    @Test
    fun homeBlocksMoveOnlyInsideKnownOrder() {
        assertEquals(
            "meds,mood,journal,appointment",
            moveHomeBlockInOrder("mood,meds,journal,appointment", "meds", -1)
        )
        assertEquals(
            "mood,meds,journal,appointment",
            moveHomeBlockInOrder("mood,meds,journal,appointment", "mood", -1)
        )
        assertEquals(
            "mood,meds,journal,appointment",
            moveHomeBlockInOrder("mood,meds,journal,appointment", "appointment", 5)
        )
    }

    @Test
    fun csvHelpersToggleValues() {
        assertEquals("a,c", csvWithout("a,b,c", "b"))
        assertEquals("a,c,b", csvWith("a,c", "b"))
        assertEquals("a,b", csvWith("a,b", "a"))
    }

    @Test
    fun parseKeysTrimsAndDropsEmptyParts() {
        assertEquals(listOf("joy", "calm"), parseKeys(" joy, calm ,, "))
        assertEquals(emptyList<String>(), parseKeys(""))
    }

    @Test
    fun joinKeysDeduplicatesPreservingOrder() {
        assertEquals("a,b,c", joinKeys(listOf("a", "b", "a", "c", "b")))
    }

    @Test
    fun tagLabelReturnsRussianLabelOrRawKey() {
        assertEquals("тревога", tagLabel(EMOTIONS, "anxiety"))
        assertEquals("unknown_key", tagLabel(EMOTIONS, "unknown_key"))
    }

    @Test
    fun russianDatesUseGenitiveMonthNames() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals("8 ноября", date.dayMonth(ru))
        assertEquals("8 нояб", date.dayMonthShort(ru))
        assertEquals("Ноябрь 2026", date.monthYear(ru))
        assertEquals("воскресенье", date.weekday(ru))
        assertEquals("8 ноября 2026", date.dateWithYear(ru))
    }

    @Test
    fun englishDatesUseMonthFirst() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals("Nov 8", date.dayMonth(en))
        assertEquals("Nov 8", date.dayMonthShort(en))
        assertEquals("November 2026", date.monthYear(en))
        assertEquals("Sunday", date.weekday(en))
        assertEquals("Nov 8, 2026", date.dateWithYear(en))
    }

    @Test
    fun journalLabelDropsYearOnlyForCurrentYear() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals("13 ноября", LocalDate.of(2026, 11, 13).journalDateLabel(today, ru))
        assertEquals("13 ноября 2025", LocalDate.of(2025, 11, 13).journalDateLabel(today, ru))
        assertEquals("Nov 13", LocalDate.of(2026, 11, 13).journalDateLabel(today, en))
        assertEquals("Nov 13, 2025", LocalDate.of(2025, 11, 13).journalDateLabel(today, en))
    }

    @Test
    fun timeFormattingPadsHoursAndMinutes() {
        assertEquals("09:05", LocalTime.of(9, 5).hhmm())
        assertEquals("23:59", LocalTime.of(23, 59).hhmm())
    }

    @Test
    fun sharedDateAndTimeFormattersFollowLocale() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals("8 ноября 2026, 09:05", date.atTime(9, 5).dateTime(ru))
        assertEquals("Nov 8, 2026, 09:05", date.atTime(9, 5).dateTime(en))
        assertEquals("3,5", formatDecimal(3.5, ru))
        assertEquals("3.5", formatDecimal(3.5, en))
    }

    @Test
    fun epochDayConversionsRoundTrip() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals(date, date.toEpochDayLong().toLocalDate())
        assertEquals(7L, daysBetween(LocalDate.of(2026, 11, 1), date))
    }
}
