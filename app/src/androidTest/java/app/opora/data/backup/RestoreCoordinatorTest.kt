package app.opora.data.backup

import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.MainActivity
import app.opora.OporaApp
import app.opora.data.OporaRepository
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RestoreCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OporaDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var backup: BackupManager
    private lateinit var coordinator: RestoreCoordinator

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        backup = BackupManager(context, db, settings)
        backup.discardRestorePoint()
        coordinator = RestoreCoordinator(
            context,
            db,
            OporaRepository(context, db, settings),
            backup,
            settings,
            WorkScheduler(context)
        )
    }

    @After
    fun tearDown() = runBlocking {
        backup.discardRestorePoint()
        db.close()
    }

    private fun med(name: String) = MedicationEntity(
        name = name,
        timesCsv = "09:00",
        startDate = LocalDate.now().toEpochDay()
    )

    private fun payloadFor(vararg meds: MedicationEntity) = BackupPayload(
        medications = meds.toList()
    )

    private suspend fun awaitSettled(): RestoreState = withTimeout(10_000) {
        coordinator.state.first { it is RestoreState.Success || it is RestoreState.Failed || it is RestoreState.Cancelled }
    }

    @Test
    fun importCreatesRestorePointThatCanBeRestored() = runBlocking {
        val oldMed = med("Старое")
        db.medicationDao().upsert(oldMed)

        assertTrue(coordinator.start(payloadFor(med("Новое"))))
        assertTrue(awaitSettled() is RestoreState.Success)
        assertEquals("Новое", db.medicationDao().getAll().single().name)
        assertNotNull(backup.restorePointInfo())

        backup.restorePoint()
        assertTrue(db.medicationDao().getAll().any { it.name == "Старое" })
    }

    @Test
    fun cancelDuringWriteLeavesOriginalDatabase() = runBlocking {
        val oldMed = med("Старое")
        db.medicationDao().upsert(oldMed)
        coordinator.stageDelayMs = 400

        assertTrue(coordinator.start(payloadFor(med("Новое"))))
        withTimeout(15_000) {
            coordinator.state.first { it == RestoreState.Running(RestoreStage.WRITING) }
        }
        coordinator.cancel()
        val result = awaitSettled()
        assertTrue(result is RestoreState.Cancelled)
        assertEquals("Старое", db.medicationDao().getAll().single().name)
    }

    @Test
    fun cancelAfterDatabaseWriteLeavesImportedDataConsistent() = runBlocking {
        db.medicationDao().upsert(med("Старое"))
        coordinator.stageDelayMs = 400

        assertTrue(coordinator.start(payloadFor(med("Новое"))))
        withTimeout(15_000) {
            coordinator.state.first { it == RestoreState.Running(RestoreStage.REARMING) }
        }
        coordinator.cancel()
        assertTrue(awaitSettled() is RestoreState.Cancelled)
        assertEquals("Новое", db.medicationDao().getAll().single().name)
    }

    @Test
    fun applicationScopeRestoreSurvivesActivityRecreation() = runBlocking {
        settings.setOnboardingDone(true)
        settings.setDisclaimerAccepted(true)
        settings.setAppLock(false)
        val app = context.applicationContext as OporaApp
        val appCoordinator = app.container.restore
        appCoordinator.stageDelayMs = 800

        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(appCoordinator.start(payloadFor(med("После поворота"))))
            scenario.recreate()
            val state = withTimeout(15_000) {
                appCoordinator.state.first { it is RestoreState.Success || it is RestoreState.Failed }
            }
            assertTrue(state is RestoreState.Success)
            assertEquals(
                "После поворота",
                app.container.db.medicationDao().getAll().single().name
            )
        } finally {
            appCoordinator.stageDelayMs = 0
            scenario?.close()
        }
    }
}
