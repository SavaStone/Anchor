package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.JournalEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.JournalViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class JournalMoodSyncTest {
    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository

    private val date = LocalDate.of(2026, 11, 12)

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OporaRepository(context, db, SettingsRepository(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun journal(id: String = "entry-1", mood: Int? = 4) = JournalEntity(
        id = id,
        title = "Запись",
        mood = mood,
        entryDate = date.toEpochDay()
    )

    @Test
    fun moodFromJournalBecomesMoodEntry() = runBlocking {
        val saved = repo.saveJournal(journal())

        val moodId = saved.moodEntryId
        assertNotNull(moodId)
        val mood = db.moodDao().byId(moodId!!)
        assertEquals(4, mood?.mood)
        assertEquals(date.toEpochDay(), mood?.date)
    }

    @Test
    fun changingMoodUpdatesSameEntryInsteadOfAddingAnother() = runBlocking {
        val first = repo.saveJournal(journal(mood = 2))
        val second = repo.saveJournal(first.copy(mood = 5))

        assertEquals(first.moodEntryId, second.moodEntryId)
        assertEquals(1, db.moodDao().getAll().size)
        assertEquals(5, db.moodDao().byId(second.moodEntryId!!)?.mood)
    }

    @Test
    fun clearingMoodRemovesLinkedEntry() = runBlocking {
        val saved = repo.saveJournal(journal())
        repo.saveJournal(saved.copy(mood = null))

        assertEquals(0, db.moodDao().getAll().size)
        assertNull(db.journalDao().byId(saved.id)?.moodEntryId)
    }

    @Test
    fun deletingJournalRemovesLinkedMood() = runBlocking {
        val saved = repo.saveJournal(journal())
        repo.deleteJournal(saved.id)

        assertEquals(0, db.journalDao().getAll().size)
        assertEquals(0, db.moodDao().getAll().size)
    }

    @Test
    fun clearingJournalKeepsMoodEnrichedInMoodSection() = runBlocking {
        val saved = repo.saveJournal(journal(mood = 2))
        repo.saveMood(
            date = date,
            mood = 4,
            anxiety = 7,
            energy = null,
            sleepMinutes = null,
            emotions = listOf("calm"),
            symptoms = emptyList(),
            note = "Наблюдение из раздела настроения",
            entryId = saved.moodEntryId!!
        )

        val cleared = repo.saveJournal(saved.copy(mood = null))

        assertNull(cleared.moodEntryId)
        val mood = db.moodDao().getAll().single()
        assertEquals(7, mood.anxiety)
        assertEquals("Наблюдение из раздела настроения", mood.note)
    }

    @Test
    fun deletingJournalKeepsMoodEnrichedInMoodSection() = runBlocking {
        val saved = repo.saveJournal(journal(mood = 2))
        repo.saveMood(
            date = date,
            mood = 4,
            anxiety = 3,
            energy = null,
            sleepMinutes = null,
            emotions = emptyList(),
            symptoms = emptyList(),
            note = "Важная заметка",
            entryId = saved.moodEntryId!!
        )

        repo.deleteJournal(saved.id)

        assertEquals(0, db.journalDao().getAll().size)
        assertEquals("Важная заметка", db.moodDao().getAll().single().note)
    }

    @Test
    fun changingJournalDateMovesLinkedMood() = runBlocking {
        val saved = repo.saveJournal(journal(mood = 2))
        val moved = date.minusDays(2)

        repo.saveJournal(saved.copy(entryDate = moved.toEpochDay()))

        assertEquals(moved.toEpochDay(), db.moodDao().byId(saved.moodEntryId!!)?.date)
    }

    @Test
    fun editingLinkedMoodUpdatesJournalSnapshot() = runBlocking {
        val saved = repo.saveJournal(journal(mood = 2))
        val moodId = saved.moodEntryId!!

        repo.saveMood(
            date = date,
            mood = 5,
            anxiety = null,
            energy = null,
            sleepMinutes = null,
            emotions = emptyList(),
            symptoms = emptyList(),
            note = "",
            entryId = moodId
        )

        assertEquals(5, db.journalDao().byId(saved.id)?.mood)
    }

    @Test
    fun movingLinkedMoodMovesJournalDate() = runBlocking {
        val saved = repo.saveJournal(journal())
        val moved = date.minusDays(3)

        repo.saveMood(
            date = moved,
            mood = 4,
            anxiety = null,
            energy = null,
            sleepMinutes = null,
            emotions = emptyList(),
            symptoms = emptyList(),
            note = "",
            entryId = saved.moodEntryId!!
        )

        assertEquals(moved.toEpochDay(), db.journalDao().byId(saved.id)?.entryDate)
    }

    @Test
    fun deletingLinkedMoodDoesNotResurrectOnJournalSave() = runBlocking {
        val saved = repo.saveJournal(journal())
        repo.deleteMood(saved.moodEntryId!!)

        val afterDelete = db.journalDao().byId(saved.id)!!
        assertNull(afterDelete.mood)
        assertNull(afterDelete.moodEntryId)

        val resaved = repo.saveJournal(afterDelete.copy(title = "Другое имя"))
        assertNull(resaved.moodEntryId)
        assertEquals(0, db.moodDao().getAll().size)
    }

    @Test
    fun journalTextSaveDoesNotResurrectDeletedMood() = runBlocking {
        val saved = repo.saveJournal(journal(mood = 3))
        val stale = saved.copy(title = "Другое имя")
        repo.deleteMood(saved.moodEntryId!!)

        // Редактор держал снимок со старой связью, пока отметку удаляли.
        repo.saveJournalText(stale)

        val updated = db.journalDao().byId(saved.id)!!
        assertEquals("Другое имя", updated.title)
        assertNull(updated.mood)
        assertNull(updated.moodEntryId)
        assertEquals(0, db.moodDao().getAll().size)
    }

    @Test
    fun journalTextSaveDoesNotMoveLinkedMood() = runBlocking {
        val saved = repo.saveJournal(journal(mood = 3))
        val moved = date.minusDays(2)

        repo.saveJournalText(saved.copy(title = "Текст", entryDate = moved.toEpochDay()))

        assertEquals(moved.toEpochDay(), db.journalDao().byId(saved.id)?.entryDate)
        assertEquals(date.toEpochDay(), db.moodDao().byId(saved.moodEntryId!!)?.date)
    }

    @Test
    fun textOnlyJournalEntryCreatesNoMood() = runBlocking {
        val saved = repo.saveJournalText(
            JournalEntity(id = "text-only", title = "Только текст", entryDate = date.toEpochDay())
        )

        assertNull(saved.mood)
        assertNull(saved.moodEntryId)
        assertEquals(0, db.moodDao().getAll().size)
    }

    @Test
    fun journalSearchFindsLinkedMoodEmotion() = runBlocking {
        val saved = repo.saveJournal(journal(mood = 3))
        repo.saveMood(
            date = date,
            mood = 3,
            anxiety = null,
            energy = null,
            sleepMinutes = null,
            emotions = listOf("joy"),
            symptoms = emptyList(),
            note = "",
            entryId = saved.moodEntryId!!
        )

        val vm = JournalViewModel(repo)
        vm.search("радость")
        val state = vm.state.first { !it.loading }

        assertEquals(listOf(saved.id), state.entries.map { it.id })
    }

    @Test
    fun oldJournalEntriesAreBackfilled() = runBlocking {
        db.journalDao().upsert(journal(id = "old", mood = 3))

        linkJournalMoods(db.journalDao(), db.moodDao())

        val linked = db.journalDao().byId("old")
        assertNotNull(linked?.moodEntryId)
        assertEquals(3, db.moodDao().byId(linked!!.moodEntryId!!)?.mood)
    }

    @Test
    fun danglingMoodLinkIsRebuilt() = runBlocking {
        db.journalDao().upsert(journal(id = "dangling", mood = 2).copy(moodEntryId = 999))

        linkJournalMoods(db.journalDao(), db.moodDao())

        val linked = db.journalDao().byId("dangling")
        assertNotNull(linked?.moodEntryId)
        assertEquals(2, db.moodDao().byId(linked!!.moodEntryId!!)?.mood)
        assertEquals(1, db.moodDao().getAll().size)
    }
}
