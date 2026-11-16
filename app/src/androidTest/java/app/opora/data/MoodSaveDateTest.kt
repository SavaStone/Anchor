package app.opora.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class MoodSaveDateTest {
    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private val zone = ZoneId.systemDefault()
    private val date = LocalDate.of(2026, 11, 13)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OporaRepository(context, db, SettingsRepository(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun save(time: LocalTime): Long = runBlocking {
        repo.saveMood(
            date = date,
            mood = 4,
            anxiety = null,
            energy = null,
            sleepMinutes = null,
            emotions = emptyList(),
            symptoms = emptyList(),
            note = "запись ${time}",
            occurredAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        )
    }

    @Test
    fun selectedDateAndTimeAreStoredWithoutOverwritingEarlierEntry() = runBlocking {
        val early = save(LocalTime.of(0, 3))
        val evening = save(LocalTime.of(21, 0))
        assertNotEquals(early, evening)

        val entries = db.moodDao().byDate(date.toEpochDay()).sortedBy { it.createdAt }
        assertEquals(2, entries.size)
        assertEquals(date.toEpochDay(), entries[0].date)
        assertEquals(date.toEpochDay(), entries[1].date)
        assertEquals(LocalTime.of(0, 3), Instant.ofEpochMilli(entries[0].createdAt)
            .atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(21, 0), Instant.ofEpochMilli(entries[1].createdAt)
            .atZone(zone).toLocalTime())
        assertEquals("запись 00:03", entries[0].note)
        assertEquals("запись 21:00", entries[1].note)
    }

    @Test
    fun editingKeepsChosenTimeWhenProvided() = runBlocking {
        val id = repo.saveMood(
            date = date,
            mood = 2,
            anxiety = null,
            energy = null,
            sleepMinutes = null,
            emotions = emptyList(),
            symptoms = emptyList(),
            note = "было",
            occurredAt = at(0, 3)
        )
        repo.saveMood(
            date = date,
            mood = 5,
            anxiety = null,
            energy = null,
            sleepMinutes = null,
            emotions = emptyList(),
            symptoms = emptyList(),
            note = "стало",
            entryId = id,
            occurredAt = at(21, 0)
        )
        val entry = db.moodDao().byId(id)!!
        assertEquals(LocalTime.of(21, 0), Instant.ofEpochMilli(entry.createdAt)
            .atZone(zone).toLocalTime())
        assertEquals(5, entry.mood)
    }
}
