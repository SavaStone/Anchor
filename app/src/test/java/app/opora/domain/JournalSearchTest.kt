package app.opora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalSearchTest {

    private fun matches(
        query: String,
        title: String = "",
        body: String = "",
        tags: Collection<String> = emptyList(),
        labels: Map<String, String> = emptyMap()
    ) = journalMatchesQuery(title, body, tags, labels, query)

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(matches("", title = "Запись"))
        assertTrue(matches("   ", title = "Запись"))
    }

    @Test
    fun matchesPartialWordIgnoringCase() {
        assertTrue(matches("ВЕЧЕР", title = "Плохой вечер"))
        assertTrue(matches("чер", body = "Тяжёлый вечер"))
    }

    @Test
    fun trimsSurroundingSpaces() {
        assertTrue(matches("  вечер  ", title = "Плохой вечер"))
    }

    @Test
    fun foldsYoToYe() {
        assertTrue(matches("елка", title = "Ёлка"))
        assertTrue(matches("ЁЛКА", title = "елка"))
    }

    @Test
    fun matchesTagLabelAndKey() {
        val labels = mapOf("joy" to "радость")
        assertTrue(matches("радость", tags = listOf("joy"), labels = labels))
        assertTrue(matches("JOY", tags = listOf("joy"), labels = labels))
    }

    @Test
    fun tagsDeduplicateIgnoringCaseAndSpaces() {
        assertEquals(
            listOf("Тревога", "сон"),
            distinctJournalTags(listOf(" Тревога ", "тревога", "сон", "СОН"))
        )
    }

    @Test
    fun missingQueryReturnsFalse() {
        assertFalse(matches("отпуск", title = "Плохой вечер", body = "Дома"))
    }

    @Test
    fun foldsDiacriticsInEnglishText() {
        assertTrue(matches("cafe", title = "Café"))
        assertTrue(matches("MULLER", title = "Müller"))
        assertEquals("muller", normalizeForSearch("Müller"))
    }

    @Test
    fun stripsMarksFromRussianLettersToo() {
        assertEquals("елка", normalizeForSearch("Ёлка"))
        assertEquals("и", normalizeForSearch("й"))
    }
}
