package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Переход через полночь не должен сразу помечать вечернюю дозу пропущенной:
 * у неё ещё есть 4 часа, чтобы отметиться.
 */
@RunWith(AndroidJUnit4::class)
class MidnightMissedGraceTest {
    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private val today: LocalDate = LocalDate.now()
    private val zone: ZoneId = ZoneId.systemDefault()

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

    private fun pending(id: String, date: LocalDate, time: String, createdAt: Long): DoseLogEntity {
        val at = date.atTime(java.time.LocalTime.parse(time))
            .atZone(zone).toInstant().toEpochMilli()
        return DoseLogEntity(
            id = id,
            medicationId = "m",
            scheduledDate = date.toEpochDay(),
            scheduledTime = time,
            scheduledAt = at,
            status = "pending",
            createdAt = createdAt
        )
    }

    @Test
    fun eveningDoseKeepsGraceAfterMidnight() = runBlocking {
        val yesterday = today.minusDays(1)
        val created = yesterday.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        db.doseDao().upsert(pending("late", yesterday, "23:00", created))
        db.doseDao().upsert(pending("old", yesterday, "19:00", created))

        // 00:01 следующего дня: 23:00 ещё в пределах четырёхчасового окна,
        // 19:00 уже нет.
        repo.markMissedOlderThan(now = today.atTime(0, 1), minutes = 240)

        assertEquals("pending", db.doseDao().byId("late")!!.status)
        assertEquals("missed", db.doseDao().byId("old")!!.status)
    }
}
