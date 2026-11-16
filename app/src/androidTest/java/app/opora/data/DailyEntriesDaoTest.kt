package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.MoodEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.db.PositiveEntity
import app.opora.domain.POSITIVE_DEED
import app.opora.domain.POSITIVE_EVENT
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DailyEntriesDaoTest {
    private lateinit var db: OporaDatabase
    private val day = LocalDate.of(2026, 11, 10).toEpochDay()

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun multipleMoodsPerDayAreKeptInOrder() = runBlocking {
        val dao = db.moodDao()
        dao.insert(MoodEntity(date = day, mood = 2, createdAt = 1_000))
        dao.insert(MoodEntity(date = day, mood = 5, createdAt = 2_000))

        val entries = dao.byDate(day)
        assertEquals(2, entries.size)
        assertEquals(listOf(2, 5), entries.map { it.mood })
    }

    @Test
    fun positiveEventsAndDeedsAreStoredSeparately() = runBlocking {
        val dao = db.positiveDao()
        dao.upsert(PositiveEntity("event-1", day, "Прогулка", POSITIVE_EVENT))
        dao.upsert(PositiveEntity("deed-1", day, "Разобрал почту", POSITIVE_DEED))

        val entries = dao.forDate(day).associateBy { it.id }
        assertEquals(2, entries.size)
        assertEquals(POSITIVE_EVENT, entries.getValue("event-1").kind)
        assertEquals(POSITIVE_DEED, entries.getValue("deed-1").kind)

        dao.upsert(entries.getValue("deed-1").copy(done = true))
        assertTrue(dao.byId("deed-1")!!.done)

        dao.delete("event-1")
        assertEquals(1, dao.forDate(day).size)
    }
}
