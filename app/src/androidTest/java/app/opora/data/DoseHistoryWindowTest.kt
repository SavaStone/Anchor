package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DoseHistoryWindowTest {
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

    @Test
    fun thirtyDayWindowCoversExactlyThirtyDays() = runBlocking {
        db.doseDao().upsert(
            DoseLogEntity(
                id = "edge",
                medicationId = "m",
                scheduledDate = today.minusDays(30).toEpochDay(),
                scheduledTime = "09:00",
                status = "taken"
            )
        )
        db.doseDao().upsert(
            DoseLogEntity(
                id = "inside",
                medicationId = "m",
                scheduledDate = today.minusDays(29).toEpochDay(),
                scheduledTime = "09:00",
                status = "taken"
            )
        )

        val window = repo.doseHistory(30, today).first()

        assertEquals(listOf("inside"), window.map { it.id })
    }
}
