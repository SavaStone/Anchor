package app.opora.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.JournalEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupJournalMoodTest {
    private lateinit var db: OporaDatabase
    private lateinit var backup: BackupManager

    private val date = LocalDate.of(2026, 11, 12)

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = BackupManager(context, db, SettingsRepository(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun oldBackupWithoutLinkRestoresMoodEntry() = runBlocking {
        backup.restore(
            BackupPayload(
                journal = listOf(
                    JournalEntity(
                        id = "old",
                        title = "Старая запись",
                        mood = 4,
                        entryDate = date.toEpochDay()
                    )
                )
            )
        )

        val journal = db.journalDao().byId("old")
        assertNotNull(journal?.moodEntryId)
        assertEquals(4, db.moodDao().byId(journal!!.moodEntryId!!)?.mood)
    }

    @Test
    fun newBackupKeepsExistingLinksWithoutDuplicates() = runBlocking {
        backup.restore(
            BackupPayload(
                moods = listOf(MoodEntity(id = 7, date = date.toEpochDay(), mood = 2)),
                journal = listOf(
                    JournalEntity(
                        id = "linked",
                        title = "Новая запись",
                        mood = 2,
                        moodEntryId = 7,
                        entryDate = date.toEpochDay()
                    )
                )
            )
        )

        assertEquals(1, db.moodDao().getAll().size)
        assertEquals(7L, db.journalDao().byId("linked")?.moodEntryId)
    }
}
