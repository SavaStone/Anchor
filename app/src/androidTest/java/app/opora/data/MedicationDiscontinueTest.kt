package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.StopReason
import app.opora.domain.hhmm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class MedicationDiscontinueTest {
    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private val today: LocalDate = LocalDate.now()

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

    /** Лекарство создано вчера, поэтому сегодняшние дозы появляются при любом времени. */
    private suspend fun seedMed(
        name: String = "Арипипразол",
        times: List<LocalTime> = listOf(LocalTime.NOON)
    ): MedicationEntity {
        val yesterday = today.minusDays(1)
        val med = MedicationEntity(
            name = name,
            dosage = "1 таблетка",
            timesCsv = times.distinct().joinToString(",") { it.hhmm() },
            startDate = yesterday.toEpochDay(),
            createdAt = yesterday.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        db.medicationDao().upsert(med)
        repo.ensureTodayLogs()
        return med
    }

    @Test
    fun discontinueRemovesPendingDosesButKeepsTakenHistory() = runBlocking {
        val now = LocalTime.now()
        val med = seedMed(times = listOf(now.minusHours(1), now.plusHours(1)))
        val doses = db.doseDao().forDay(today.toEpochDay())
        assertEquals(2, doses.size)
        val taken = doses.first()
        repo.setDoseStatus(taken.id, "taken")

        assertTrue(repo.discontinueMedication(med.id, StopReason.DOCTOR.key))

        val saved = db.medicationDao().byId(med.id)!!
        assertFalse(saved.active)
        assertNotNull(saved.discontinuedAt)
        assertEquals(StopReason.DOCTOR.key, saved.discontinuedReason)

        val left = db.doseDao().forDay(today.toEpochDay())
        assertTrue(left.none { it.status == "pending" })
        assertEquals(listOf(taken.id), left.map { it.id })
    }

    @Test
    fun undoOfTakenDoseCannotRecreateWaitingDoseAfterStop() = runBlocking {
        val med = seedMed()
        val dose = db.doseDao().forDay(today.toEpochDay()).single()
        repo.setDoseStatus(dose.id, "taken")
        repo.discontinueMedication(med.id, StopReason.DOCTOR.key)

        // «Вернуть в ожидание» из истории не должно воскрешать дозу.
        repo.setDoseStatus(dose.id, "pending")

        assertEquals("taken", db.doseDao().byId(dose.id)!!.status)
        assertTrue(db.doseDao().forDay(today.toEpochDay()).none { it.status == "pending" })
    }

    @Test
    fun discontinueTwiceKeepsFirstDateAndReason() = runBlocking {
        val med = seedMed()
        assertTrue(repo.discontinueMedication(med.id, StopReason.DOCTOR.key))
        val first = db.medicationDao().byId(med.id)!!

        assertFalse(repo.discontinueMedication(med.id, StopReason.SIDE_EFFECTS.key))

        val second = db.medicationDao().byId(med.id)!!
        assertEquals(first.discontinuedAt, second.discontinuedAt)
        assertEquals(StopReason.DOCTOR.key, second.discontinuedReason)
    }

    @Test
    fun discontinueUnknownMedicationDoesNothing() = runBlocking {
        assertFalse(repo.discontinueMedication("missing-id", StopReason.DOCTOR.key))
    }

    @Test
    fun discontinuedMedicationLeavesActiveListAndNextDose() = runBlocking {
        val med = seedMed()
        assertTrue(repo.activeMedications().first().any { it.id == med.id })
        assertNotNull(repo.nextDose())

        repo.discontinueMedication(med.id, StopReason.COURSE_FINISHED.key)

        assertTrue(repo.activeMedications().first().isEmpty())
        assertNull(repo.nextDose())
    }

    @Test
    fun resumeRestoresMedicationAndTodaysDoses() = runBlocking {
        val med = seedMed()
        repo.discontinueMedication(med.id, StopReason.DOCTOR.key)

        assertTrue(repo.resumeMedication(med.id))

        val saved = db.medicationDao().byId(med.id)!!
        assertTrue(saved.active)
        assertNull(saved.discontinuedAt)
        assertEquals("", saved.discontinuedReason)
        assertTrue(db.doseDao().forDay(today.toEpochDay()).any { it.medicationId == med.id })
    }

    @Test
    fun resumeIsNoOpForActiveMedication() = runBlocking {
        val med = seedMed()
        assertFalse(repo.resumeMedication(med.id))
    }

    @Test
    fun savingActiveMedicationClearsStopFields() = runBlocking {
        val med = seedMed()
        repo.discontinueMedication(med.id, StopReason.DOCTOR.key)
        val stopped = db.medicationDao().byId(med.id)!!

        repo.saveMedication(stopped.copy(active = true), expectedInventory = stopped.inventoryCount)

        val saved = db.medicationDao().byId(med.id)!!
        assertTrue(saved.active)
        assertNull(saved.discontinuedAt)
        assertEquals("", saved.discontinuedReason)
    }

    @Test
    fun savingPausedMedicationKeepsStopDateAndReason() = runBlocking {
        val med = seedMed()
        repo.discontinueMedication(med.id, StopReason.SIDE_EFFECTS.key)
        val stopped = db.medicationDao().byId(med.id)!!

        repo.saveMedication(
            stopped.copy(active = false, name = "Арипипразол (пауза)"),
            expectedInventory = stopped.inventoryCount
        )

        val saved = db.medicationDao().byId(med.id)!!
        assertFalse(saved.active)
        assertEquals(stopped.discontinuedAt, saved.discontinuedAt)
        assertEquals(StopReason.SIDE_EFFECTS.key, saved.discontinuedReason)
    }
}
