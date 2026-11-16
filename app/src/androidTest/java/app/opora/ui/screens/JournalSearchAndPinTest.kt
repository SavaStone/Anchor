package app.opora.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.JournalEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.JournalViewModel
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class JournalSearchAndPinTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository

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

    @Test
    fun pinOnEmptyNoteDoesNotCreateEntry() {
        compose.setContent {
            OporaTheme {
                JournalEditorScreen(
                    entryId = null,
                    vm = JournalViewModel(repo),
                    onClose = {}
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(TestStrings.get(R.string.journal_pin)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(TestStrings.get(R.string.journal_pin)).performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        runBlocking {
            assertEquals(0, db.journalDao().getAll().size)
        }
    }

    @Test
    fun emotionsFromJournalDayAreFoundBySearch() = runBlocking {
        val day = LocalDate.now()
        db.journalDao().upsert(
            JournalEntity(
                title = "Вечер",
                body = "Было тревожно",
                entryDate = day.toEpochDay()
            )
        )
        db.moodDao().insert(
            MoodEntity(
                date = day.toEpochDay(),
                mood = 3,
                emotions = "anxiety"
            )
        )
        val vm = JournalViewModel(repo)
        // Дожидаемся первого полного состояния, затем ищем по эмоции,
        // которая сохранена только в отметке этого дня.
        withTimeout(5_000) { vm.state.first { !it.loading && it.entries.size == 1 } }
        vm.search("тревога")
        val filtered = withTimeout(5_000) {
            vm.state.first { it.query == "тревога" && it.entries.size == 1 }
        }
        assertEquals("Вечер", filtered.entries.single().title)
    }

    @Test
    fun journalTagsAreStoredCaseInsensitively() = runBlocking {
        val saved = repo.saveJournalText(
            JournalEntity(
                title = "Метки",
                tags = "Тревога,тревога,сон,СОН",
                entryDate = LocalDate.now().toEpochDay()
            )
        )
        assertEquals("Тревога,сон", saved.tags)
    }
}
