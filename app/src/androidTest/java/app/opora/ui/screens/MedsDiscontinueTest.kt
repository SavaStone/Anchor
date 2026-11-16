package app.opora.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.OporaRepository
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.MedsViewModel
import app.opora.ui.TestStrings
import app.opora.R
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Сценарий «врач сказал больше не принимать»: прекращение приёма доступно из
 * «ещё» на карточке лекарства, причина необязательна, действие обратимо.
 */
@RunWith(AndroidJUnit4::class)
class MedsDiscontinueTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private lateinit var medId: String
    private val name = "Арипипразол"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OporaRepository(context, db, SettingsRepository(context))
        val med = MedicationEntity(
            name = name,
            dosage = "1 таблетка",
            timesCsv = "21:00",
            startDate = LocalDate.now().toEpochDay()
        )
        medId = med.id
        runBlocking { db.medicationDao().upsert(med) }

        compose.setContent {
            OporaTheme {
                MedsScreen(
                    vm = MedsViewModel(repo),
                    onAddMedication = {},
                    onEditMedication = {}
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.meds_tab_list)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.meds_tab_list)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(TestStrings.get(R.string.meds_more_a11y, "$name, 1 таблетка"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun overflowOffersDiscontinueAndDialogCanBeDismissed() {
        compose.onNodeWithContentDescription(TestStrings.get(R.string.meds_more_a11y, "$name, 1 таблетка")).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.actions_discontinue)).assertExists().performClick()

        compose.onNodeWithText(TestStrings.get(R.string.meds_stop_title)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.meds_stop_cancel)).assertExists()

        compose.onNodeWithText(TestStrings.get(R.string.meds_stop_cancel)).performClick()

        val saved = runBlocking { repo.medicationOnce(medId) }!!
        assertTrue(saved.active)
        assertNull(saved.discontinuedAt)
    }

    @Test
    fun confirmingStopMovesMedicationToStoppedSectionAndUndoRestoresIt() {
        compose.onNodeWithContentDescription(TestStrings.get(R.string.meds_more_a11y, "$name, 1 таблетка")).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.actions_discontinue)).performClick()

        compose.onNodeWithText(TestStrings.get(R.string.meds_stop_reason_none)).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.stop_doctor)).performClick()
        // В диалоге подтверждение короче пункта меню, чтобы подпись
        // не переносилась при крупном шрифте.
        compose.onNodeWithText(TestStrings.get(R.string.meds_stop_confirm)).performClick()

        compose.waitUntil(10_000) {
            runBlocking { repo.medicationOnce(medId)?.active == false }
        }
        val stopped = runBlocking { repo.medicationOnce(medId) }!!
        assertNotNull(stopped.discontinuedAt)
        assertFalse(runBlocking { db.doseDao().forDay(LocalDate.now().toEpochDay()) }
            .any { it.medicationId == medId && it.status == "pending" })

        compose.onNodeWithText(TestStrings.get(R.string.meds_section_stopped)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.stop_doctor), substring = true).assertExists()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.meds_snack_stopped, name)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.meds_snack_restore)).performClick()

        compose.waitUntil(10_000) {
            runBlocking { repo.medicationOnce(medId)?.active == true }
        }
        val resumed = runBlocking { repo.medicationOnce(medId) }!!
        assertNull(resumed.discontinuedAt)
    }

    @Test
    fun stoppedMedicationOffersOnlyDeleteInItsOverflow() {
        runBlocking { repo.discontinueMedication(medId, "doctor") }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.meds_section_stopped)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription(TestStrings.get(R.string.meds_more_a11y, "$name, 1 таблетка")).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_medication)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_resume)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_discontinue)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_medication)).assertDoesNotExist()
    }

}
