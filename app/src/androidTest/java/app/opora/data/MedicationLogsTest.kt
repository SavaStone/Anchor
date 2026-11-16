package app.opora.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.notifications.MedicationAlarms
import app.opora.notifications.Notify
import app.opora.ui.SettingsViewModel
import app.opora.work.WorkScheduler
import app.opora.domain.hhmm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class MedicationLogsTest {
    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        // These tests post and inspect real notifications; a fresh install has
        // no POST_NOTIFICATIONS grant, and test order must not decide the result.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
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
    fun doseScheduledEarlierTodayStaysAvailableAfterLateMedicationCreation() = runBlocking {
        val now = LocalDateTime.now()
        // Ранний слот сегодняшнего дня; если сейчас ночь, отступаем на минуту.
        val slot = if (now.hour >= 5) now.minusHours(5) else now.minusMinutes(1)
        val med = MedicationEntity(
            name = "Арипипразол",
            dosage = "0,5 таблетки",
            timesCsv = slot.toLocalTime().hhmm(),
            startDate = slot.toLocalDate().toEpochDay(),
            createdAt = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        db.medicationDao().upsert(med)

        repo.ensureTodayLogs(slot.toLocalDate())

        val doses = db.doseDao().forDay(slot.toLocalDate().toEpochDay())
        assertEquals(1, doses.size)
        // Слот появился после своего времени, поэтому не должен задним числом
        // стать «пропущенным»: человек ещё не видел этот приём.
        assertEquals("pending", doses.single().status)
    }

    @Test
    fun quickStockAdjustmentCountsAndClampsAtZero() = runBlocking {
        val med = MedicationEntity(
            name = "Атаракс",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 2.0,
            refillThreshold = 1.0
        )
        db.medicationDao().upsert(med)

        repo.adjustInventory(med.id, 1.0)
        assertEquals(3.0, db.medicationDao().byId(med.id)?.inventoryCount)

        repo.adjustInventory(med.id, -5.0)
        assertEquals(0.0, db.medicationDao().byId(med.id)?.inventoryCount)
    }

    @Test
    fun halfDoseSubtractsHalfFromStock() = runBlocking {
        val med = MedicationEntity(
            name = "Тест",
            dosage = "0,5 капсулы",
            timesCsv = "09:00",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 19.0,
            refillThreshold = 5.0
        )
        db.medicationDao().upsert(med)
        repo.ensureTodayLogs()
        val dose = db.doseDao().forDay(LocalDate.now().toEpochDay()).single()
        repo.setDoseStatus(dose.id, "taken")
        assertEquals(18.5, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)
    }

    private suspend fun insertDose(
        med: MedicationEntity,
        time: String,
        date: LocalDate = LocalDate.now()
    ): DoseLogEntity {
        val dose = DoseLogEntity(
            id = DoseLogEntity.scheduledId(med.id, date.toEpochDay(), time),
            medicationId = med.id,
            medicationName = med.name,
            dosage = med.dosage,
            scheduledDate = date.toEpochDay(),
            scheduledTime = time,
            scheduledAt = date.atTime(java.time.LocalTime.parse(time))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            status = "pending"
        )
        db.doseDao().upsert(dose)
        return dose
    }

    @Test
    fun undoOfDoseWithEmptyStockDoesNotCreateStock() = runBlocking {
        val med = MedicationEntity(
            name = "Тест",
            dosage = "1 таблетка",
            timesCsv = "08:00",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 0.0,
            refillThreshold = 0.0
        )
        db.medicationDao().upsert(med)
        val dose = insertDose(med, "08:00")

        repo.setDoseStatus(dose.id, "taken")
        assertEquals(0.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)

        repo.setDoseStatus(dose.id, "pending")
        assertEquals(0.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)
    }

    @Test
    fun undoingPartiallyCoveredDoseRestoresOnlyDeductedStock() = runBlocking {
        val med = MedicationEntity(
            name = "Тест",
            dosage = "1 таблетка",
            timesCsv = "08:00",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 0.5,
            refillThreshold = 0.0
        )
        db.medicationDao().upsert(med)
        val dose = insertDose(med, "08:00")

        repo.setDoseStatus(dose.id, "taken")
        assertEquals(0.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)

        repo.setDoseStatus(dose.id, "pending")
        assertEquals(0.5, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)
    }

    @Test
    fun deletingPrnLoggedWithoutStockDoesNotCreateStock() = runBlocking {
        val med = MedicationEntity(
            name = "Тест",
            dosage = "1 таблетка",
            scheduleType = "as_needed",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 0.0,
            refillThreshold = 0.0
        )
        db.medicationDao().upsert(med)

        repo.logPrn(med.id, at = LocalDate.now().atTime(10, 0))
        val log = db.doseDao().forDay(LocalDate.now().toEpochDay()).single()
        assertEquals(0.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)

        repo.deletePrnDose(log.id)
        assertEquals(0.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)
    }

    @Test
    fun staleEditorSaveKeepsStockChangeMadeMeanwhile() = runBlocking {
        val med = MedicationEntity(
            name = "Тест",
            dosage = "1 таблетка",
            timesCsv = "08:00",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 10.0,
            refillThreshold = 2.0
        )
        db.medicationDao().upsert(med)
        val dose = insertDose(med, "08:00")
        repo.setDoseStatus(dose.id, "taken")
        assertEquals(9.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)

        // Редактор был открыт до приёма и сохраняет неизменённый остаток 10.
        repo.saveMedication(med.copy(notes = "заметка"), expectedInventory = 10.0)

        assertEquals(9.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)
    }

    @Test
    fun concurrentDoseTakesDoNotLoseStock() = runBlocking {
        val med = MedicationEntity(
            name = "Тест",
            dosage = "1 таблетка",
            timesCsv = "08:00",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 20.0,
            refillThreshold = 2.0
        )
        db.medicationDao().upsert(med)
        val doses = (0 until 10).map { index -> insertDose(med, "%02d:%02d".format(8, index)) }

        coroutineScope {
            doses.map { dose ->
                async(Dispatchers.Default) {
                    repo.setDoseStatus(dose.id, "taken")
                }
            }.awaitAll()
        }

        assertEquals(10.0, db.medicationDao().byId(med.id)?.inventoryCount!!, 0.0001)
    }

    @Test
    fun refillNotificationRespectsDisabledSetting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepository(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        fun refillId(medId: String) = ("refill:" + medId).hashCode()

        settings.setRefillReminders(true)
        val enabledMed = MedicationEntity(
            name = "Тест-уведомление",
            dosage = "1 таблетка",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 2.0,
            refillThreshold = 1.0
        )
        db.medicationDao().upsert(enabledMed)
        val enabledDose = insertDose(enabledMed, "08:00")
        try {
            repo.setDoseStatus(enabledDose.id, "taken")
            // Положительный контроль: путь уведомления действительно работает.
            assertTrue(manager.activeNotifications.any { it.id == refillId(enabledMed.id) })
        } finally {
            manager.cancel(refillId(enabledMed.id))
        }

        settings.setRefillReminders(false)
        val disabledMed = MedicationEntity(
            name = "Тест-без-уведомления",
            dosage = "1 таблетка",
            startDate = LocalDate.now().toEpochDay(),
            inventoryCount = 2.0,
            refillThreshold = 1.0
        )
        db.medicationDao().upsert(disabledMed)
        val disabledDose = insertDose(disabledMed, "08:00")
        try {
            repo.setDoseStatus(disabledDose.id, "taken")
            assertFalse(manager.activeNotifications.any { it.id == refillId(disabledMed.id) })
        } finally {
            settings.setRefillReminders(true)
            manager.cancel(refillId(disabledMed.id))
        }
    }



    @Test
    fun disablingMedicationRemindersCancelsShownNotification(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepository(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        settings.setMedReminders(true)
        // Предыдущие ручные проверки могли выключить канал; тест начинается
        // с чистого канала, иначе проверяется не код, а старая настройка.
        runCatching { manager.deleteNotificationChannel(Notify.CHANNEL_MEDS) }
        Notify.ensureChannels(context)
        val med = MedicationEntity(
            name = "Тест-снятие",
            dosage = "1 таблетка",
            timesCsv = "09:00",
            startDate = LocalDate.now().toEpochDay()
        )
        db.medicationDao().upsert(med)
        val dose = insertDose(med, "09:00")
        Notify.medication(context, dose, med)
        val id = dose.id.hashCode()
        assertTrue(manager.activeNotifications.any { it.id == id })

        val vm = SettingsViewModel(
            settings,
            WorkScheduler(context),
            MedicationAlarms(context, db),
            repo
        )
        vm.setMedReminders(false)
        val deadline = System.currentTimeMillis() + 5_000
        while (manager.activeNotifications.any { it.id == id } &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }
        assertFalse(manager.activeNotifications.any { it.id == id })
        settings.setMedReminders(true)
        Unit
    }


    @Test
    fun scheduleChangeKeepsPastSnapshotIntact() = runBlocking {
        val day1 = LocalDate.now().minusDays(2)
        val med = MedicationEntity(
            name = "Тест-снимок",
            timesCsv = "09:00",
            startDate = day1.toEpochDay(),
            createdAt = day1.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        db.medicationDao().upsert(med)
        repo.ensureTodayLogs(day1)
        val before = db.scheduleSnapshotDao().forDay(day1.toEpochDay()).single()
        assertEquals("09:00", before.timesCsv)

        repo.saveMedication(med.copy(timesCsv = "12:00"))
        repo.ensureTodayLogs()
        val after = db.scheduleSnapshotDao().forDay(day1.toEpochDay()).single()
        assertEquals("09:00", after.timesCsv)
        assertEquals("12:00", db.scheduleSnapshotDao().forDay(LocalDate.now().toEpochDay()).single().timesCsv)
    }

    @Test
    fun pauseAndResumeLeaveHistoryInterval() = runBlocking {
        val today = LocalDate.now()
        val med = MedicationEntity(
            name = "Тест-пауза",
            timesCsv = "09:00",
            startDate = today.toEpochDay()
        )
        db.medicationDao().upsert(med)
        repo.saveMedication(med.copy(active = false))
        val opened = db.medicationPauseDao().getAll().single()
        assertEquals(med.id, opened.medicationId)
        assertEquals(null, opened.endAt)

        repo.resumeMedication(med.id)
        val closed = db.medicationPauseDao().getAll().single()
        assertTrue(closed.endAt != null)
        assertTrue(closed.endAt!! >= closed.startAt)
    }

    @Test
    fun stoppingPausedMedicationClosesPauseInterval() = runBlocking {
        val today = LocalDate.now()
        val med = MedicationEntity(
            name = "Тест-стоп-паузы",
            timesCsv = "09:00",
            startDate = today.toEpochDay()
        )
        db.medicationDao().upsert(med)
        repo.saveMedication(med.copy(active = false))
        repo.discontinueMedication(med.id, "тест")

        val pause = db.medicationPauseDao().getAll().single()
        assertTrue(pause.endAt != null)
        assertTrue(db.medicationDao().byId(med.id)?.discontinuedAt != null)
    }

    @Test
    fun snoozedDoseIsNotAutoMissedBeforeSnoozeTime() = runBlocking {
        // Snooze is compared with the real clock inside markMissedOlderThan,
        // so the fixture must be relative to now, not a fixed hour of the day.
        val now = LocalDateTime.now()
        val scheduled = now.minusHours(5)
        val med = MedicationEntity(
            name = "Тест-снуз",
            dosage = "1 таблетка",
            timesCsv = scheduled.toLocalTime().hhmm(),
            startDate = scheduled.toLocalDate().toEpochDay()
        )
        db.medicationDao().upsert(med)
        val zone = ZoneId.systemDefault()
        val createdAt = scheduled.minusHours(1).atZone(zone).toInstant().toEpochMilli()
        val snoozeUntil = now.plusMinutes(30).atZone(zone).toInstant().toEpochMilli()
        db.doseDao().upsert(
            DoseLogEntity(
                id = "snoozed-dose",
                medicationId = med.id,
                medicationName = med.name,
                dosage = med.dosage,
                scheduledDate = scheduled.toLocalDate().toEpochDay(),
                scheduledTime = scheduled.toLocalTime().hhmm(),
                scheduledAt = scheduled.atZone(zone).toInstant().toEpochMilli(),
                status = "pending",
                snoozeUntil = snoozeUntil,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )

        repo.markMissedOlderThan(now = now, minutes = 240)

        val after = db.doseDao().byId("snoozed-dose")
        assertEquals("pending", after?.status)
        assertEquals(snoozeUntil, after?.snoozeUntil)
    }

    @Test
    fun explicitlyEnteredInventoryValueWinsOverConcurrentChange() = runBlocking {
        val today = LocalDate.now()
        val med = MedicationEntity(
            name = "Тест-запас",
            timesCsv = "09:00",
            startDate = today.toEpochDay(),
            inventoryCount = 10.0,
            refillThreshold = 3.0
        )
        db.medicationDao().upsert(med)
        // Пока редактор был открыт, запас изменился из уведомления.
        db.medicationDao().upsert(med.copy(inventoryCount = 8.0))

        // Пользователь явно вернул загруженное значение 10 — его нельзя отбросить.
        repo.saveMedication(
            med.copy(inventoryCount = 10.0),
            expectedInventory = 10.0,
            inventoryEdited = true
        )
        assertEquals(10.0, db.medicationDao().byId(med.id)?.inventoryCount)

        // Без правки поля сохраняется актуальный запас, а не устаревший снимок.
        db.medicationDao().upsert(med.copy(inventoryCount = 8.0))
        repo.saveMedication(
            med.copy(inventoryCount = 10.0),
            expectedInventory = 10.0,
            inventoryEdited = false
        )
        assertEquals(8.0, db.medicationDao().byId(med.id)?.inventoryCount)
    }

    @Test
    fun missedCutoffUsesLocalScheduleAfterZoneChange() = runBlocking {
        val today = LocalDate.now()
        val med = MedicationEntity(
            name = "Тест-пояс",
            dosage = "1 таблетка",
            timesCsv = "09:00",
            startDate = today.toEpochDay()
        )
        db.medicationDao().upsert(med)
        val createdAt = today.atTime(7, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val staleScheduledAt = today.atTime(23, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dose = DoseLogEntity(
            id = "zone-dose",
            medicationId = med.id,
            medicationName = med.name,
            dosage = med.dosage,
            scheduledDate = today.toEpochDay(),
            scheduledTime = "09:00",
            scheduledAt = staleScheduledAt,
            status = "pending",
            createdAt = createdAt,
            updatedAt = createdAt
        )
        db.doseDao().upsert(dose)

        repo.markMissedOlderThan(now = today.atTime(15, 0), minutes = 240)

        assertEquals("missed", db.doseDao().byId("zone-dose")?.status)
    }
}
