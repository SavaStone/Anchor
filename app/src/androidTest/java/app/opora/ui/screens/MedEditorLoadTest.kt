package app.opora.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MedEditorLoadTest {
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
    fun savingExistingMedicationKeepsColorOutsidePalette() {
        val customColor = 0xFF123456
        val med = MedicationEntity(
            name = "Старый цвет",
            timesCsv = "09:00",
            startDate = LocalDate.now().toEpochDay(),
            color = customColor
        )
        runBlocking { db.medicationDao().upsert(med) }
        var closed = false

        compose.setContent {
            OporaTheme {
                MedEditorScreen(medId = med.id, onClose = { closed = true }, repository = repo, db = db)
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.med_editor_title_edit)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.med_editor_save)).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { closed }
        // Compose work must stay on the test's main thread; only the DB read
        // goes through a blocking coroutine here.
        assertEquals(customColor, runBlocking { db.medicationDao().byId(med.id)?.color })
    }

    @Test
    fun missingMedicationShowsRetryAndBackInsteadOfBlankForm() {
        compose.setContent {
            OporaTheme {
                MedEditorScreen(medId = "deleted", onClose = {}, repository = repo, db = db)
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.med_editor_load_error_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.meds_retry)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.meds_back)).assertExists()
    }
}
