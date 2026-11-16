package app.opora.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.OporaRepository
import app.opora.data.db.JournalEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.journalDateLabel
import app.opora.ui.JournalViewModel
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * У закреплённых записей нет заголовка даты, поэтому дата должна быть
 * в самой карточке — иначе заметку невозможно привязать ко дню.
 */
@RunWith(AndroidJUnit4::class)
class JournalPinnedDateTest {
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
        runBlocking {
            db.journalDao().upsert(
                JournalEntity(
                    title = "Закреплённая",
                    body = "Текст",
                    pinned = true,
                    entryDate = LocalDate.now().toEpochDay()
                )
            )
        }
        compose.setContent {
            OporaTheme {
                JournalScreen(vm = JournalViewModel(repo), onOpen = {}, onNew = {})
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Закреплённая").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pinnedCardShowsItsDate() {
        val locale = app.opora.AppLanguage.locale(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        compose.onNodeWithText(
            LocalDate.now().journalDateLabel(LocalDate.now(), locale)
        ).assertExists()
    }
}
