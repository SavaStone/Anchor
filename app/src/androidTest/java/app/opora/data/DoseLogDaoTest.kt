package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.OporaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DoseLogDaoTest {
    private lateinit var db: OporaDatabase
    private val day = LocalDate.of(2026, 11, 8).toEpochDay()

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun prn(time: String = "10:28") = DoseLogEntity(
        medicationId = "med-1",
        medicationName = "Атаракс",
        scheduledDate = day,
        scheduledTime = time,
        scheduledAt = 1_700_000_000_000,
        status = "taken",
        takenAt = 1_700_000_000_000,
        isPrn = true
    )

    @Test
    fun twoPrnDosesInSameMinuteAreBothKept() = runBlocking {
        db.doseDao().insertIgnore(prn())
        db.doseDao().insertIgnore(prn())

        assertEquals(2, db.doseDao().forDay(day).count { it.isPrn })
    }

    @Test
    fun scheduledDoseWithSameStableIdIsInsertedOnce() = runBlocking {
        val id = DoseLogEntity.scheduledId("med-1", day, "09:00")
        val dose = prn("09:00").copy(id = id, isPrn = false)
        db.doseDao().insertIgnore(dose)
        db.doseDao().insertIgnore(dose)

        assertEquals(1, db.doseDao().forDay(day).size)
    }
}
