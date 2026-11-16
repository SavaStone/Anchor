package app.opora.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.TodayViewModel
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Приостановленное лекарство — не пустой аккаунт: карточка «Начните
 * с лекарства» не должна заменять блок лекарств и принятую сегодня дозу.
 */
@RunWith(AndroidJUnit4::class)
class TodayPausedMedTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OporaRepository(context, db, SettingsRepository(context))
        val today = LocalDate.now()
        val med = MedicationEntity(
            name = "Арипипразол",
            dosage = "1 таблетка",
            timesCsv = "09:00",
            startDate = today.minusDays(1).toEpochDay(),
            active = false
        )
        runBlocking {
            db.medicationDao().upsert(med)
            db.doseDao().upsert(
                DoseLogEntity(
                    medicationId = med.id,
                    medicationName = med.name,
                    dosage = med.dosage,
                    scheduledDate = today.toEpochDay(),
                    scheduledTime = "09:00",
                    status = "taken",
                    takenAt = System.currentTimeMillis()
                )
            )
        }
        compose.setContent {
            OporaTheme {
                TodayScreen(
                    vm = TodayViewModel(repo),
                    onEditMood = { _, _ -> },
                    onOpenMeds = {},
                    onAddMedication = {},
                    onOpenJournal = {},
                    onOpenAppointments = {},
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(context.getString(R.string.home_block_mood)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pausedMedicationKeepsTheMedicationCard() {
        compose.onAllNodesWithText(context.getString(R.string.empty_med_title)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.home_block_meds)).assertCountEquals(1)
    }
}
