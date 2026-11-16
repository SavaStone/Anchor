package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class MedicationDoseIntegrityTest {
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

    private suspend fun seed(
        name: String = "Арипипразол",
        stock: Double? = null,
        startDate: LocalDate = today
    ): MedicationEntity {
        val med = MedicationEntity(
            name = name,
            dosage = "1 таблетка",
            timesCsv = "09:00",
            startDate = startDate.toEpochDay(),
            inventoryCount = stock,
            createdAt = startDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        db.medicationDao().upsert(med)
        repo.ensureTodayLogs()
        return med
    }

    @Test
    fun undoAfterMedicationDeletionKeepsTakenDose() = runBlocking {
        val med = seed()
        val dose = db.doseDao().forDay(today.toEpochDay()).single()
        repo.setDoseStatus(dose.id, "taken")

        repo.deleteMedication(med.id)
        repo.setDoseStatus(dose.id, "pending")

        assertNull(db.medicationDao().byId(med.id))
        assertNotNull(db.doseDao().byId(dose.id))
        assertEquals("taken", db.doseDao().byId(dose.id)!!.status)
    }

    @Test
    fun concurrentTakesOfOneDoseDeductStockOnce() = runBlocking {
        val med = seed(stock = 10.0)
        val dose = db.doseDao().forDay(today.toEpochDay()).single()

        coroutineScope {
            (0 until 20).map {
                async(Dispatchers.Default) { repo.setDoseStatus(dose.id, "taken") }
            }.awaitAll()
        }

        assertEquals(9.0, db.medicationDao().byId(med.id)!!.inventoryCount!!, 0.0001)
        assertEquals(1.0, db.doseDao().byId(dose.id)!!.stockDeducted!!, 0.0001)
    }

    @Test
    fun savingMedicationKeepsYesterdaysPendingDose() = runBlocking {
        val med = seed(startDate = today.minusDays(1))
        val yesterday = today.minusDays(1)
        val oldDose = DoseLogEntity(
            id = "old-pending",
            medicationId = med.id,
            medicationName = med.name,
            dosage = med.dosage,
            scheduledDate = yesterday.toEpochDay(),
            scheduledTime = "09:00",
            status = "pending"
        )
        db.doseDao().upsert(oldDose)

        repo.saveMedication(med.copy(notes = "новая заметка"))

        assertTrue(
            db.doseDao().forDay(yesterday.toEpochDay()).any { it.id == oldDose.id }
        )
        assertTrue(
            db.doseDao().forDay(today.toEpochDay()).any {
                it.medicationId == med.id && it.status == "pending"
            }
        )
    }
}
