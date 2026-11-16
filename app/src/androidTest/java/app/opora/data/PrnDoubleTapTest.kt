package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.MedsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Двойной тап «Записать» не должен создавать две PRN-дозы. */
@RunWith(AndroidJUnit4::class)
class PrnDoubleTapTest {
    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private val today = LocalDate.now()

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

    @Test
    fun doubleTapLogsOneDose() = runBlocking {
        val med = MedicationEntity(
            name = "Атаракс",
            scheduleType = "as_needed",
            startDate = today.toEpochDay()
        )
        db.medicationDao().upsert(med)
        val vm = MedsViewModel(repo)

        vm.prn(med.id)
        vm.prn(med.id)

        withTimeout(5_000) {
            while (db.doseDao().forDay(today.toEpochDay()).none { it.medicationId == med.id }) {
                delay(10)
            }
        }
        delay(300)
        assertEquals(
            1,
            db.doseDao().forDay(today.toEpochDay()).count { it.medicationId == med.id }
        )
    }
}
