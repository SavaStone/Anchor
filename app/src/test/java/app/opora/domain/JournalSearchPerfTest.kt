package app.opora.domain

import app.opora.data.db.JournalEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalSearchPerfTest {
    @Test
    fun searchOverThousandEntriesPerCharacter() {
        val entries = (0 until 1000).map { index ->
            JournalEntity(
                id = "perf-$index",
                title = "Запись $index",
                body = "Тяжёлый вечер, тревога и усталость, длинный текст заметки $index. " +
                    "Прогулка, сон, разговор с врачом и планы на завтра.",
                tags = "тревога,сон",
                entryDate = LocalDate.now().minusDays((index % 300).toLong()).toEpochDay()
            )
        }
        val queries = "тревога и усталость прогулка".map { it.toString() }
        val normalized = entries.associate { it.id to normalizeForSearch(it.title + "\n" + it.body) }
        // прогрев
        entries.forEach {
            journalMatchesQuery(
                it.title, it.body, parseKeys(it.tags), emptyMap(), "т",
                normalizedText = normalized[it.id]
            )
        }
        val started = System.nanoTime()
        var matches = 0
        queries.forEach { q ->
            entries.forEach { entry ->
                if (journalMatchesQuery(
                        entry.title, entry.body, parseKeys(entry.tags), emptyMap(), q,
                        normalizedText = normalized[entry.id]
                    )
                ) {
                    matches++
                }
            }
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        println("JOURNAL_SEARCH_MS=${"%.2f".format(elapsedMs)} perChar=${"%.3f".format(elapsedMs / queries.size)} matches=$matches")
        assertTrue(elapsedMs < 10_000)
    }
}
