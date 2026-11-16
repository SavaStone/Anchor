package app.opora.ui.screens

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.JournalEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Пользователь с одним дневником (без настроения и приёмов) должен иметь
 * возможность собрать отчёт, а не видеть «нет записей».
 */
@RunWith(AndroidJUnit4::class)
class ReportJournalOnlyTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private lateinit var settings: SettingsRepository
    private lateinit var restoration: StateRestorationTester

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        repo = OporaRepository(context, db, settings)
        runBlocking {
            db.journalDao().upsert(
                JournalEntity(
                    title = "Только дневник",
                    body = "Запись без отметок настроения и лекарств.",
                    entryDate = LocalDate.now().toEpochDay()
                )
            )
        }
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            OporaTheme {
                ReportScreen(onBack = {}, repository = repo, settings = settings)
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.report_title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun journalOnlyPeriodCanProduceReport() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.report_generate)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.report_generate)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.report_empty_title)).assertDoesNotExist()
    }

    @Test
    fun journalOnlyDataActuallyProducesPdfAndSurvivesRecreation() {
        compose.onNodeWithText(TestStrings.get(R.string.report_generate)).performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.report_ready)).fetchSemanticsNodes().isNotEmpty()
        }
        // Поворот/пересоздание activity не должны терять карточку файла.
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(TestStrings.get(R.string.report_ready)).assertExists()
    }
}
